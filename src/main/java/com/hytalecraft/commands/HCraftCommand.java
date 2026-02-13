package com.hytalecraft.commands;

import com.hytalecraft.config.HytaleCraftConfig;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.util.concurrent.CompletableFuture;

public class HCraftCommand extends AbstractCommand {

    public HCraftCommand(HytaleCraftConfig config) {
        super("hcraft", "HytaleCraft AI generation commands");

        addSubCommand(new SetKeyCommand(config));
        addSubCommand(new StatusCommand(config));
        addSubCommand(new GenerateCommand(config));
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        context.sendMessage(Message.raw(
            "HytaleCraft commands: /hcraft setkey <key> | /hcraft status | /hcraft generate <size> <prompt>"));
        return CompletableFuture.completedFuture(null);
    }
}
