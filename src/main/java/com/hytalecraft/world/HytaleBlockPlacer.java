package com.hytalecraft.world;

import com.hytalecraft.pipeline.VoxelGrid;
import com.hytalecraft.pipeline.VoxelGrid.VoxelPos;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Places voxels as Hytale blocks in the world, batched for performance.
 */
public class HytaleBlockPlacer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BLOCKS_PER_BATCH = 500;
    private static final int DEFAULT_CHUNK_LOAD_TIMEOUT_SECONDS = 60;

    private final boolean forceLoadChunks;
    private final int chunkLoadTimeoutSeconds;

    public HytaleBlockPlacer() {
        this(true, DEFAULT_CHUNK_LOAD_TIMEOUT_SECONDS);
    }

    public HytaleBlockPlacer(boolean forceLoadChunks, int chunkLoadTimeoutSeconds) {
        this.forceLoadChunks = forceLoadChunks;
        this.chunkLoadTimeoutSeconds = Math.max(1, chunkLoadTimeoutSeconds);
    }

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
        Set<Long> requiredChunks = new HashSet<>();
        for (Map.Entry<VoxelPos, Integer> entry : grid.getVoxels().entrySet()) {
            VoxelPos pos = entry.getKey();
            int rgb = entry.getValue();
            int x = originX + pos.x();
            int y = originY + pos.y();
            int z = originZ + pos.z();
            String blockName = HytaleBlockPalette.getClosestBlock(rgb);
            entries.add(new PlacementEntry(
                    x,
                    y,
                    z,
                    blockName
            ));
            requiredChunks.add(ChunkUtil.indexChunkFromBlock(x, z));
        }

        int totalBlocks = entries.size();
        progress.accept("Prepared " + totalBlocks + " blocks across " + requiredChunks.size() + " chunks.");

        if (entries.isEmpty()) {
            progress.accept("Done! Placement complete: requested=0, placed=0, skippedUnloaded=0, failed=0.");
            future.complete(0);
            return future;
        }

        CompletableFuture<Void> readinessFuture = CompletableFuture.completedFuture(null);
        if (forceLoadChunks) {
            readinessFuture = preloadAndVerifyChunks(world, requiredChunks, progress);
        }

        readinessFuture
                .thenRun(() -> {
                    progress.accept("Placing " + totalBlocks + " blocks...");
                    placeBatch(world, entries, 0, totalBlocks, progress, future, new PlacementStats());
                })
                .exceptionally(throwable -> {
                    Throwable cause = unwrap(throwable);
                    future.completeExceptionally(cause);
                    return null;
                });

        return future;
    }

    private CompletableFuture<Void> preloadAndVerifyChunks(World world,
                                                           Set<Long> requiredChunks,
                                                           Consumer<String> progress) {
        progress.accept("Preloading " + requiredChunks.size() + " chunks (timeout: "
                + chunkLoadTimeoutSeconds + "s)...");

        CompletableFuture<?>[] chunkLoads = requiredChunks.stream()
                .map(world::getChunkAsync)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(chunkLoads)
                .orTimeout(chunkLoadTimeoutSeconds, TimeUnit.SECONDS)
                .thenCompose(unused -> countMissingLoadedChunks(world, requiredChunks))
                .thenAccept(missingChunks -> {
                    if (missingChunks > 0) {
                        throw new IllegalStateException("Chunk preload incomplete: " + missingChunks + " of "
                                + requiredChunks.size() + " required chunks are still not loaded.");
                    }
                    progress.accept("Chunk preload complete (" + requiredChunks.size() + " chunks).");
                })
                .handle((unused, throwable) -> {
                    if (throwable != null) {
                        throw new CompletionException(toChunkPreloadException(throwable, requiredChunks.size()));
                    }
                    return null;
                });
    }

    private CompletableFuture<Integer> countMissingLoadedChunks(World world, Set<Long> requiredChunks) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        world.execute(() -> {
            int missing = 0;
            for (Long chunkIndex : requiredChunks) {
                BlockAccessor chunk = world.getChunkIfLoaded(chunkIndex);
                if (chunk == null) {
                    missing++;
                }
            }
            future.complete(missing);
        });
        return future;
    }

    private void placeBatch(World world, List<PlacementEntry> entries,
                            int startIndex, int totalBlocks,
                            Consumer<String> progress, CompletableFuture<Integer> future,
                            PlacementStats stats) {
        world.execute(() -> {
            int endIndex = Math.min(startIndex + BLOCKS_PER_BATCH, entries.size());

            for (int i = startIndex; i < endIndex; i++) {
                PlacementEntry entry = entries.get(i);
                try {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(entry.x, entry.z);
                    BlockAccessor chunk = world.getChunkIfLoaded(chunkIndex);

                    if (chunk == null) {
                        stats.skippedUnloaded++;
                        continue;
                    }

                    boolean success = chunk.setBlock(entry.x, entry.y, entry.z, entry.blockName);
                    if (success) {
                        stats.placed++;
                    } else {
                        stats.failed++;
                    }
                } catch (Exception e) {
                    stats.failed++;
                    LOGGER.atWarning().log("[HytaleBlockPlacer] Failed to place block at %d,%d,%d: %s",
                            entry.x, entry.y, entry.z, e.getMessage());
                }
            }

            if (endIndex < entries.size()) {
                int percent = (endIndex * 100) / totalBlocks;
                progress.accept("Placing blocks... " + percent + "% (" + endIndex + "/" + totalBlocks
                        + ", placed=" + stats.placed
                        + ", skippedUnloaded=" + stats.skippedUnloaded
                        + ", failed=" + stats.failed + ")");
                placeBatch(world, entries, endIndex, totalBlocks, progress, future, stats);
            } else {
                progress.accept("Done! Placement complete: requested=" + totalBlocks
                        + ", placed=" + stats.placed
                        + ", skippedUnloaded=" + stats.skippedUnloaded
                        + ", failed=" + stats.failed + ".");
                LOGGER.atInfo().log("[HytaleBlockPlacer] Placement finished: requested=%d placed=%d skipped=%d failed=%d",
                        totalBlocks, stats.placed, stats.skippedUnloaded, stats.failed);
                future.complete(stats.placed);
            }
        });
    }

    private IllegalStateException toChunkPreloadException(Throwable throwable, int requiredChunkCount) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof TimeoutException) {
            return new IllegalStateException("Chunk preload timed out after " + chunkLoadTimeoutSeconds
                    + " seconds while waiting for " + requiredChunkCount + " chunks.", cause);
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        return new IllegalStateException("Chunk preload failed: " + message, cause);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static class PlacementStats {
        int placed;
        int skippedUnloaded;
        int failed;
    }

    private record PlacementEntry(int x, int y, int z, String blockName) {}
}
