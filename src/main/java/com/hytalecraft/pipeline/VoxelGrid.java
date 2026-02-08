package com.hytalecraft.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoxelGrid {

    public record VoxelPos(int x, int y, int z) {}

    private final Map<VoxelPos, Integer> voxels;
    private final int size;

    public VoxelGrid(Map<VoxelPos, Integer> voxels, int size) {
        this.voxels = voxels;
        this.size = size;
    }

    public VoxelGrid(int size) {
        this(new HashMap<>(), size);
    }

    public void set(int x, int y, int z, int rgb) {
        voxels.put(new VoxelPos(x, y, z), rgb);
    }

    public Integer get(int x, int y, int z) {
        return voxels.get(new VoxelPos(x, y, z));
    }

    public Map<VoxelPos, Integer> getVoxels() {
        return voxels;
    }

    public int getSize() {
        return size;
    }

    public int getVoxelCount() {
        return voxels.size();
    }

    public List<VoxelPos> getVoxelsAtY(int y) {
        List<VoxelPos> result = new ArrayList<>();
        for (VoxelPos pos : voxels.keySet()) {
            if (pos.y() == y) {
                result.add(pos);
            }
        }
        return result;
    }

    public int getMinY() {
        return voxels.keySet().stream().mapToInt(VoxelPos::y).min().orElse(0);
    }

    public int getMaxY() {
        return voxels.keySet().stream().mapToInt(VoxelPos::y).max().orElse(0);
    }
}
