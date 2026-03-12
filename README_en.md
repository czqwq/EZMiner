# EZMiner

> 🌐 [中文文档](README.md)

A high-performance chain-mining mod for **Minecraft 1.7.10**, designed specifically for the **GregTech: New Horizons (GTNH)** modpack.

---

## Features at a Glance

- Activate chain mining by **holding the chain key** (or **click-to-toggle** — configurable)
- Two primary modes: **Chain Mode** and **Blast Mode**
- Blast Mode has **5 sub-modes**, switchable with the mouse scroll wheel
- Real-time **HUD** shows the active mode and live block count while mining
- **Block outline preview**: targeted blocks are highlighted through walls before mining begins
- All drops are **batch-collected** and delivered after the chain ends — no entity spam lag
- Full hot-reload support via `/EZMiner reloadConfig` — no restart needed
- Full English and Chinese localisation

---

## Key Bindings

| Key | Default | Description |
|-----|---------|-------------|
| Chain Key | `` ` `` (grave / backtick) | Start/stop chain mining |
| Mode Switch | `V` | Cycle the main mode (Chain ↔ Blast) |
| Mouse Scroll | — | While chain is active, cycle the sub-mode |

---

## Primary Modes

### Chain Mode

Starting from the block you break, EZMiner performs a **priority-queue BFS flood-fill** outward, finding all connected blocks of the **same type**. Blocks closest to the origin are mined first, producing a smooth sphere-shaped expansion effect.

### Blast Mode

Mines all blocks in a configurable radius around the target. Five sub-modes are available:

| Sub-mode | Description |
|----------|-------------|
| All Blocks | Mines every harvestable block in the radius |
| Same Type | Mines only blocks that match the targeted block |
| Tunnel | Digs a straight tunnel in the direction you are looking |
| Ore Only | Mines only ore blocks |
| Logging | Mines only wood blocks (great for clearing trees) |

---

## HUD Display

While chain mining is active, the top-left corner shows:

```
[EZMiner] ■ Chain Active
  ○─ <Main Mode>
  └─ <Sub-mode>
  └─ Chained Blocks: N        ← shown only while mining
```

---

## Block Outline Preview

Aim at a block with the chain key held and EZMiner will render glowing outlines around every block that would be included in the current chain.

- Outlines are **visible through walls** so you can see the full vein even if it's buried
- Only blocks within render distance are outlined — performance is not affected
- Preview stops automatically when targeting air or entities
- Can be disabled with `usePreview = false` in the config

---

## Drop Handling

All drops generated during a chain are **collected silently** and delivered in one batch when the operation finishes, preventing item-entity lag spikes.

- `dropToPlayer = true` (default): drops spawn as item entities at the player's current feet position
- `dropToPlayer = false`: drops spawn as item entities at the location of the first mined block

Stack sizes always respect each item's `maxStackSize`.

---

## Visual Prospecting Integration

When the [Visual Prospecting](https://github.com/GTNewHorizons/VisualProspecting) mod is installed, EZMiner automatically **records ore-vein data to the map overlay** as it mines GregTech ore blocks — no need to manually right-click each ore. This works across all mining modes whenever a GT ore block is encountered.

---

## Configuration

Config file: `config/EZMiner/EZMiner.cfg`  
Hot-reload command: `/EZMiner reloadConfig`

### Server-side Limits (admin-controlled caps)

| Key | Default | Description |
|-----|---------|-------------|
| `bigRadius` | `8` | Maximum chain / blast radius (blocks) |
| `blockLimit` | `1024` | Maximum blocks per chain operation |
| `smallRadius` | `2` | Adjacency detection radius in chain mode — blocks within this range count as "connected" |
| `tunnelWidth` | `1` | Half-width of the tunnel in Tunnel sub-mode (blocks) |

### Client / Player Settings

| Key | Default | Description |
|-----|---------|-------------|
| `addExhaustion` | `0.025` | Food exhaustion added per block mined (negative values restore food) |
| `dropToPlayer` | `true` | Where to deliver batched drops: `true` = at the player's feet (default), `false` = at the origin block where mining started |
| `usePreview` | `true` | Show block outline preview while chain is active |
| `useChainDoneMessage` | `true` | Show a chat summary when a chain operation finishes |
| `chainActivationMode` | `0` | Chain key behaviour: `0` = **hold** to activate (default), `1` = **click to toggle** on/off |

---

## Safety Features

- Checks tool durability before each block — stops automatically before the tool would break
- Exhaustion is consumed per block to prevent "free" infinite mining
- Never mines the block directly under the player's feet to prevent accidental falls
- Ignores fake-player interactions to prevent exploit automation
- Immediately halts all operations when a player logs out
- Full exception handling with detailed log output

---

## Compatibility

- Minecraft **1.7.10**
- Forge **10.13.4.1614**
- GregTech: New Horizons (GTNH) modpack
