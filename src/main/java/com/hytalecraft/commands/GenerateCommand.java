package com.hytalecraft.commands;

import com.hytalecraft.config.HytaleCraftConfig;
import com.hytalecraft.pipeline.GenerationPipeline;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class GenerateCommand extends AbstractPlayerCommand {

    private final HytaleCraftConfig config;
    private final RequiredArg<Integer> sizeArg;
    private final RequiredArg<String> promptArg;

    public GenerateCommand(HytaleCraftConfig config) {
        super("generate", "Generate a 3D model from text: /hcraft generate <size> <prompt>");
        this.config = config;
        this.sizeArg = withRequiredArg("size", "Voxel grid size (16-128)", ArgTypes.INTEGER);
        this.promptArg = withRequiredArg("prompt", "Text prompt describing the 3D model", ArgTypes.STRING);
        setAllowsExtraArguments(true);
    }

    @Override
    protected void execute(CommandContext context,
                           Store<EntityStore> store,
                           Ref<EntityStore> senderRef,
                           PlayerRef playerRef,
                           World world) {

        if (!config.hasApiKey()) {
            context.sendMessage(Message.raw("API key not set! Use /hcraft setkey <key> first."));
            return;
        }

        int size = context.get(sizeArg);

        if (size < 16 || size > config.getMaxSize()) {
            context.sendMessage(Message.raw("Size must be between 16 and " + config.getMaxSize()));
            return;
        }

        // Build prompt from the input string: strip command prefix + size arg
        String inputString = context.getInputString();
        String prompt = context.get(promptArg);
        // The prompt arg only captures the first word. Get the rest from inputString.
        // inputString format: "hcraft generate <size> <prompt words...>"
        // Find prompt start after the size argument
        String sizeStr = String.valueOf(size);
        int sizeIdx = inputString.indexOf(sizeStr);
        if (sizeIdx >= 0) {
            String afterSize = inputString.substring(sizeIdx + sizeStr.length()).trim();
            if (!afterSize.isEmpty()) {
                prompt = afterSize;
            }
        }

        Transform transform = playerRef.getTransform();
        Vector3d position = transform.getPosition();
        int originX = (int) Math.floor(position.x);
        int originY = (int) Math.floor(position.y);
        int originZ = (int) Math.floor(position.z);

        context.sendMessage(Message.raw("Starting generation: \"" + prompt + "\" (size: " + size + ")"));

        GenerationPipeline pipeline = new GenerationPipeline(config);
        pipeline.execute(prompt, size, world, originX, originY, originZ,
                msg -> context.sendMessage(Message.raw(msg)));
    }
}
