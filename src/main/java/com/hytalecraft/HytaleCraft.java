package com.hytalecraft;

import com.hytalecraft.commands.DumpBlocksCommand;
import com.hytalecraft.commands.PlaceBlockCommand;
import com.hytalecraft.commands.HCraftCommand;
import com.hytalecraft.config.HytaleCraftConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import java.util.concurrent.CompletableFuture;

public class HytaleCraft extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private HytaleCraftConfig config;

    public HytaleCraft(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("[HytaleCraft] Plugin loaded (v%s)", getManifest().getVersion().toString());
    }

    @Override
    public CompletableFuture<Void> preLoad() {
        config = new HytaleCraftConfig(getDataDirectory());
        return config.load();
    }

    @Override
    public void setup() {
        getCommandRegistry().registerCommand(new PlaceBlockCommand());
        getCommandRegistry().registerCommand(new HCraftCommand(config));
        getCommandRegistry().registerCommand(new DumpBlocksCommand());
        LOGGER.atInfo().log("[HytaleCraft] Commands registered");
    }

    @Override
    public void start() {
        LOGGER.atInfo().log("[HytaleCraft] Plugin enabled (v%s)", getManifest().getVersion().toString());
    }

    public HytaleCraftConfig getHytaleCraftConfig() {
        return config;
    }
}
