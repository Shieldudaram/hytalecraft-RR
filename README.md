# HytaleCraft

AI-powered text-to-blocks pipeline for Hytale. Describe anything in text and watch it materialize as blocks in your world.

Showcase: https://x.com/jakob4509/status/2021000591128891437
```
/hcraft generate 64 a giant dragon statue
```

**Text prompt → Image → 3D Model → Voxels → Hytale Blocks**

## How It Works

HytaleCraft uses a multi-stage AI pipeline to turn text descriptions into 3D block structures:

1. **Text → Image** — [fal.ai Z-Image Turbo](https://fal.ai) generates an image from your prompt
2. **Image → 3D Model** — SAM-3D converts the image into a GLB mesh
3. **3D Model → Voxels** — Triangle-splitting voxelizer converts the mesh into a voxel grid
4. **Voxels → Blocks** — CIE-LAB color matching maps each voxel to the closest Hytale block from a palette of ~115 blocks

Blocks are placed in batches of 500/tick to avoid lag.

## Commands

| Command | Description |
|---------|-------------|
| `/hcraft generate <size> <prompt>` | Generate a 3D model and place it at your position |
| `/hcraft setkey <key>` | Set your fal.ai API key |
| `/hcraft status` | Show plugin status, API key, and settings |
| `/placeblock` | Test command — places a single Rock_Stone block |
| `/dumpblocks` | Debug — dumps all registered block type names to a file |

**Size** controls the voxel grid resolution (`16+`).
There is no hard upper cap in command validation. Larger values increase detail, footprint, and generation cost.

## Examples

### Structures
```
/hcraft generate 96 a massive medieval castle with towers and a drawbridge
/hcraft generate 80 a gothic cathedral with flying buttresses
/hcraft generate 64 a japanese pagoda temple surrounded by cherry blossoms
/hcraft generate 80 a giant treehouse built into an ancient oak tree
/hcraft generate 64 a wizard tower with glowing crystal spire
/hcraft generate 96 an ancient roman colosseum
```

### Creatures & Statues
```
/hcraft generate 64 a dragon perched on a cliff with wings spread
/hcraft generate 80 a colossal stone golem with glowing crystal eyes
/hcraft generate 48 a giant phoenix bird made of fire
/hcraft generate 64 a massive kraken emerging from the ocean
/hcraft generate 96 a titan warrior statue holding a sword and shield
```

### Vehicles & Ships
```
/hcraft generate 80 a pirate galleon with full sails
/hcraft generate 64 a viking longship with dragon figurehead
/hcraft generate 48 a hot air balloon with a basket
/hcraft generate 96 a massive airship with propellers and wooden hull
```

### Nature & Landscapes
```
/hcraft generate 64 a giant mushroom with red cap and white spots
/hcraft generate 80 a floating island with a waterfall pouring off the edge
/hcraft generate 48 a huge ancient tree with twisted roots
/hcraft generate 64 a crystal cave formation with stalactites
```

### Fantasy & Fun
```
/hcraft generate 64 a treasure chest overflowing with gold coins
/hcraft generate 80 a giant skull fortress built into a mountainside
/hcraft generate 48 a sword stuck in a stone pedestal
/hcraft generate 64 a portal gate made of obsidian with rune carvings
/hcraft generate 96 a floating wizard academy with multiple towers
```

> **Tip:** Start with size 32 for quick previews, then go to 64–96 for detailed builds.
> Very large sizes are allowed and will show a warning before generation starts.
> Caution: extreme sizes can significantly increase runtime and resource usage.

## Configuration

HytaleCraft stores settings in `config.json` under the mod data directory.

Default config:

```json
{
  "falApiKey": "",
  "defaultSize": 32,
  "maxSize": 128,
  "warnSizeAbove": 128,
  "centerOnPlayerXZ": true,
  "forceLoadChunks": true,
  "chunkLoadTimeoutSeconds": 60
}
```

- `maxSize` (legacy): retained for backward compatibility; no longer enforces an upper size cap.
- `warnSizeAbove` (legacy): retained for backward compatibility; warning uses a built-in soft threshold.
- `centerOnPlayerXZ`: When `true`, placement is centered around the player on X/Z.
- `forceLoadChunks`: When `true`, required chunks are preloaded before block placement.
- `chunkLoadTimeoutSeconds`: If chunk preload exceeds this timeout, generation aborts before placement.

## Setup

### Requirements

- Hytale Early Access with server files
- Java 25
- A [fal.ai](https://fal.ai) API key

### Installation

1. Build the plugin:
   ```bash
   export JAVA_HOME="/opt/homebrew/opt/openjdk"  # or your Java 25 path
   ./gradlew build
   ```

2. Copy the JAR to your server's `mods/` directory:
   ```bash
   cp build/libs/HytaleCraft-0.2.0.jar "<hytale-install>/Server/mods/"
   ```

3. Start the server with assets:
   ```bash
   cd "<hytale-install>/Server"
   java -Djava.awt.headless=true -Xms4G -Xmx4G -jar HytaleServer.jar --assets ../Assets.zip
   ```

4. In-game, set your API key:
   ```
   /hcraft setkey <your-fal-ai-key>
   ```

5. Generate something:
   ```
   /hcraft generate 32 a red mushroom
   ```

## Project Structure

```
src/main/java/com/hytalecraft/
├── HytaleCraft.java                  # Plugin entry point
├── commands/
│   ├── HCraftCommand.java            # /hcraft parent command
│   ├── GenerateCommand.java          # /hcraft generate
│   ├── SetKeyCommand.java            # /hcraft setkey
│   ├── StatusCommand.java            # /hcraft status
│   ├── PlaceBlockCommand.java        # /placeblock (test)
│   └── DumpBlocksCommand.java        # /dumpblocks (debug)
├── pipeline/
│   ├── FalAPI.java                   # fal.ai HTTP client (queue pattern)
│   ├── GLBParser.java                # GLB binary parser (Java NIO)
│   ├── Voxelizer.java                # Triangle-splitting voxelization
│   ├── TextureSampler.java           # UV texture sampling
│   ├── VoxelGrid.java                # Voxel data structures
│   └── GenerationPipeline.java       # Orchestrates the full pipeline
├── world/
│   ├── HytaleBlockPlacer.java        # Batched block placement (500/tick)
│   └── HytaleBlockPalette.java       # CIE-LAB color matching (~115 blocks)
└── config/
    └── HytaleCraftConfig.java        # API key & settings (Gson)
```

## Block Palette

The palette includes ~115 verified Hytale block types mapped by color using CIE-LAB perceptual distance (CIE76 Delta-E):

- **Soil_Clay** — 14 colors (Black, Blue, Cyan, Green, Grey, Lime, Ocean, Orange, Pink, Purple, Red, White, Yellow)
- **Soil_Clay_Smooth** — 12 colors
- **Cloth_Block_Wool** — 20 colors (10 base + 10 light variants)
- **Rock** — Stone, Basalt, Marble, Sandstone, Chalk, Slate, Volcanic, etc.
- **Rock_Crystal** — 8 colors (Blue, Cyan, Green, Pink, Purple, Red, White, Yellow)
- **Wood_*_Planks** — 10 wood types
- **Soil** — Dirt, Sand, Grass, Gravel, Mud, Snow, Ash, Pebbles

## Tech Stack

- **Java 25** — Hytale server requirement
- **fal.ai** — Z-Image Turbo (text→image) + SAM-3D (image→GLB)
- **Gson** — JSON parsing (bundled with Hytale)
- **Java NIO** — GLB binary parsing
- **java.net.http** — HTTP client for API calls
- No external dependencies beyond what Hytale provides

## Credits

Pipeline architecture inspired by [Falcraft](https://github.com/blendi-remade/falcraft) (Minecraft Fabric mod).

## License

MIT
