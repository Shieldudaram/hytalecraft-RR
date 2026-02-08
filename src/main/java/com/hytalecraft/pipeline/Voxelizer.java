package com.hytalecraft.pipeline;

import com.hytalecraft.pipeline.VoxelGrid.VoxelPos;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts 3D mesh data to a voxel grid using triangle splitting.
 * Ported from Falcraft — uses VoxelPos instead of Minecraft BlockPos.
 */
public class Voxelizer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final double EPSILON = 1.0 / (1 << 16);
    private static final double DISTANCE_LIMIT = 2.0;
    private static final float SATURATION_BOOST = 1.0f;

    // ==================== VECTOR HELPERS ====================

    private static class Vec2 {
        double u, v;

        Vec2(double u, double v) {
            this.u = u;
            this.v = v;
        }

        static Vec2 mix(Vec2 a, Vec2 b, double t) {
            return new Vec2(a.u + (b.u - a.u) * t, a.v + (b.v - a.v) * t);
        }
    }

    private static class Vec3 {
        double x, y, z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        double get(int axis) {
            return switch (axis) {
                case 0 -> x;
                case 1 -> y;
                case 2 -> z;
                default -> throw new IllegalArgumentException("Invalid axis: " + axis);
            };
        }

        static Vec3 sub(Vec3 a, Vec3 b) {
            return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
        }

        static Vec3 mix(Vec3 a, Vec3 b, double t) {
            return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
        }

        static Vec3 cross(Vec3 a, Vec3 b) {
            return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
        }

        static double dot(Vec3 a, Vec3 b) {
            return a.x * b.x + a.y * b.y + a.z * b.z;
        }

        double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        Vec3 normalize() {
            double len = length();
            if (len > 0) {
                return new Vec3(x / len, y / len, z / len);
            }
            return new Vec3(0, 0, 0);
        }
    }

    // ==================== TEXTURED TRIANGLE ====================

    private static class TexturedTriangle {
        Vec3[] v = new Vec3[3];
        Vec2[] t = new Vec2[3];
        int[] c = new int[3];

        TexturedTriangle(Vec3 v0, Vec3 v1, Vec3 v2, Vec2 t0, Vec2 t1, Vec2 t2, int c0, int c1, int c2) {
            v[0] = v0; v[1] = v1; v[2] = v2;
            t[0] = t0; t[1] = t1; t[2] = t2;
            c[0] = c0; c[1] = c1; c[2] = c2;
        }

        TexturedTriangle copy() {
            return new TexturedTriangle(
                    new Vec3(v[0].x, v[0].y, v[0].z),
                    new Vec3(v[1].x, v[1].y, v[1].z),
                    new Vec3(v[2].x, v[2].y, v[2].z),
                    new Vec2(t[0].u, t[0].v),
                    new Vec2(t[1].u, t[1].v),
                    new Vec2(t[2].u, t[2].v),
                    c[0], c[1], c[2]
            );
        }

        Vec3 vertex(int i) { return v[i]; }
        Vec2 texture(int i) { return t[i]; }
        int color(int i) { return c[i]; }

        Vec3 normal() {
            return Vec3.cross(Vec3.sub(v[1], v[0]), Vec3.sub(v[2], v[0]));
        }

        double area() {
            return normal().length() / 2.0;
        }

        Vec2 textureCenter() {
            return new Vec2(
                    (t[0].u + t[1].u + t[2].u) / 3.0,
                    (t[0].v + t[1].v + t[2].v) / 3.0
            );
        }

        int colorCenter() {
            int r0 = (c[0] >> 16) & 0xFF, g0 = (c[0] >> 8) & 0xFF, b0 = c[0] & 0xFF;
            int r1 = (c[1] >> 16) & 0xFF, g1 = (c[1] >> 8) & 0xFF, b1 = c[1] & 0xFF;
            int r2 = (c[2] >> 16) & 0xFF, g2 = (c[2] >> 8) & 0xFF, b2 = c[2] & 0xFF;
            int r = (r0 + r1 + r2) / 3;
            int g = (g0 + g1 + g2) / 3;
            int b = (b0 + b1 + b2) / 3;
            return (r << 16) | (g << 8) | b;
        }

        static int mixColor(int c1, int c2, double t) {
            int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
            int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
            int r = (int) (r1 + (r2 - r1) * t);
            int g = (int) (g1 + (g2 - g1) * t);
            int b = (int) (b1 + (b2 - b1) * t);
            return (r << 16) | (g << 8) | b;
        }

        double min(int axis) {
            return Math.min(v[0].get(axis), Math.min(v[1].get(axis), v[2].get(axis)));
        }

        double max(int axis) {
            return Math.max(v[0].get(axis), Math.max(v[1].get(axis), v[2].get(axis)));
        }

        int[] voxelMin() {
            return new int[]{
                    (int) Math.floor(min(0)),
                    (int) Math.floor(min(1)),
                    (int) Math.floor(min(2))
            };
        }

        int[] voxelMax() {
            return new int[]{
                    (int) Math.floor(max(0)) + 1,
                    (int) Math.floor(max(1)) + 1,
                    (int) Math.floor(max(2)) + 1
            };
        }
    }

    private static class WeightedUv {
        double weight;
        Vec2 uv;

        WeightedUv(double weight, Vec2 uv) {
            this.weight = weight;
            this.uv = uv;
        }

        static WeightedUv mix(WeightedUv a, WeightedUv b) {
            double weightSum = a.weight + b.weight;
            if (weightSum == 0) return new WeightedUv(0, new Vec2(0, 0));
            return new WeightedUv(
                    weightSum,
                    new Vec2(
                            (a.weight * a.uv.u + b.weight * b.uv.u) / weightSum,
                            (a.weight * a.uv.v + b.weight * b.uv.v) / weightSum
                    )
            );
        }
    }

    private static class WeightedColor {
        double weight;
        int color;

        WeightedColor(double weight, int color) {
            this.weight = weight;
            this.color = color;
        }
    }

    // ==================== TRIANGLE SPLITTING ====================

    private static boolean isZero(double x) {
        return Math.abs(x) < EPSILON;
    }

    private static boolean eq(double x, int plane) {
        return isZero(x - plane);
    }

    private static double intersectRayAxisPlane(Vec3 org, Vec3 dir, int axis, int plane) {
        double d = -dir.get(axis);
        return isZero(d) ? 0 : (org.get(axis) - plane) / d;
    }

    private static double distancePointPlane(Vec3 p, Vec3 planeOrg, Vec3 planeNormal) {
        return Vec3.dot(planeNormal, Vec3.sub(p, planeOrg));
    }

    private enum DiscardMode {
        NONE, DISCARD_LO, DISCARD_HI
    }

    private static void splitTriangle(int axis, int plane, TexturedTriangle tri,
                                       List<TexturedTriangle> outLo, List<TexturedTriangle> outHi,
                                       DiscardMode mode) {
        boolean[] loVertices = new boolean[3];
        boolean[] planarVertices = new boolean[3];
        int loSum = 0, planarSum = 0;

        for (int i = 0; i < 3; i++) {
            double val = tri.vertex(i).get(axis);
            planarVertices[i] = eq(val, plane);
            loVertices[i] = val < plane;
            if (loVertices[i]) loSum++;
            if (planarVertices[i]) planarSum++;
        }

        java.util.function.BiConsumer<TexturedTriangle, Boolean> push = (t, isLo) -> {
            if (mode == DiscardMode.NONE) {
                (isLo ? outLo : outHi).add(t);
            } else if (mode == DiscardMode.DISCARD_LO) {
                if (!isLo) outHi.add(t);
            } else {
                if (isLo) outLo.add(t);
            }
        };

        if (loSum == 0) { push.accept(tri, false); return; }
        if (loSum == 3) { push.accept(tri, true); return; }
        if (planarSum == 3) { push.accept(tri, false); return; }

        if (planarSum == 2) {
            int nonPlanar = !planarVertices[0] ? 0 : !planarVertices[1] ? 1 : 2;
            push.accept(tri, loVertices[nonPlanar]);
            return;
        }

        if (planarSum == 1) {
            int planarIdx = planarVertices[0] ? 0 : planarVertices[1] ? 1 : 2;
            int[] nonPlanarIdx = {(planarIdx + 1) % 3, (planarIdx + 2) % 3};

            if (loVertices[nonPlanarIdx[0]] == loVertices[nonPlanarIdx[1]]) {
                push.accept(tri, loVertices[nonPlanarIdx[0]]);
                return;
            }

            Vec3 planarVert = tri.vertex(planarIdx);
            Vec2 planarTex = tri.texture(planarIdx);
            int planarCol = tri.color(planarIdx);
            Vec3[] nonPlanarVerts = {tri.vertex(nonPlanarIdx[0]), tri.vertex(nonPlanarIdx[1])};
            Vec2[] nonPlanarTexs = {tri.texture(nonPlanarIdx[0]), tri.texture(nonPlanarIdx[1])};
            int[] nonPlanarCols = {tri.color(nonPlanarIdx[0]), tri.color(nonPlanarIdx[1])};
            Vec3 edge = Vec3.sub(nonPlanarVerts[1], nonPlanarVerts[0]);

            double t = intersectRayAxisPlane(nonPlanarVerts[0], edge, axis, plane);
            Vec3 geoIsect = Vec3.mix(nonPlanarVerts[0], nonPlanarVerts[1], t);
            Vec2 texIsect = Vec2.mix(nonPlanarTexs[0], nonPlanarTexs[1], t);
            int colIsect = TexturedTriangle.mixColor(nonPlanarCols[0], nonPlanarCols[1], t);

            TexturedTriangle tri1 = new TexturedTriangle(planarVert, nonPlanarVerts[0], geoIsect,
                    planarTex, nonPlanarTexs[0], texIsect, planarCol, nonPlanarCols[0], colIsect);
            TexturedTriangle tri2 = new TexturedTriangle(planarVert, geoIsect, nonPlanarVerts[1],
                    planarTex, texIsect, nonPlanarTexs[1], planarCol, colIsect, nonPlanarCols[1]);

            push.accept(tri1, loVertices[nonPlanarIdx[0]]);
            push.accept(tri2, !loVertices[nonPlanarIdx[0]]);
            return;
        }

        // Regular case: one vertex isolated
        boolean isolatedIsLo = loSum == 1;
        int isolatedIdx = isolatedIsLo ?
                (loVertices[0] ? 0 : loVertices[1] ? 1 : 2) :
                (!loVertices[0] ? 0 : !loVertices[1] ? 1 : 2);
        int[] otherIdx = {(isolatedIdx + 1) % 3, (isolatedIdx + 2) % 3};

        Vec3 isolatedVert = tri.vertex(isolatedIdx);
        Vec2 isolatedTex = tri.texture(isolatedIdx);
        int isolatedCol = tri.color(isolatedIdx);
        Vec3[] otherVerts = {tri.vertex(otherIdx[0]), tri.vertex(otherIdx[1])};
        Vec2[] otherTexs = {tri.texture(otherIdx[0]), tri.texture(otherIdx[1])};
        int[] otherCols = {tri.color(otherIdx[0]), tri.color(otherIdx[1])};

        Vec3[] edges = {Vec3.sub(otherVerts[0], isolatedVert), Vec3.sub(otherVerts[1], isolatedVert)};
        double[] ts = {
                intersectRayAxisPlane(isolatedVert, edges[0], axis, plane),
                intersectRayAxisPlane(isolatedVert, edges[1], axis, plane)
        };
        Vec3[] geoIsects = {
                Vec3.mix(isolatedVert, otherVerts[0], ts[0]),
                Vec3.mix(isolatedVert, otherVerts[1], ts[1])
        };
        Vec2[] texIsects = {
                Vec2.mix(isolatedTex, otherTexs[0], ts[0]),
                Vec2.mix(isolatedTex, otherTexs[1], ts[1])
        };
        int[] colIsects = {
                TexturedTriangle.mixColor(isolatedCol, otherCols[0], ts[0]),
                TexturedTriangle.mixColor(isolatedCol, otherCols[1], ts[1])
        };

        TexturedTriangle isolatedTri = new TexturedTriangle(
                isolatedVert, geoIsects[0], geoIsects[1],
                isolatedTex, texIsects[0], texIsects[1],
                isolatedCol, colIsects[0], colIsects[1]
        );

        TexturedTriangle otherTri1 = new TexturedTriangle(
                geoIsects[0], otherVerts[0], otherVerts[1],
                texIsects[0], otherTexs[0], otherTexs[1],
                colIsects[0], otherCols[0], otherCols[1]
        );
        TexturedTriangle otherTri2 = new TexturedTriangle(
                geoIsects[0], geoIsects[1], otherVerts[1],
                texIsects[0], texIsects[1], otherTexs[1],
                colIsects[0], colIsects[1], otherCols[1]
        );

        push.accept(isolatedTri, isolatedIsLo);
        push.accept(otherTri1, !isolatedIsLo);
        push.accept(otherTri2, !isolatedIsLo);
    }

    private static WeightedUv computeTriangleUvInVoxel(TexturedTriangle inputTriangle, int vx, int vy, int vz) {
        List<TexturedTriangle> preSplit = new ArrayList<>();
        List<TexturedTriangle> postSplit = new ArrayList<>();

        preSplit.add(inputTriangle.copy());

        int[] pos = {vx, vy, vz};

        for (int hi = 0; hi < 2; hi++) {
            DiscardMode mode = hi == 0 ? DiscardMode.DISCARD_LO : DiscardMode.DISCARD_HI;

            for (int axis = 0; axis < 3; axis++) {
                int plane = pos[axis] + hi;

                for (TexturedTriangle t : preSplit) {
                    splitTriangle(axis, plane, t, postSplit, postSplit, mode);
                }

                preSplit.clear();
                if (postSplit.isEmpty()) {
                    return new WeightedUv(0, new Vec2(0, 0));
                }

                List<TexturedTriangle> tmp = preSplit;
                preSplit = postSplit;
                postSplit = tmp;
            }
        }

        WeightedUv result = new WeightedUv(0, new Vec2(0, 0));
        for (TexturedTriangle t : preSplit) {
            double weight = inputTriangle.area();
            Vec2 uv = t.textureCenter();
            result = WeightedUv.mix(result, new WeightedUv(weight, uv));
        }

        return result;
    }

    // ==================== MAIN VOXELIZATION ====================

    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution) {
        return voxelize(mesh, resolution, null);
    }

    public static VoxelGrid voxelize(GLBParser.MeshData mesh, int resolution, TextureSampler textureSampler) {
        float[] vertices = mesh.vertices();
        int[] indices = mesh.indices();
        float[] uvs = mesh.uvs();

        boolean hasUVs = uvs != null && uvs.length > 0;
        boolean hasTexture = textureSampler != null && hasUVs;
        int[] colors = mesh.colors();
        boolean hasColors = colors != null && colors.length > 0;

        if (vertices.length == 0) {
            return new VoxelGrid(resolution);
        }

        // Calculate bounding box
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (int i = 0; i < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;
        double maxSize = Math.max(Math.max(sizeX, sizeY), sizeZ);

        if (maxSize == 0) {
            return new VoxelGrid(resolution);
        }

        double antiBleed = 0.5;
        double sampleScale = resolution - antiBleed;
        double scale = sampleScale / maxSize;
        double offsetX = -minX * scale + antiBleed / 2;
        double offsetY = -minY * scale + antiBleed / 2;
        double offsetZ = -minZ * scale + antiBleed / 2;

        // Build triangles
        List<TexturedTriangle> triangles = new ArrayList<>();
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i];
            int idx1 = indices[i + 1];
            int idx2 = indices[i + 2];

            Vec3 v0 = new Vec3(
                    vertices[idx0 * 3] * scale + offsetX,
                    vertices[idx0 * 3 + 1] * scale + offsetY,
                    vertices[idx0 * 3 + 2] * scale + offsetZ
            );
            Vec3 v1 = new Vec3(
                    vertices[idx1 * 3] * scale + offsetX,
                    vertices[idx1 * 3 + 1] * scale + offsetY,
                    vertices[idx1 * 3 + 2] * scale + offsetZ
            );
            Vec3 v2 = new Vec3(
                    vertices[idx2 * 3] * scale + offsetX,
                    vertices[idx2 * 3 + 1] * scale + offsetY,
                    vertices[idx2 * 3 + 2] * scale + offsetZ
            );

            Vec2 t0 = hasUVs ? new Vec2(uvs[idx0 * 2], uvs[idx0 * 2 + 1]) : new Vec2(0, 0);
            Vec2 t1 = hasUVs ? new Vec2(uvs[idx1 * 2], uvs[idx1 * 2 + 1]) : new Vec2(0, 0);
            Vec2 t2 = hasUVs ? new Vec2(uvs[idx2 * 2], uvs[idx2 * 2 + 1]) : new Vec2(0, 0);

            int c0 = hasColors ? colors[idx0] : 0x808080;
            int c1 = hasColors ? colors[idx1] : 0x808080;
            int c2 = hasColors ? colors[idx2] : 0x808080;

            triangles.add(new TexturedTriangle(v0, v1, v2, t0, t1, t2, c0, c1, c2));
        }

        // Voxelize each triangle
        Map<VoxelPos, WeightedColor> candidates = new HashMap<>();

        for (TexturedTriangle tri : triangles) {
            int[] vMin = tri.voxelMin();
            int[] vMax = tri.voxelMax();

            vMin[0] = Math.max(0, vMin[0]);
            vMin[1] = Math.max(0, vMin[1]);
            vMin[2] = Math.max(0, vMin[2]);
            vMax[0] = Math.min(resolution, vMax[0]);
            vMax[1] = Math.min(resolution, vMax[1]);
            vMax[2] = Math.min(resolution, vMax[2]);

            Vec3 planeOrg = tri.vertex(0);
            Vec3 planeNormal = tri.normal().normalize();
            boolean validNormal = !Double.isNaN(planeNormal.x);

            for (int vz = vMin[2]; vz < vMax[2]; vz++) {
                for (int vy = vMin[1]; vy < vMax[1]; vy++) {
                    for (int vx = vMin[0]; vx < vMax[0]; vx++) {
                        if (validNormal) {
                            Vec3 center = new Vec3(vx + 0.5, vy + 0.5, vz + 0.5);
                            double dist = Math.abs(distancePointPlane(center, planeOrg, planeNormal));
                            if (dist > DISTANCE_LIMIT) {
                                continue;
                            }
                        }

                        WeightedUv weightedUv = computeTriangleUvInVoxel(tri, vx, vy, vz);

                        if (weightedUv.weight <= 0) {
                            continue;
                        }

                        int color;
                        if (hasTexture) {
                            color = textureSampler.sample((float) weightedUv.uv.u, (float) weightedUv.uv.v);
                        } else {
                            color = boostSaturation(tri.colorCenter(), SATURATION_BOOST);
                        }

                        VoxelPos pos = new VoxelPos(vx, vy, vz);
                        WeightedColor newColor = new WeightedColor(weightedUv.weight, color);
                        WeightedColor existing = candidates.get(pos);

                        if (existing == null || newColor.weight > existing.weight) {
                            candidates.put(pos, newColor);
                        }
                    }
                }
            }
        }

        // Build VoxelGrid
        VoxelGrid grid = new VoxelGrid(resolution);
        for (Map.Entry<VoxelPos, WeightedColor> entry : candidates.entrySet()) {
            VoxelPos pos = entry.getKey();
            grid.set(pos.x(), pos.y(), pos.z(), entry.getValue().color);
        }

        LOGGER.atInfo().log("[Voxelizer] Voxelized %d triangles -> %d voxels (resolution=%d)",
                triangles.size(), grid.getVoxelCount(), resolution);

        return grid;
    }

    // ==================== SATURATION BOOST ====================

    private static int boostSaturation(int rgb, float factor) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;

        if (max == min) {
            return rgb; // achromatic
        }

        float d = max - min;
        float s = l > 0.5f ? d / (2f - max - min) : d / (max + min);

        float h;
        if (max == r) {
            h = ((g - b) / d + (g < b ? 6f : 0f)) / 6f;
        } else if (max == g) {
            h = ((b - r) / d + 2f) / 6f;
        } else {
            h = ((r - g) / d + 4f) / 6f;
        }

        s = Math.min(1.0f, s * factor);

        float r2, g2, b2;
        if (s == 0) {
            r2 = g2 = b2 = l;
        } else {
            float q = l < 0.5f ? l * (1f + s) : l + s - l * s;
            float p = 2f * l - q;
            r2 = hueToRgb(p, q, h + 1f / 3f);
            g2 = hueToRgb(p, q, h);
            b2 = hueToRgb(p, q, h - 1f / 3f);
        }

        int ri = Math.min(255, Math.max(0, Math.round(r2 * 255)));
        int gi = Math.min(255, Math.max(0, Math.round(g2 * 255)));
        int bi = Math.min(255, Math.max(0, Math.round(b2 * 255)));

        return (ri << 16) | (gi << 8) | bi;
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }
}
