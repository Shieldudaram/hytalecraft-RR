package com.hytalecraft.commands;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DumpBlocksCommand extends AbstractCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public DumpBlocksCommand() {
        super("dumpblocks", "Dump all registered block type names to a file");
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        try {
            var assetMap = BlockType.getAssetMap();
            var map = assetMap.getAssetMap();
            List<String> names = new ArrayList<>(map.keySet());
            Collections.sort(names);

            Path outFile = Path.of("block_types_dump.txt");
            Files.writeString(outFile, String.join("\n", names));

            context.sendMessage(Message.raw("Dumped " + names.size() + " block types to " + outFile.toAbsolutePath()));
            LOGGER.atInfo().log("[DumpBlocks] Dumped %d block types", names.size());

            // Show first 20 as preview
            int preview = Math.min(20, names.size());
            for (int i = 0; i < preview; i++) {
                context.sendMessage(Message.raw("  " + names.get(i)));
            }
            if (names.size() > preview) {
                context.sendMessage(Message.raw("  ... and " + (names.size() - preview) + " more"));
            }
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error: " + e.getMessage()));
            LOGGER.atWarning().log("[DumpBlocks] Error: %s", e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }
}
