package com.hytalecraft.pipeline;

import com.hytalecraft.config.HytaleCraftConfig;
import com.hytalecraft.world.HytaleBlockPlacer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Orchestrates the full text→image→3D→voxel→blocks pipeline.
 */
public class GenerationPipeline {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HytaleCraftConfig config;

    public GenerationPipeline(HytaleCraftConfig config) {
        this.config = config;
    }

    /**
     * Executes the full generation pipeline.
     *
     * @param prompt   Text prompt
     * @param size     Voxel grid resolution
     * @param world    Hytale world (implements Executor)
     * @param originX  Placement origin X
     * @param originY  Placement origin Y
     * @param originZ  Placement origin Z
     * @param progress Progress callback for messages to player
     */
    public void execute(String prompt, int size, World world,
                        int originX, int originY, int originZ,
                        Consumer<String> progress) {

        if (!config.hasApiKey()) {
            progress.accept("Error: API key not configured.");
            return;
        }

        FalAPI falApi = new FalAPI(config.getFalApiKey(), progress);

        // Run API calls on a background thread
        CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Generate image + 3D model via fal.ai
                progress.accept("[1/4] Generating 3D model from prompt...");
                FalAPI.ModelResult modelResult = falApi.generateModelFast(prompt);

                // Step 2: Parse GLB
                progress.accept("[2/4] Parsing 3D model...");
                byte[] glbData = modelResult.glbData();

                // Try to extract embedded texture for UV sampling
                TextureSampler textureSampler = null;
                try {
                    byte[] textureData = GLBParser.extractEmbeddedTexture(glbData);
                    if (textureData != null) {
                        textureSampler = new TextureSampler(textureData);
                        progress.accept("Found embedded texture for color sampling.");
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().log("[Pipeline] Texture extraction failed, using vertex colors: %s", e.getMessage());
                }

                GLBParser.MeshData meshData = GLBParser.parse(glbData, textureSampler);

                // Step 3: Voxelize
                progress.accept("[3/4] Voxelizing mesh (size=" + size + ")...");
                VoxelGrid voxelGrid = Voxelizer.voxelize(meshData, size, textureSampler);
                progress.accept("Voxelized: " + voxelGrid.getVoxelCount() + " voxels");

                return voxelGrid;

            } catch (Exception e) {
                LOGGER.atWarning().log("[Pipeline] Generation failed: %s", e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool())

        // Step 4: Place blocks on the world thread
        .thenAcceptAsync(voxelGrid -> {
            progress.accept("[4/4] Placing blocks in world...");
            HytaleBlockPlacer placer = new HytaleBlockPlacer();
            placer.place(world, voxelGrid, originX, originY, originZ, progress);
        }, world)

        .exceptionally(throwable -> {
            String msg = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
            progress.accept("Generation failed: " + msg);
            LOGGER.atWarning().log("[Pipeline] Pipeline error: %s", msg);
            return null;
        });
    }
}
