package com.hytalecraft.commands;

import com.hytalecraft.config.HytaleCraftConfig;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

public class StatusCommand extends AbstractCommand {

    private final HytaleCraftConfig config;

    public StatusCommand(HytaleCraftConfig config) {
        super("status", "Show HytaleCraft status");
        this.config = config;
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        String keyStatus = config.hasApiKey() ? "configured" : "NOT SET - use /hcraft setkey <key>";
        context.sendMessage(Message.raw("HytaleCraft v0.2.0"));
        context.sendMessage(Message.raw("API Key: " + keyStatus));
        context.sendMessage(Message.raw("Default size: " + config.getDefaultSize()));
        context.sendMessage(Message.raw("Max size: " + config.getMaxSize()));
        return CompletableFuture.completedFuture(null);
    }
}
