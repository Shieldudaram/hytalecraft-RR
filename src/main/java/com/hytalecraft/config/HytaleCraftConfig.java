package com.hytalecraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class HytaleCraftConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private ConfigData data;

    private static class ConfigData {
        String falApiKey = "";
        int defaultSize = 32;
        // Legacy compatibility field. Generation is no longer capped by config maxSize.
        int maxSize = 128;
        // Legacy compatibility field. Warning threshold now uses a built-in soft limit.
        Integer warnSizeAbove = 128;
        Boolean centerOnPlayerXZ = true;
        Boolean forceLoadChunks = true;
        Integer chunkLoadTimeoutSeconds = 60;
    }

    public HytaleCraftConfig(Path dataDirectory) {
        this.configFile = dataDirectory.resolve("config.json");
        this.data = new ConfigData();
    }

    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (Files.exists(configFile)) {
                    String json = Files.readString(configFile);
                    data = GSON.fromJson(json, ConfigData.class);
                    boolean changed = sanitize();
                    if (changed) {
                        save();
                    }
                    LOGGER.atInfo().log("[HytaleCraft] Config loaded");
                } else {
                    Files.createDirectories(configFile.getParent());
                    save();
                    LOGGER.atInfo().log("[HytaleCraft] Default config created");
                }
            } catch (IOException e) {
                LOGGER.atWarning().log("[HytaleCraft] Failed to load config: %s", e.getMessage());
                data = new ConfigData();
            }
        });
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.atWarning().log("[HytaleCraft] Failed to save config: %s", e.getMessage());
        }
    }

    public String getFalApiKey() {
        return data.falApiKey;
    }

    public void setFalApiKey(String key) {
        data.falApiKey = key;
        save();
    }

    public boolean hasApiKey() {
        return data.falApiKey != null && !data.falApiKey.isEmpty();
    }

    public int getDefaultSize() {
        return data.defaultSize;
    }

    @Deprecated
    public int getMaxSize() {
        return data.maxSize;
    }

    @Deprecated
    public int getWarnSizeAbove() {
        return data.warnSizeAbove != null ? data.warnSizeAbove : 128;
    }

    public boolean isCenterOnPlayerXZ() {
        return data.centerOnPlayerXZ == null || data.centerOnPlayerXZ;
    }

    public boolean isForceLoadChunks() {
        return data.forceLoadChunks == null || data.forceLoadChunks;
    }

    public int getChunkLoadTimeoutSeconds() {
        return data.chunkLoadTimeoutSeconds != null ? data.chunkLoadTimeoutSeconds : 60;
    }

    private boolean sanitize() {
        boolean changed = false;

        if (data == null) {
            data = new ConfigData();
            return true;
        }

        if (data.defaultSize < 16) {
            data.defaultSize = 16;
            changed = true;
        }

        if (data.maxSize < 16) {
            data.maxSize = 16;
            changed = true;
        }

        if (data.warnSizeAbove == null) {
            data.warnSizeAbove = 128;
            changed = true;
        } else if (data.warnSizeAbove < 16) {
            data.warnSizeAbove = 16;
            changed = true;
        }

        if (data.centerOnPlayerXZ == null) {
            data.centerOnPlayerXZ = true;
            changed = true;
        }

        if (data.forceLoadChunks == null) {
            data.forceLoadChunks = true;
            changed = true;
        }

        if (data.chunkLoadTimeoutSeconds == null) {
            data.chunkLoadTimeoutSeconds = 60;
            changed = true;
        } else if (data.chunkLoadTimeoutSeconds < 1) {
            data.chunkLoadTimeoutSeconds = 1;
            changed = true;
        }

        return changed;
    }
}
