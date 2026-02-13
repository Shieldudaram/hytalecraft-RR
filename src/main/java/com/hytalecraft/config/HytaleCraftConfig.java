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
        int maxSize = 128;
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
                    if (data == null) {
                        data = new ConfigData();
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

    public int getMaxSize() {
        return data.maxSize;
    }
}
