package com.hytalecraft.world;

import com.hytalecraft.pipeline.VoxelGrid;
import com.hytalecraft.pipeline.VoxelGrid.VoxelPos;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Places voxels as Hytale blocks in the world, batched for performance.
 */
public class HytaleBlockPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BLOCKS_PER_BATCH = 500;

    /**
     * Places all voxels from a VoxelGrid into the world at the given origin.
     * Runs on the world thread in batches of 500 blocks.
     *
     * @param world       The Hytale world
     * @param grid        The voxel grid to place
     * @param originX     World X origin
     * @param originY     World Y origin
     * @param originZ     World Z origin
     * @param progress    Progress callback
     * @return CompletableFuture that completes with total blocks placed
     */
    public CompletableFuture<Integer> place(World world, VoxelGrid grid,
                                             int originX, int originY, int originZ,
                                             Consumer<String> progress) {
        CompletableFuture<Integer> future = new CompletableFuture<>();

        // Collect all voxels into a placement list
        List<PlacementEntry> entries = new ArrayList<>();
        for (Map.Entry<VoxelPos, Integer> entry : grid.getVoxels().entrySet()) {
            VoxelPos pos = entry.getKey();
            int rgb = entry.getValue();
            String blockName = HytaleBlockPalette.getClosestBlock(rgb);
            entries.add(new PlacementEntry(
                    originX + pos.x(),
                    originY + pos.y(),
                    originZ + pos.z(),
                    blockName
            ));
        }

        int totalBlocks = entries.size();
        progress.accept("Placing " + totalBlocks + " blocks...");

        // Place in batches on the world thread
        placeBatch(world, entries, 0, totalBlocks, progress, future);

        return future;
    }

    private void placeBatch(World world, List<PlacementEntry> entries,
                            int startIndex, int totalBlocks,
                            Consumer<String> progress, CompletableFuture<Integer> future) {
        world.execute(() -> {
            int endIndex = Math.min(startIndex + BLOCKS_PER_BATCH, entries.size());
            int placed = 0;

            for (int i = startIndex; i < endIndex; i++) {
                PlacementEntry entry = entries.get(i);
                try {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(entry.x, entry.z);
                    BlockAccessor chunk = world.getChunkIfLoaded(chunkIndex);

                    if (chunk == null) {
                        continue;
                    }

                    chunk.setBlock(entry.x, entry.y, entry.z, entry.blockName);
                    placed++;
                } catch (Exception e) {
                    LOGGER.atWarning().log("[HytaleBlockPlacer] Failed to place block at %d,%d,%d: %s",
                            entry.x, entry.y, entry.z, e.getMessage());
                }
            }

            if (endIndex < entries.size()) {
                int percent = (endIndex * 100) / totalBlocks;
                progress.accept("Placing blocks... " + percent + "% (" + endIndex + "/" + totalBlocks + ")");
                placeBatch(world, entries, endIndex, totalBlocks, progress, future);
            } else {
                progress.accept("Done! Placed " + totalBlocks + " blocks.");
                LOGGER.atInfo().log("[HytaleBlockPlacer] Placed %d blocks", totalBlocks);
                future.complete(totalBlocks);
            }
        });
    }

    private record PlacementEntry(int x, int y, int z, String blockName) {}
}
