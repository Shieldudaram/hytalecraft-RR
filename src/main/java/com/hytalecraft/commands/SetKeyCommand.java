package com.hytalecraft.commands;

import com.hytalecraft.config.HytaleCraftConfig;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import java.util.concurrent.CompletableFuture;

public class SetKeyCommand extends AbstractCommand {

    private final HytaleCraftConfig config;
    private final RequiredArg<String> keyArg;

    public SetKeyCommand(HytaleCraftConfig config) {
        super("setkey", "Set your fal.ai API key");
        this.config = config;
        this.keyArg = withRequiredArg("key", "Your fal.ai API key", ArgTypes.STRING);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        String key = context.get(keyArg);
        config.setFalApiKey(key);
        context.sendMessage(Message.raw("API key saved! Use /hcraft status to verify."));
        return CompletableFuture.completedFuture(null);
    }
}
