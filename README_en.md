# EZMiner

> 🌐 [中文文档](README.md)

A high-performance chain-mining mod designed for **GregTech: New Horizons (GTNH)**, running on Minecraft 1.7.10. Hold a key to mine in bulk — release to stop instantly.

---

## Quick Start

1. Aim at the block you want to mine
2. Hold **`` ` ``** (grave / backtick, rebindable) — block outlines appear
3. Keep holding — blocks are chained and mined
4. Release the key — mining stops immediately
5. Scroll the mouse wheel to switch sub-modes
6. Press `V` to cycle main modes (Blast → Chain → Special)

---

## Three Main Modes

### Blast Mode

Mines all blocks within a configurable radius around the target. **7 sub-modes**, switchable with the mouse wheel:

| Sub-mode | Description |
|----------|-------------|
| All Blocks | Mines every harvestable block in the radius |
| Same Type | Mines only blocks that match the targeted block exactly |
| Tunnel | Digs a straight tunnel in the direction you are facing |
| Ore Only | Mines only ore blocks |
| Logging | Mines only log blocks (bulk tree removal) |
| Inverse Chain | Mines everything in the radius **except** the targeted block type |
| GT Vein Ore | Mines only GT large-vein ore blocks, skipping scattered surface deposits |

### Chain Mode

Starting from the target block, automatically finds and mines all **connected blocks of the same type**. **2 sub-modes**:

| Sub-mode | Description |
|----------|-------------|
| Basic | Blocks must match both ID and metadata to be chained |
| Fuzzy | Blocks only need to match ID — metadata is ignored (e.g. all wool colours together) |

### Special Mode

Non-mining utility functions. **3 sub-modes**:

| Sub-mode | Description |
|----------|-------------|
| Minesweeper | Automatically detects and flags mines in [LootGames](https://github.com/GTNewHorizons/LootGames) minesweeper puzzles |
| Crop Harvest | Right-click to harvest all mature crops in range (supports Vanilla, IC2, CropsNH, Natura) |
| Sudoku Assistant | Automatically fills correct answers in [LootGames](https://github.com/GTNewHorizons/LootGames) Sudoku puzzles |

---

## Controls

### Key Bindings

| Action | Default | Description |
|--------|---------|-------------|
| Activate Chain | `` ` `` | Hold to mine, release to stop |
| Switch Main Mode | `V` | Cycle Blast → Chain → Special |
| Switch Sub-mode | Mouse Wheel | Active while chain key is held; arrow keys also work |
| Open Config | Inventory Screen | Click the `[EZMiner] Settings` button on the left side of your inventory |

### Activation Mode

Two activation behaviours, configurable in the settings GUI:

- **Hold** (default): chain is active while the key is pressed
- **Toggle**: press once to start, press again to stop

### Scroll Lock

While the chain key is held, the **mouse wheel is locked** from switching hotbar slots and is dedicated to cycling sub-modes. This can be disabled in the config.

---

## Block Preview

While the chain key is held and you are aiming at a block, all blocks that would be included in the chain are **highlighted with glowing outlines**:

- Outlines are visible through walls — buried veins are fully visible
- Only blocks within render distance are shown to keep performance smooth
- Two render styles: **Native** (clean single-pass wireframe) and **Modern** (solid visible edges + translucent hidden edges)
- Can be turned off in the config

---

## HUD Display

While the chain key is held, real-time info appears in the top-left corner:

```
[EZMiner] ■ Chain Active
  ○─ Blast Mode
  └─ Same Type
  └─ Chained Blocks: 128
```

- HUD hides automatically when the key is released
- Position can be set with `/EZMiner hud pos <x> <y>`
- Title animation can be set to **Rainbow Bounce** or **Wave Highlight**

---

## Drop Handling

All drops generated during a chain are **collected and held back**, then delivered in a single batch at the player's feet once the operation finishes — no entity-spam lag spikes.

Stack sizes always respect each item's maximum stack limit.

---

## Configuration

EZMiner's configuration is split into client and server parts:

### In-Game Config GUI

Press `E` to open your inventory, then click the `[EZMiner] Settings` button on the left:

- **Client Settings** tab: preview, display, key mode, HUD animation, etc.
- **Server Settings** tab (OP only): radius, block limits, costs, preview caps, etc.

Numeric fields accept direct input; toggle options switch ON/OFF with a click.

### Config File Locations

| File | Path | Description |
|------|------|-------------|
| Client Config | `config/EZMiner/EZMiner.cfg` | Per-player preferences |
| Server Config | `EZMiner/EZMiner_Server.cfg` | Server rules (single-player: `.minecraft/EZMiner/`) |

### Common Server Options (OP only)

| Option | Default | Description |
|--------|---------|-------------|
| Max Radius | 8 | Maximum range for chain/blast operations (blocks) |
| Max Blocks | 1024 | Maximum blocks per operation |
| Adjacency Radius | 2 | Range in which same-type blocks count as "connected" in chain mode |
| Tunnel Width | 1 | Half-width for tunnel blast (0 = 1 wide, 1 = 3 wide) |
| Blocks Per Tick | 16 | Maximum blocks broken per game tick (max 64; lower = less TPS impact) |
| Food Cost | 0.025 | Hunger exhaustion per block mined |
| Drop Location | Player feet | Where batched drops appear — at the player or at the origin block |
| Minesweeper Cooldown | 5 s | Delay between auto-flag operations in Minesweeper mode |
| Sudoku Cooldown | 5 s | Delay between auto-fill operations in Sudoku mode |

### Common Client Options

| Option | Default | Description |
|--------|---------|-------------|
| Block Preview | ON | Show highlighted outlines of chained blocks |
| Completion Message | ON | Show chat summary after each chain operation |
| Key Mode | Hold | Hold = hold-to-activate, Toggle = click-to-toggle |
| Scroll Lock | ON | Block hotbar scroll while chain key is held |
| Hide IGI HUD | OFF | Temporarily hide InGame Info XML HUD while EZMiner is active |
| HUD Animation | Rainbow Bounce | Title animation style |
| Render Style | Native | Preview outline rendering style |

---

## Commands

All commands start with `/EZMiner`:

| Command | Permission | Description |
|---------|------------|-------------|
| `reloadConfig` | OP | Reload server config from disk, syncs to all online players |
| `reloadClientConfig` | Anyone | Reload local client config from disk |
| `active_mode <0\|1>` | Anyone | Set chain key behaviour (0 = Hold, 1 = Toggle) |
| `hud pos <x> <y>` | Anyone | Set HUD pixel position on screen |

---

## Mod Integration

### Visual Prospecting

When [Visual Prospecting](https://github.com/GTNewHorizons/VisualProspecting) is installed, EZMiner **automatically records ore-vein data to the map overlay** as it mines GT ore blocks — no need to manually right-click each ore. Works across all mining modes.

### LootGames

With [LootGames](https://github.com/GTNewHorizons/LootGames) installed, the Special mode's **Minesweeper** and **Sudoku** assistants can automatically solve puzzles for you.

### InGame Info XML

When installed, EZMiner can optionally **auto-hide the IGI HUD** while its own HUD is visible to prevent overlap.

---

## Fortune Cap Override (Mixin — disabled by default)

By default, GT and BartWorks ores only respond to Fortune III and below. EZMiner can lift this cap:

- When enabled, Fortune V, X, and higher enchantment levels work on GT/BW ores
- A configurable max Fortune level (up to 255)
- Option to treat player-placed ores as natural for Fortune bonuses

> ⚠️ This feature is injected via Mixin at game startup. **Changes require a full game restart** and cannot be applied through `/EZMiner reloadConfig`.

---

## Safety Features

- Stops automatically before the tool would break (durability < 1)
- Hunger is consumed per block to prevent infinite free mining
- Never mines the block directly under the player's feet
- Ignores FakePlayer interactions to prevent exploits
- Immediately halts all operations when a player logs out
- Full error logging for easy troubleshooting

---

## Compatibility

- Minecraft **1.7.10**
- Forge **10.13.4.1614+**
- Optimised for GregTech: New Horizons (GTNH) modpack
