# Copilot Instructions for EZMiner

## Project Overview

EZMiner is a high-performance chain-mining Minecraft Forge mod for **Minecraft 1.7.10**, designed for the **GregTech: New Horizons (GTNH)** modpack. It provides chain mining and blast mining modes with real-time HUD, block-outline preview, and per-player server/client synchronization.

## Architecture

- **Per-player `Manager`** (server-side) tracks each player's chain key state, config, and active mining operation via `BaseOperator`.
- **`ClientProxy`** (client-side) handles HUD rendering, key bindings, and block-outline preview.
- **Network packets** (`/network/`) synchronize mode state, config, and chain count between server and client.
- **`ParallelTick`** runs background block-search threads in sync with server ticks via `TickEventHandler` — no Mixins are used.
- **Block finders** (`/core/founder/`) each extend `BasePositionFounder` and implement a distinct block-discovery algorithm (chain, blast, tunnel, ore, log, etc.).

### Main Modes
- **Chain mode** — BFS/priority-queue expansion from the hit block outward to same-type neighbours.
- **Blast mode** — 5 sub-modes (all-blocks, same-type, tunnel, ore-only, logging), cycled with the scroll wheel while the chain key is held.

## Build & Validate

```bash
# Apply code formatting first, then build
./gradlew spotlessApply build
```

- Uses the **GTNH convention plugin** with **Jabel** to allow Java 17 syntax targeting JVM 8.
- There is no `src/test` directory; there are no unit tests. Validation is done by building the mod successfully.
- Spotless **must** be applied before building, otherwise the build will fail on formatting checks.

## Coding Conventions

- **Java 17 syntax** is allowed (Jabel transpiles to JVM 8).
- **Root package**: `com.czqwq.EZMiner`
- Match the formatting style enforced by Spotless (Google Java Format). Always run `./gradlew spotlessApply` before committing.
- Networking: register new packets in `NetworkMain`, extend `AbstractPacket` from the GTNH network library.
- New mining modes: add a new `BasePositionFounder` subclass in `core/founder/`, wire it into `MinerModeState` and `Manager`.
- Config values are declared in `MinerConfig` and persisted via `Config` / `FileReadUtils`.
- Server-side player state is keyed by UUID in `PlayerManager`.
- Use `MessageUtils` for all player-facing chat messages to support i18n.
- Do **not** use Mixins (`usesMixins = false` in `gradle.properties`).

## Key Files

| File | Purpose |
|------|---------|
| `src/main/java/com/czqwq/EZMiner/EZMiner.java` | Mod entry point, FML lifecycle |
| `src/main/java/com/czqwq/EZMiner/core/Manager.java` | Per-player server-side manager |
| `src/main/java/com/czqwq/EZMiner/core/BaseOperator.java` | Block-breaking executor |
| `src/main/java/com/czqwq/EZMiner/core/MinerModeState.java` | Mode/sub-mode state |
| `src/main/java/com/czqwq/EZMiner/core/MinerConfig.java` | Configuration data |
| `src/main/java/com/czqwq/EZMiner/core/PlayerManager.java` | Multi-player state map |
| `src/main/java/com/czqwq/EZMiner/network/NetworkMain.java` | Packet registration |
| `src/main/java/com/czqwq/EZMiner/client/HudRenderer.java` | HUD rendering |
| `src/main/java/com/czqwq/EZMiner/client/render/MinerRenderer.java` | Block-outline preview |
| `src/main/java/com/czqwq/EZMiner/Config.java` | Global config & hot-reload |
| `gradle.properties` | Mod metadata and build flags |
