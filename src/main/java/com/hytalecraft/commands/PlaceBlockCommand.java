package com.hytalecraft.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * POC command: places a single Rock_Stone block 2 blocks above the player.
 * Usage: /placeblock
 */
public class PlaceBlockCommand extends AbstractPlayerCommand {

    public PlaceBlockCommand() {
        super("placeblock", "Places a Rock_Stone block near the player");
    }

    @Override
    protected void execute(CommandContext context,
                           Store<EntityStore> store,
                           Ref<EntityStore> senderRef,
                           PlayerRef playerRef,
                           World world) {

        Transform transform = playerRef.getTransform();
        Vector3d position = transform.getPosition();

        int x = (int) Math.floor(position.x);
        int y = (int) Math.floor(position.y) + 2;
        int z = (int) Math.floor(position.z);

        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        BlockAccessor chunk = world.getChunkIfLoaded(chunkIndex);

        if (chunk == null) {
            context.sendMessage(Message.raw("Chunk not loaded at position!"));
            return;
        }

        boolean success = chunk.setBlock(x, y, z, "Rock_Stone");

        if (success) {
            context.sendMessage(Message.raw("Placed Rock_Stone at " + x + ", " + y + ", " + z));
        } else {
            context.sendMessage(Message.raw("Failed to place block at " + x + ", " + y + ", " + z));
        }
    }
}
