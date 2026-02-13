package com.hytalecraft.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for GLB (GL Transmission Format Binary) files.
 * Ported from Falcraft — pure Java NIO, no external deps.
 */
public class GLBParser {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new Gson();

    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int JSON_CHUNK_TYPE = 0x4E4F534A; // "JSON"
    private static final int BIN_CHUNK_TYPE = 0x004E4942; // "BIN\0"

    public record MeshData(float[] vertices, int[] indices, int[] colors, float[] uvs) {}

    /**
     * Extracts the embedded texture from a GLB file if present.
     */
    public static byte[] extractEmbeddedTexture(byte[] glbData) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(glbData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int magic = buffer.getInt();
        if (magic != GLB_MAGIC) {
            return null;
        }
        buffer.getInt(); // version
        buffer.getInt(); // length

        int jsonChunkLength = buffer.getInt();
        buffer.getInt(); // chunk type

        byte[] jsonBytes = new byte[jsonChunkLength];
        buffer.get(jsonBytes);
        JsonObject gltf = GSON.fromJson(new String(jsonBytes), JsonObject.class);

        int binChunkLength = buffer.getInt();
        buffer.getInt(); // chunk type

        byte[] binData = new byte[binChunkLength];
        buffer.get(binData);

        if (!gltf.has("images")) {
            return null;
        }

        JsonArray images = gltf.getAsJsonArray("images");
        if (images.isEmpty()) {
            return null;
        }

        JsonObject image = images.get(0).getAsJsonObject();
        if (!image.has("bufferView")) {
            return null;
        }

        int bufferViewIndex = image.get("bufferView").getAsInt();
        JsonArray bufferViews = gltf.getAsJsonArray("bufferViews");
        JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();

        int byteOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        int byteLength = bufferView.get("byteLength").getAsInt();

        byte[] textureData = new byte[byteLength];
        System.arraycopy(binData, byteOffset, textureData, 0, byteLength);

        return textureData;
    }

    /**
     * Parses a GLB file and extracts mesh data.
     */
    public static MeshData parse(byte[] glbData, TextureSampler textureSampler) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(glbData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        int magic = buffer.getInt();
        if (magic != GLB_MAGIC) {
            throw new IOException("Invalid GLB magic number: 0x" + Integer.toHexString(magic));
        }

        int version = buffer.getInt();
        int length = buffer.getInt();

        int jsonChunkLength = buffer.getInt();
        int jsonChunkType = buffer.getInt();

        if (jsonChunkType != JSON_CHUNK_TYPE) {
            throw new IOException("Expected JSON chunk, got: 0x" + Integer.toHexString(jsonChunkType));
        }

        byte[] jsonBytes = new byte[jsonChunkLength];
        buffer.get(jsonBytes);
        JsonObject gltf = GSON.fromJson(new String(jsonBytes), JsonObject.class);

        int binChunkLength = buffer.getInt();
        int binChunkType = buffer.getInt();

        if (binChunkType != BIN_CHUNK_TYPE) {
            throw new IOException("Expected BIN chunk, got: 0x" + Integer.toHexString(binChunkType));
        }

        byte[] binData = new byte[binChunkLength];
        buffer.get(binData);

        return extractMeshData(gltf, binData);
    }

    private static MeshData extractMeshData(JsonObject gltf, byte[] binData) throws IOException {
        JsonArray meshes = gltf.getAsJsonArray("meshes");
        if (meshes == null || meshes.isEmpty()) {
            throw new IOException("No meshes found in glTF");
        }

        JsonObject mesh = meshes.get(0).getAsJsonObject();
        JsonArray primitives = mesh.getAsJsonArray("primitives");

        List<Float> allVertices = new ArrayList<>();
        List<Integer> allIndices = new ArrayList<>();
        List<Integer> allColors = new ArrayList<>();
        List<Float> allUVs = new ArrayList<>();

        int vertexOffset = 0;

        for (int i = 0; i < primitives.size(); i++) {
            JsonObject primitive = primitives.get(i).getAsJsonObject();
            JsonObject attributes = primitive.getAsJsonObject("attributes");

            int positionAccessorIndex = attributes.get("POSITION").getAsInt();
            float[] positions = extractFloatArray(gltf, binData, positionAccessorIndex);

            if (primitive.has("indices")) {
                int indicesAccessorIndex = primitive.get("indices").getAsInt();
                int[] indices = extractIntArray(gltf, binData, indicesAccessorIndex);
                for (int index : indices) {
                    allIndices.add(index + vertexOffset);
                }
            } else {
                for (int j = 0; j < positions.length / 3; j++) {
                    allIndices.add(vertexOffset + j);
                }
            }

            float[] uvs = null;
            if (attributes.has("TEXCOORD_0")) {
                int uvAccessorIndex = attributes.get("TEXCOORD_0").getAsInt();
                uvs = extractFloatArray(gltf, binData, uvAccessorIndex);
            }

            int[] colors;
            if (attributes.has("COLOR_0")) {
                int colorAccessorIndex = attributes.get("COLOR_0").getAsInt();
                colors = extractColorArray(gltf, binData, colorAccessorIndex);
            } else {
                colors = new int[positions.length / 3];
                for (int j = 0; j < colors.length; j++) {
                    float x = positions[j * 3];
                    float y = positions[j * 3 + 1];
                    float z = positions[j * 3 + 2];
                    int gray = (int) ((Math.abs(x * 50) + Math.abs(y * 50) + Math.abs(z * 50)) % 128) + 64;
                    colors[j] = (gray << 16) | (gray << 8) | gray;
                }
            }

            for (float pos : positions) {
                allVertices.add(pos);
            }
            for (int color : colors) {
                allColors.add(color);
            }
            if (uvs != null) {
                for (float uv : uvs) {
                    allUVs.add(uv);
                }
            } else {
                for (int j = 0; j < positions.length / 3; j++) {
                    allUVs.add(0.0f);
                    allUVs.add(0.0f);
                }
            }

            vertexOffset += positions.length / 3;
        }

        float[] vertices = new float[allVertices.size()];
        for (int i = 0; i < allVertices.size(); i++) {
            vertices[i] = allVertices.get(i);
        }

        int[] indices = allIndices.stream().mapToInt(Integer::intValue).toArray();
        int[] colors = allColors.stream().mapToInt(Integer::intValue).toArray();

        float[] uvs = new float[allUVs.size()];
        for (int i = 0; i < allUVs.size(); i++) {
            uvs[i] = allUVs.get(i);
        }

        return new MeshData(vertices, indices, colors, uvs);
    }

    private static float[] extractFloatArray(JsonObject gltf, byte[] binData, int accessorIndex) throws IOException {
        JsonObject accessor = gltf.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        int count = accessor.get("count").getAsInt();
        String type = accessor.get("type").getAsString();

        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;

        JsonObject bufferView = gltf.getAsJsonArray("bufferViews").get(bufferViewIndex).getAsJsonObject();
        int bufferViewOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;

        int componentsPerElement = getComponentCount(type);
        int totalComponents = count * componentsPerElement;

        ByteBuffer buffer = ByteBuffer.wrap(binData, bufferViewOffset + byteOffset, totalComponents * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        float[] result = new float[totalComponents];
        for (int i = 0; i < totalComponents; i++) {
            result[i] = buffer.getFloat();
        }

        return result;
    }

    private static int[] extractIntArray(JsonObject gltf, byte[] binData, int accessorIndex) throws IOException {
        JsonObject accessor = gltf.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();

        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;

        JsonObject bufferView = gltf.getAsJsonArray("bufferViews").get(bufferViewIndex).getAsJsonObject();
        int bufferViewOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;

        ByteBuffer buffer = ByteBuffer.wrap(binData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(bufferViewOffset + byteOffset);

        int[] result = new int[count];

        for (int i = 0; i < count; i++) {
            if (componentType == 5121) { // UNSIGNED_BYTE
                result[i] = buffer.get() & 0xFF;
            } else if (componentType == 5123) { // UNSIGNED_SHORT
                result[i] = buffer.getShort() & 0xFFFF;
            } else if (componentType == 5125) { // UNSIGNED_INT
                result[i] = buffer.getInt();
            } else {
                throw new IOException("Unsupported index component type: " + componentType);
            }
        }

        return result;
    }

    private static int[] extractColorArray(JsonObject gltf, byte[] binData, int accessorIndex) throws IOException {
        JsonObject accessor = gltf.getAsJsonArray("accessors").get(accessorIndex).getAsJsonObject();
        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        String type = accessor.get("type").getAsString();

        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;

        JsonObject bufferView = gltf.getAsJsonArray("bufferViews").get(bufferViewIndex).getAsJsonObject();
        int bufferViewOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;

        int componentsPerVertex = getComponentCount(type);
        int totalOffset = bufferViewOffset + byteOffset;

        ByteBuffer buffer = ByteBuffer.wrap(binData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(totalOffset);

        int[] colors = new int[count];

        for (int i = 0; i < count; i++) {
            float r, g, b;

            if (componentType == 5126) { // FLOAT
                r = buffer.getFloat();
                g = buffer.getFloat();
                b = buffer.getFloat();
                if (componentsPerVertex == 4) buffer.getFloat(); // skip alpha
            } else if (componentType == 5121) { // UNSIGNED_BYTE
                r = (buffer.get() & 0xFF) / 255.0f;
                g = (buffer.get() & 0xFF) / 255.0f;
                b = (buffer.get() & 0xFF) / 255.0f;
                if (componentsPerVertex == 4) buffer.get(); // skip alpha
            } else if (componentType == 5123) { // UNSIGNED_SHORT
                r = (buffer.getShort() & 0xFFFF) / 65535.0f;
                g = (buffer.getShort() & 0xFFFF) / 65535.0f;
                b = (buffer.getShort() & 0xFFFF) / 65535.0f;
                if (componentsPerVertex == 4) buffer.getShort(); // skip alpha
            } else {
                throw new IOException("Unsupported color component type: " + componentType);
            }

            int ri = Math.min(255, Math.max(0, (int) (r * 255)));
            int gi = Math.min(255, Math.max(0, (int) (g * 255)));
            int bi = Math.min(255, Math.max(0, (int) (b * 255)));
            colors[i] = (ri << 16) | (gi << 8) | bi;
        }

        return colors;
    }

    private static int getComponentCount(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}
