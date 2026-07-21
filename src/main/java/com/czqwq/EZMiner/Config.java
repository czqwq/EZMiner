package com.czqwq.EZMiner;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;

import com.czqwq.EZMiner.config.ConfigValidator;
import com.czqwq.EZMiner.core.MinerConfig;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class Config {

    public static Configuration clientConfiguration;
    public static Configuration serverConfiguration;

    // ===== Server-side authoritative settings =====
    public static int bigRadius = 8;
    public static int blockLimit = 1024;
    public static int smallRadius = 2;
    public static int tunnelWidth = 1;
    public static double addExhaustion = 0.025;
    public static boolean dropToPlayer = true;
    public static boolean serverUsePreview = true;
    public static int serverMaxPreviewBigRadius = 8;
    public static int serverMaxPreviewBlockLimit = 1024;
    /**
     * Maximum number of blocks broken per server tick during a chain operation.
     * Lower values reduce server load; higher values complete the chain faster.
     */
    public static int breakPerTick = 16;
    /**
     * Maximum number of blocks broken per server tick during <strong>cached</strong>
     * chain operations (no background search overhead, so a higher default is safe).
     */
    public static int cachedBreakPerTick = 64;
    /**
     * Crazy Mode — removes the per-tick block limit so chains complete as fast as
     * possible. A built-in safety cap prevents the server from freezing.
     * ⚠ May cause lag on extremely large veins. Default: false.
     */
    public static boolean crazyMode = false;
    /** Spawn drops immediately per-block instead of batching at chain end (avoids lag spike). */
    public static boolean dropImmediately = false;
    /**
     * XP drop mode for chain mining.
     * 0 = Immediate — XP orbs spawn per-block as vanilla does.
     * 1 = Delayed — XP is accumulated and spawned when the chain ends (default).
     */
    public static int xpDropMode = 1;
    /**
     * When {@code true} (default) and {@link #xpDropMode} is 1 (delayed), all
     * accumulated XP is merged into a single large XP orb when the chain ends.
     * When {@code false}, individual per-block XP orbs are spawned at the end.
     * This option has no effect when {@link #xpDropMode} is 0 (immediate).
     */
    public static boolean mergeXPOrbs = true;
    // ===== Block Swap settings =====
    /** Master switch for block swap mode. When false, the mode is hidden from the list. */
    public static boolean enableBlockSwapMode = false;
    /** Server-side max block swap radius (blocks). */
    public static int blockSwapRadius = 8;
    /** Server-side max blocks that can be swapped in a single operation. */
    public static int blockSwapLimit = 1024;
    /** Enable cached chain sub-modes (2/3). WIP experimental feature — enable at own risk. */
    public static boolean enableCachedChain = false;
    /**
     * When enabled, the BFS search will load chunks from disk as needed
     * during chain mining operations instead of stopping at chunk boundaries.
     * This allows chain mining to reach blocks beyond the player's current
     * view distance. May cause brief server lag when many chunks are loaded.
     * Default: false.
     */
    public static boolean enableChainChunkLoading = false;
    /**
     * Seconds without any new blocks mined before the idle countdown begins.
     * When the chain hits the loaded-chunk boundary and stops making progress,
     * this timeout triggers a player-visible countdown before auto-cancel.
     * Set to -1 to disable. Default: 50.
     */
    public static int chainIdleTimeoutSeconds = 50;
    /**
     * Seconds of countdown (with chat warnings each second) before the chain
     * operation is automatically cancelled due to inactivity.
     * Set to -1 to disable. Default: 10.
     */
    public static int chainIdleCountdownSeconds = 10;
    /**
     * Cooldown in seconds between successive minesweeper auto-flag operations
     * when the chain key is held in Special / Minesweeper mode. Minimum: 0.1 s (2 ticks).
     */
    public static double minesweeperProbeCooldownSeconds = 5.0;
    /**
     * Cooldown in seconds between successive Sudoku auto-fill operations
     * when the chain key is held in Special / Sudoku mode. Minimum: 0.1 s (2 ticks).
     */
    public static double sudokuProbeCooldownSeconds = 5.0;
    /**
     * When true, encountering a block that the player's current tool cannot harvest
     * will immediately stop the entire chain operation (instead of silently skipping
     * it). This prevents accidental destruction of blocks that require a higher-tier
     * tool. Default: false (skip unharvestable blocks).
     */
    public static boolean stopOnUnbreakable = false;
    /**
     * When true, EZMiner's fast harvest paths fire the per-block Forge
     * {@code BlockEvent.BreakEvent} for every chained block (honouring cancellation
     * and forwarding the event-modified XP value), restoring compatibility with
     * protection/claim mods and BreakEvent listeners at a small per-block cost.
     * Default: false (fast paths skip the event, as originally designed).
     */
    public static boolean fireBreakEvent = false;
    /**
     * Cooldown in ticks between successive uses of the chain mining feature.
     * 0 = no cooldown (default). 20 ticks = 1 second.
     * When non-zero, the player cannot start a new chain until the cooldown expires.
     * Creative-mode players are exempt from the cooldown.
     */
    public static int chainCooldownTicks = 0;

    /**
     * Number of background worker threads used for parallel block search during chain
     * mining operations. Higher values scan large radii faster but consume more CPU.
     * Set to 0 to disable multi-threaded search entirely (falls back to single-thread).
     * Range: 0–8. Default: 3.
     */
    public static int searchWorkerThreads = 3;

    /**
     * Pause/interrupt check cadence for background search threads. The search thread
     * calls {@code waitUntil()} after this many processed positions — parking until the
     * next tick starts when the tick has ended, and noticing chain cancellation.
     * <p>
     * 0 = check on every position (safest; legacy behavior). Higher values check less
     * often — searches run slightly faster but react to tick end / cancellation more
     * coarsely. Range: 0–4096. Default: 0.
     */
    public static int searchBudgetPerYield = 0;

    /**
     * When true, chain-mode BFS uses two plain queues (currentFrontier + nextFrontier)
     * instead of a single PriorityQueue — O(1) poll vs O(log n). The wave-front order
     * differs from distance-ordered expansion; when a vein exceeds blockLimit, which
     * blocks get selected may differ. Default: false (use PriorityQueue).
     */
    public static boolean useDualFrontierBfs = false;

    /**
     * When true, visited-position tracking uses fastutil's primitive
     * {@code LongOpenHashSet} (behind a synchronized wrapper) instead of
     * {@code ConcurrentHashMap.newKeySet()}. Avoids Long boxing overhead for large
     * searches. Default: true (use primitive set for reduced GC pressure).
     */
    public static boolean usePrimitiveVisitedSet = true;

    // ===== Stability & reliability features =====

    /** When true (default), validates config values after load and logs warnings for issues. */
    public static boolean enableConfigValidation = true;

    /**
     * When true (default), compat reflection calls use safe wrappers that catch
     * {@code LinkageError} and avoid triggering static initialisers on class load.
     * When false, raw {@code Class.forName()} is used (legacy behavior).
     */
    public static boolean enableSafeReflection = true;

    /**
     * When true (default), mixins are only applied when the target class bytecode
     * shape matches expectations — preventing crashes on GTNH version mismatches.
     * Requires a game restart to take effect (mixin plugin is loaded at startup).
     */
    public static boolean enableMixinCapabilityGates = true;

    /**
     * When true (default), network packet handlers verify they are running on the
     * main thread and defer execution if called from a netty IO thread.
     */
    public static boolean enableMainThreadGuard = true;

    /**
     * When true (default), a tick-based watchdog monitors chain operations and
     * force-resets the state if no progress is made within
     * {@link #chainWatchdogTimeoutTicks} ticks.
     */
    public static boolean enableChainWatchdog = true;

    /**
     * Number of server ticks without any block harvested before the watchdog
     * force-cancels a chain operation. 100 ticks = 5 seconds at 20 TPS.
     * Only used when {@link #enableChainWatchdog} is true.
     */
    public static int chainWatchdogTimeoutTicks = 100;

    /**
     * When true (default), if drops cannot be spawned at the player's position
     * (e.g. chunk unloaded), the drop collector tries fallback positions:
     * respawn point → world spawn → discard with warning.
     */
    public static boolean enableDropFallbackChain = true;

    /**
     * When true, background search threads respect a deadline timestamp and yield
     * at tick boundaries even if {@code unPause()} is never called (defense against
     * founder-thread hangs). Default: false (experimental — may affect search
     * throughput).
     */
    public static boolean enableBudgetDeadline = false;

    /**
     * When true (default), the server signals the client to switch tools when the
     * current tool is about to break during chain mining, instead of cancelling.
     */
    public static boolean enableToolBreakHandoff = true;

    /** Max ticks the server waits for the client to switch tools (default: 5). */
    public static int toolBreakHandoffTimeoutTicks = 5;

    /**
     * Suppress Hodgepodge off-thread-read warnings for EZMiner background threads.
     * When true, threads named "EZMiner-*" will not log warnings when reading the
     * chunk map from off-thread (the snapshot still serves data safely).
     * Applied via Log4j2 filter — no restart required. Default: true.
     */
    public static boolean suppressHodgepodgeWarnings = true;

    /**
     * Enable direct {@code ExtendedBlockStorage.func_150818_a} writes during chain
     * mining, bypassing {@code World.setBlock} and its neighbor notifications.
     * <p>
     * When enabled, blocks are written directly to sub-chunk arrays and
     * {@code markBlockForUpdate} is called per block to sync clients. This eliminates
     * 3 chunk lookups per block compared to the vanilla path but skips
     * {@code onNeighborBlockChange} notifications — adjacent redstone/mechanism blocks
     * will NOT react to mined blocks.
     * <p>
     * When disabled (default), the existing fast-harvest mixin path is used instead:
     * {@code World.setBlock(x,y,z,air,0,2)} with flag=2 (client update only, skip
     * neighbor notification). This is safer and still avoids 6 neighbor-change calls
     * per block.
     * <p>
     * Compatible with EndlessIDs and Hodgepodge. Default: false (safe mode).
     */
    public static boolean useChunkCachedHarvest = false;

    /** Remove Fortune III cap for GT/BW ore drops. Mixin-based — requires game restart. */
    public static boolean enableUnlimitedOreFortune = false;

    /** Max Fortune level for GT/BW ores (clamped to 255). Mixin-based — requires game restart. */
    public static int maxFortuneLevel = 3;

    /**
     * When {@code true}, ores that were placed by a player (i.e. not naturally generated) are
     * treated as naturally generated and therefore also benefit from the Fortune enchantment bonus when
     * {@link #enableUnlimitedOreFortune} is {@code true}.
     *
     * <p>
     * <strong>Like {@link #enableUnlimitedOreFortune}, this value is read once at startup via
     * Mixin and cannot be changed without restarting the game.</strong>
     *
     * <p>
     * Default: {@code false}.
     */
    public static boolean enableFortuneForPlacedOre = false;

    // ===== Client-side settings =====
    public static final String CLIENT_CATEGORY = "Client";
    public static boolean usePreview = true;
    public static boolean useChainDoneMessage = true;
    public static int hudPosX = 5;
    public static int hudPosY = 5;
    /** Client preferred chain radius (capped by server at runtime). */
    public static int clientBigRadius = 8;
    /** Client preferred chain block limit (capped by server at runtime). */
    public static int clientBlockLimit = 1024;
    /** Client preferred adjacency radius (capped by server at runtime). */
    public static int clientSmallRadius = 2;
    /** Client preferred tunnel width (capped by server at runtime). */
    public static int clientTunnelWidth = 1;
    /** Client preferred block swap radius (capped by server at runtime). */
    public static int clientBlockSwapRadius = 8;
    /** Client preferred block swap limit (capped by server at runtime). */
    public static int clientBlockSwapLimit = 1024;
    /**
     * Maximum search radius used by the client-side preview renderer.
     * Independent of the server's {@code bigRadius}: the preview can use a smaller value
     * to keep frame-rate smooth on large ore veins.
     */
    public static int previewBigRadius = 8;
    /**
     * Maximum number of blocks shown in the client-side preview.
     * A lower limit makes the preview search finish faster and keeps GPU vertex data small.
     */
    public static int previewBlockLimit = 256;
    // ===== Runtime server overrides (client memory only) =====
    public static int runtimeServerMaxBigRadius = Integer.MAX_VALUE;
    public static int runtimeServerMaxBlockLimit = Integer.MAX_VALUE;
    public static int runtimeServerMaxSmallRadius = Integer.MAX_VALUE;
    public static int runtimeServerMaxTunnelWidth = Integer.MAX_VALUE;
    public static int runtimeServerMaxBlockSwapRadius = Integer.MAX_VALUE;
    public static int runtimeServerMaxBlockSwapLimit = Integer.MAX_VALUE;
    public static int runtimeServerMaxPreviewBigRadius = Integer.MAX_VALUE;
    public static int runtimeServerMaxPreviewBlockLimit = Integer.MAX_VALUE;
    public static boolean runtimeServerUsePreview = true;
    /** Chain key mode: 0 = Hold (default), 1 = Toggle. */
    public static int chainActivationMode = 0;
    /**
     * When {@code true} and InGame Info XML is installed, EZMiner will temporarily set
     * {@code ConfigurationHandler.showHUD = false} while its own HUD is visible (i.e. while
     * the chain key is held), and restore the original value when the key is released.
     * This prevents the two HUDs from overlapping on screen.
     */
    public static boolean suppressIngameInfoHud = false;

    /** HUD brand animation: 0 = Rainbow Bounce, 1 = Wave Highlight. */
    public static int hudAnimationStyle = 0;

    /** Preview render style: 0 = Native wireframe, 1 = Modern two-pass (Ultimine style). */
    public static int renderStyle = 0;
    /** Block hotbar scrolling while chain key held (reserved for sub-mode switching). */
    public static boolean blockScrollOnChainKey = true;

    /** Master switch for Smart Tool Switching. */
    public static boolean smartToolSwitchEnabled = true;

    /** Smart Tool Switch mode: 0 = Hold, 1 = Toggle (default). */
    public static int smartToolSwitchActivationMode = 1;

    // ===== Smart Tool Switch advanced settings =====

    /**
     * When true (default), tool scoring factors remaining durability so tools
     * with more remaining uses rank higher than identical-but-worn tools.
     */
    public static boolean smartToolSwitchDurabilityScore = true;

    /**
     * When true, the smart tool switch scans all 36 inventory slots instead of
     * only the 9-slot hotbar. Tools in the main inventory are swapped into the
     * hotbar automatically. Default: false.
     */
    public static boolean smartToolSwitchFullInventory = false;

    /**
     * Comma-separated list of preferred tool item registry names. When multiple
     * tools have equal harvest levels, those matching earlier entries are preferred.
     * Example: {@code "minecraft:diamond_pickaxe, IC2:itemToolDiamondDrill"}.
     * Leave empty to use harvest-level-only ranking (default).
     */
    public static String preferredTools = "";

    private static final String BLOCK_SCROLL_ON_CHAIN_KEY_COMMENT = "When true, mouse-wheel scrolling is blocked from changing the inventory hotbar slot while the chain key is held, so that scrolling is reserved for switching EZMiner sub-modes.";

    private static final String SMART_TOOL_SWITCH_ENABLED_COMMENT = "Master switch for Smart Tool Switching. When false, the feature is completely disabled.";

    private static final String SMART_TOOL_SWITCH_ACTIVATION_MODE_COMMENT = "Smart Tool Switch key activation mode. "
        + "0 = Hold (tool switching active only while key is held). "
        + "1 = Toggle (press once to activate, press again to deactivate, default).";

    private static final String RENDER_STYLE_COMMENT = "Preview outline rendering style. "
        + "0 = Native (single-pass wireframe, original). "
        + "1 = Modern (two-pass: solid visible lines + translucent hidden lines, FTB-Ultimine inspired).";

    private static final String HUD_ANIMATION_STYLE_COMMENT = "HUD brand animation style. 0 = Rainbow Bounce (original). 1 = Wave Highlight (letters light up one-by-one then fill left-to-right).";

    private static final String CHAIN_ACTIVATION_MODE_COMMENT = "Controls how the chain key activates mining. "
        + "0 = Hold (keep the key held to keep mining, release to stop). "
        + "1 = Toggle (press once to start, press again to stop).";
    private static final String SUPPRESS_INGAMEINFO_HUD_COMMENT = "When true and InGame Info XML is installed, EZMiner will temporarily hide the InGame Info XML HUD "
        + "while its own HUD is visible (chain key held), then restore it when the key is released. "
        + "Prevents the two HUDs from overlapping. Default: false.";

    public static void init(File clientConfigFile, File serverConfigFile) {
        if (clientConfiguration == null) {
            clientConfiguration = new Configuration(clientConfigFile);
        }
        if (serverConfiguration == null) {
            serverConfiguration = new Configuration(serverConfigFile);
        }
        load();
    }

    public static void load() {
        if (serverConfiguration != null) {
            loadServerOnlyInternal();
            if (FMLCommonHandler.instance()
                .getSide()
                .isServer()) {
                runtimeServerMaxBigRadius = bigRadius;
                runtimeServerMaxBlockLimit = blockLimit;
                runtimeServerMaxSmallRadius = smallRadius;
                runtimeServerMaxTunnelWidth = tunnelWidth;
                runtimeServerMaxBlockSwapRadius = blockSwapRadius;
                runtimeServerMaxBlockSwapLimit = blockSwapLimit;
                runtimeServerMaxPreviewBigRadius = serverMaxPreviewBigRadius;
                runtimeServerMaxPreviewBlockLimit = serverMaxPreviewBlockLimit;
                runtimeServerUsePreview = serverUsePreview;
            }
            if (serverConfiguration.hasChanged()) {
                serverConfiguration.save();
            }
        }
        if (clientConfiguration != null) {
            loadClientOnlyInternal();
            clampClientMiningToServerCaps();
            clampClientPreviewToServerCaps();
            if (clientConfiguration.hasChanged()) {
                clientConfiguration.save();
            }
        }
        if (enableConfigValidation) {
            ConfigValidator.validate();
        }
    }

    private static void loadServerOnlyInternal() {
        serverConfiguration.load();
        bigRadius = serverConfiguration.getInt(
            "bigRadius",
            Configuration.CATEGORY_GENERAL,
            8,
            0,
            Integer.MAX_VALUE,
            "Maximum radius (in blocks) for chain and blast operations.");
        blockLimit = serverConfiguration.getInt(
            "blockLimit",
            Configuration.CATEGORY_GENERAL,
            1024,
            0,
            Integer.MAX_VALUE,
            "Maximum number of blocks that can be harvested in a single chain operation.");
        smallRadius = serverConfiguration.getInt(
            "smallRadius",
            Configuration.CATEGORY_GENERAL,
            2,
            0,
            Integer.MAX_VALUE,
            "Adjacency detection radius for chain mode. Two ore blocks are considered connected "
                + "if they are within this many blocks of each other.");
        tunnelWidth = serverConfiguration.getInt(
            "tunnelWidth",
            Configuration.CATEGORY_GENERAL,
            1,
            0,
            Integer.MAX_VALUE,
            "Half-width of the tunnel dug by Tunnel blast sub-mode (0 = 1-block wide, 1 = 3-block wide, etc.).");
        breakPerTick = serverConfiguration.getInt(
            "breakPerTick",
            Configuration.CATEGORY_GENERAL,
            16,
            1,
            512,
            "Maximum blocks broken per server tick during a chain operation. "
                + "Lower values reduce server load (recommended: 16).");
        cachedBreakPerTick = serverConfiguration.getInt(
            "cachedBreakPerTick",
            Configuration.CATEGORY_GENERAL,
            64,
            1,
            1024,
            "Maximum blocks broken per server tick during a cached chain operation. "
                + "Cached chains have no background search overhead, so a higher rate is safe.");
        crazyMode = serverConfiguration.getBoolean(
            "crazyMode",
            Configuration.CATEGORY_GENERAL,
            false,
            "Crazy Mode — removes the per-tick block limit. Chains complete as fast as "
                + "possible. A built-in safety cap prevents server freezes. "
                + "⚠ May cause lag on very large veins. Default: false.");
        dropImmediately = serverConfiguration.getBoolean(
            "dropImmediately",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, drops are spawned immediately after each block harvest during a "
                + "chain, instead of being batched and flushed at the end. "
                + "Eliminates the end-of-chain lag spike on large veins.");
        xpDropMode = serverConfiguration.getInt(
            "xpDropMode",
            Configuration.CATEGORY_GENERAL,
            1,
            0,
            1,
            "XP drop mode for chain-mined blocks. " + "0 = Immediate (XP orbs spawn per-block as vanilla does). "
                + "1 = Delayed (XP is collected and spawned when the chain ends, default).");
        mergeXPOrbs = serverConfiguration.getBoolean(
            "mergeXPOrbs",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default) and xpDropMode is 1 (delayed), all accumulated XP is "
                + "merged into a single large XP orb when the chain ends. "
                + "When false, individual per-block XP orbs are spawned. "
                + "Has no effect when xpDropMode is 0 (immediate).");
        enableBlockSwapMode = serverConfiguration.getBoolean(
            "enableBlockSwapMode",
            Configuration.CATEGORY_GENERAL,
            false,
            "Master switch for the Block Swap special sub-mode. "
                + "When false (default), the mode is hidden from the list.");
        blockSwapRadius = serverConfiguration.getInt(
            "blockSwapRadius",
            Configuration.CATEGORY_GENERAL,
            8,
            0,
            Integer.MAX_VALUE,
            "Maximum radius for block swap mode. Controls how far from the target block " + "the search expands.");
        blockSwapLimit = serverConfiguration.getInt(
            "blockSwapLimit",
            Configuration.CATEGORY_GENERAL,
            1024,
            0,
            Integer.MAX_VALUE,
            "Maximum number of blocks that can be swapped in a single block-swap operation.");
        enableCachedChain = serverConfiguration.getBoolean(
            "enableCachedChain",
            Configuration.CATEGORY_GENERAL,
            false,
            "WIP — When true, the cached chain sub-modes appear in the chain mode cycle. "
                + "Cached chain pre-calculates block positions on the server thread and "
                + "feeds them to the operator without a background founder. "
                + "May have bugs with certain modded ores. Default: false.");
        enableChainChunkLoading = serverConfiguration.getBoolean(
            "enableChainChunkLoading",
            Configuration.CATEGORY_GENERAL,
            false,
            "When enabled, actively loads unloaded chunks during chain and blast mining "
                + "operations instead of stopping at chunk boundaries. "
                + "Chunks are loaded incrementally (up to 4 per tick) to avoid server stalls. "
                + "Default: false.");
        chainIdleTimeoutSeconds = serverConfiguration.getInt(
            "chainIdleTimeoutSeconds",
            Configuration.CATEGORY_GENERAL,
            50,
            -1,
            Integer.MAX_VALUE,
            "Seconds without any new blocks mined before the idle countdown begins. "
                + "Set to -1 to disable. Default: 50.");
        chainIdleCountdownSeconds = serverConfiguration.getInt(
            "chainIdleCountdownSeconds",
            Configuration.CATEGORY_GENERAL,
            10,
            -1,
            Integer.MAX_VALUE,
            "Countdown seconds before auto-cancelling an idle chain. " + "Set to -1 to disable. Default: 5.");
        searchWorkerThreads = serverConfiguration.getInt(
            "searchWorkerThreads",
            Configuration.CATEGORY_GENERAL,
            3,
            0,
            8,
            "Number of background worker threads for parallel block search. "
                + "Higher values scan large radii faster. 0 disables multi-threading. "
                + "Default: 3. Max: 8.");
        searchBudgetPerYield = serverConfiguration.getInt(
            "searchBudgetPerYield",
            Configuration.CATEGORY_GENERAL,
            0,
            0,
            4096,
            "Pause/interrupt check cadence for background search threads. "
                + "The search thread checks tick boundaries and cancellation after this many positions. "
                + "0 = check on every position (safest; legacy behavior). "
                + "Higher values = slightly faster searches, coarser reaction. Default: 0.");
        useDualFrontierBfs = serverConfiguration.getBoolean(
            "useDualFrontierBfs",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, chain-mode BFS uses two plain queues instead of a PriorityQueue "
                + "for O(1) frontier operations. Default: false.");
        usePrimitiveVisitedSet = serverConfiguration.getBoolean(
            "usePrimitiveVisitedSet",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true, visited-set tracking uses fastutil's primitive long hash set "
                + "instead of ConcurrentHashMap to avoid Long boxing. Default: true.");
        enableConfigValidation = serverConfiguration.getBoolean(
            "enableConfigValidation",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default), validates config values after load and logs warnings for issues.");
        enableSafeReflection = serverConfiguration.getBoolean(
            "enableSafeReflection",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default), compat reflection uses safe wrappers with LinkageError guards.");
        enableMixinCapabilityGates = serverConfiguration.getBoolean(
            "enableMixinCapabilityGates",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default), mixins are only applied when target class bytecode matches expectations. "
                + "Requires game restart to take effect.");
        enableMainThreadGuard = serverConfiguration.getBoolean(
            "enableMainThreadGuard",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default), network packet handlers verify main-thread execution.");
        enableChainWatchdog = serverConfiguration.getBoolean(
            "enableChainWatchdog",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default), a tick-based watchdog force-cancels stuck chain operations.");
        chainWatchdogTimeoutTicks = serverConfiguration.getInt(
            "chainWatchdogTimeoutTicks",
            Configuration.CATEGORY_GENERAL,
            100,
            20,
            1200,
            "Ticks without progress before watchdog fires. 100 = 5 seconds at 20 TPS. "
                + "Only used when enableChainWatchdog is true.");
        enableDropFallbackChain = serverConfiguration.getBoolean(
            "enableDropFallbackChain",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default), drop flush tries fallback positions (respawn → world spawn) "
                + "if the player's chunk is unloaded.");
        enableBudgetDeadline = serverConfiguration.getBoolean(
            "enableBudgetDeadline",
            Configuration.CATEGORY_GENERAL,
            false,
            "EXPERIMENTAL — When true, search threads respect a deadline timestamp for tick-boundary "
                + "yielding. Default: false.");
        enableToolBreakHandoff = serverConfiguration.getBoolean(
            "enableToolBreakHandoff",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true (default), the server signals the client to switch tools when the current "
                + "tool is about to break during chain mining.");
        toolBreakHandoffTimeoutTicks = serverConfiguration.getInt(
            "toolBreakHandoffTimeoutTicks",
            Configuration.CATEGORY_GENERAL,
            5,
            1,
            40,
            "Max ticks the server waits for the client to switch tools (default: 5 = 0.25s).");
        addExhaustion = serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "addExhaustion",
                0.025,
                "Food exhaustion added to the player for each block mined during a chain operation. "
                    + "Negative values restore food.",
                -Double.MAX_VALUE,
                Double.MAX_VALUE)
            .getDouble();
        dropToPlayer = serverConfiguration.getBoolean(
            "dropToPlayer",
            Configuration.CATEGORY_GENERAL,
            true,
            "Controls where batched drops are spawned after a chain operation. "
                + "true = at the player's current feet position (default). "
                + "false = at the center of the first block that was mined.");
        serverUsePreview = serverConfiguration.getBoolean(
            "serverUsePreview",
            Configuration.CATEGORY_GENERAL,
            true,
            "Server global preview switch. false disables preview for all clients.");
        serverMaxPreviewBigRadius = serverConfiguration.getInt(
            "serverMaxPreviewBigRadius",
            Configuration.CATEGORY_GENERAL,
            8,
            0,
            Integer.MAX_VALUE,
            "Server-side maximum allowed preview radius.");
        serverMaxPreviewBlockLimit = serverConfiguration.getInt(
            "serverMaxPreviewBlockLimit",
            Configuration.CATEGORY_GENERAL,
            1024,
            0,
            Integer.MAX_VALUE,
            "Server-side maximum allowed preview block count.");
        minesweeperProbeCooldownSeconds = serverConfiguration
            .get(
                "minesweeperProbeCooldownSeconds",
                Configuration.CATEGORY_GENERAL,
                5.0,
                "Cooldown in seconds between successive auto-flag operations in Special / Minesweeper mode "
                    + "(while the chain key is held). Minimum: 0.1. Default: 5.",
                0.1,
                3600.0)
            .getDouble();
        sudokuProbeCooldownSeconds = serverConfiguration
            .get(
                "sudokuProbeCooldownSeconds",
                Configuration.CATEGORY_GENERAL,
                5.0,
                "Cooldown in seconds between successive auto-fill operations in Special / Sudoku mode "
                    + "(while the chain key is held). Minimum: 0.1. Default: 5.",
                0.1,
                3600.0)
            .getDouble();
        stopOnUnbreakable = serverConfiguration.getBoolean(
            "stopOnUnbreakable",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, encountering a block that cannot be harvested with the current tool "
                + "will immediately stop the entire chain operation instead of silently skipping it. "
                + "Default: false.");
        fireBreakEvent = serverConfiguration.getBoolean(
            "fireBreakEvent",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, the fast harvest paths fire the per-block Forge BlockEvent.BreakEvent for "
                + "every chained block, honouring cancellation and event-modified XP. Enables "
                + "compatibility with protection/claim mods at a small per-block performance cost. "
                + "Default: false.");
        chainCooldownTicks = serverConfiguration.getInt(
            "chainCooldownTicks",
            Configuration.CATEGORY_GENERAL,
            0,
            0,
            Integer.MAX_VALUE,
            "Cooldown in ticks between successive uses of the chain mining feature. "
                + "0 = no cooldown. 20 ticks = 1 second. "
                + "Creative-mode players are exempt from the cooldown. "
                + "Default: 0.");
        enableUnlimitedOreFortune = serverConfiguration.getBoolean(
            "enableUnlimitedOreFortune",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, removes the Fortune III cap for GT / BartWorks ore drops so that Fortune levels "
                + "above III yield additional drops. "
                + "IMPORTANT: This option is implemented via Mixin and is applied once at JVM startup. "
                + "It CANNOT be changed via /EZMiner reloadConfig — a full game restart is required. "
                + "Default: false.");
        useChunkCachedHarvest = serverConfiguration.getBoolean(
            "useChunkCachedHarvest",
            Configuration.CATEGORY_GENERAL,
            false,
            "EXPERIMENTAL — Enables a faster block-harvest path during chain mining. "
                + "Skips some safety checks for maximum speed. "
                + "⚠ Use with caution. Default: false.");
        suppressHodgepodgeWarnings = serverConfiguration.getBoolean(
            "suppressHodgepodgeWarnings",
            Configuration.CATEGORY_GENERAL,
            true,
            "When true, suppresses Hodgepodge off-thread-read warnings for EZMiner background threads. "
                + "Applied via Log4j2 filter at mod init — survives /EZMiner reloadConfig without restart. "
                + "Default: true.");
        maxFortuneLevel = serverConfiguration.getInt(
            "maxFortuneLevel",
            Configuration.CATEGORY_GENERAL,
            3,
            3,
            255,
            "Maximum Fortune enchantment level that GT / BartWorks ores will respond to when "
                + "enableUnlimitedOreFortune is true. "
                + "IMPORTANT: This option is implemented via Mixin and is applied once at JVM startup. "
                + "It CANNOT be changed via /EZMiner reloadConfig — a full game restart is required. "
                + "Default: 3 (no cap extension).");
        enableFortuneForPlacedOre = serverConfiguration.getBoolean(
            "enableFortuneForPlacedOre",
            Configuration.CATEGORY_GENERAL,
            false,
            "When true, ores placed by players (not naturally generated) are treated as natural ores "
                + "and also benefit from the Fortune bonus when enableUnlimitedOreFortune is true. "
                + "IMPORTANT: This option is implemented via Mixin and is applied once at JVM startup. "
                + "It CANNOT be changed via /EZMiner reloadConfig — a full game restart is required. "
                + "Default: false.");
    }

    public static void applyServerRuntimeLimits(int maxBigRadius, int maxBlockLimit, int maxSmallRadius,
        int maxTunnelWidth, int maxPreviewBigRadius, int maxPreviewBlockLimit, boolean allowPreview,
        int syncedBreakPerTick, int maxBlockSwapRadius, int maxBlockSwapLimit, boolean syncedEnableBlockSwapMode) {
        runtimeServerMaxBigRadius = Math.max(0, maxBigRadius);
        runtimeServerMaxBlockLimit = Math.max(0, maxBlockLimit);
        runtimeServerMaxSmallRadius = Math.max(0, maxSmallRadius);
        runtimeServerMaxTunnelWidth = Math.max(0, maxTunnelWidth);
        runtimeServerMaxBlockSwapRadius = Math.max(0, maxBlockSwapRadius);
        runtimeServerMaxBlockSwapLimit = Math.max(0, maxBlockSwapLimit);
        runtimeServerMaxPreviewBigRadius = Math.max(0, maxPreviewBigRadius);
        runtimeServerMaxPreviewBlockLimit = Math.max(0, maxPreviewBlockLimit);
        runtimeServerUsePreview = allowPreview;
        breakPerTick = Math.max(1, syncedBreakPerTick);
        // Keep server fields mirrored on client memory for compatibility in existing reads.
        serverMaxPreviewBigRadius = Math.max(0, maxPreviewBigRadius);
        serverMaxPreviewBlockLimit = Math.max(0, maxPreviewBlockLimit);
        serverUsePreview = allowPreview;
        enableBlockSwapMode = syncedEnableBlockSwapMode;
        clampClientMiningToServerCaps();
        clampClientPreviewToServerCaps();
    }

    /**
     * Applies server-synced performance settings on the client so the OP
     * server-settings GUI shows and saves the server's actual values (same
     * pattern as {@code enableBlockSwapMode}/{@code breakPerTick} in
     * {@link #applyServerRuntimeLimits}).
     */
    public static void applyServerRuntimePerformance(int syncedSearchBudgetPerYield, boolean syncedUseDualFrontierBfs,
        boolean syncedUsePrimitiveVisitedSet) {
        searchBudgetPerYield = Math.max(0, Math.min(4096, syncedSearchBudgetPerYield));
        useDualFrontierBfs = syncedUseDualFrontierBfs;
        usePrimitiveVisitedSet = syncedUsePrimitiveVisitedSet;
    }

    /**
     * Applies server-synced general config values on the client (P1-1 fix).
     * Mirrors the server's actual runtime values into the client's {@code Config}
     * static fields so the OP GUI displays correct values instead of the
     * client's local config file defaults.
     */
    public static void applyServerRuntimeConfig(int syncedCachedBreakPerTick, boolean syncedDropImmediately,
        double syncedAddExhaustion, boolean syncedDropToPlayer, double syncedMinesweeperCooldown,
        double syncedSudokuCooldown, boolean syncedEnableCachedChain, int syncedSearchWorkerThreads,
        boolean syncedSuppressHodgepodgeWarnings, boolean syncedEnableChainChunkLoading,
        boolean syncedUseChunkCachedHarvest, boolean syncedCrazyMode, int syncedChainIdleTimeoutSeconds,
        int syncedChainIdleCountdownSeconds, boolean syncedStopOnUnbreakable, int syncedChainCooldownTicks,
        int syncedXpDropMode, boolean syncedMergeXPOrbs, boolean syncedFireBreakEvent) {
        cachedBreakPerTick = Math.max(1, Math.min(1024, syncedCachedBreakPerTick));
        dropImmediately = syncedDropImmediately;
        addExhaustion = syncedAddExhaustion;
        dropToPlayer = syncedDropToPlayer;
        minesweeperProbeCooldownSeconds = Math.max(0.1, syncedMinesweeperCooldown);
        sudokuProbeCooldownSeconds = Math.max(0.1, syncedSudokuCooldown);
        enableCachedChain = syncedEnableCachedChain;
        searchWorkerThreads = Math.max(0, Math.min(8, syncedSearchWorkerThreads));
        suppressHodgepodgeWarnings = syncedSuppressHodgepodgeWarnings;
        enableChainChunkLoading = syncedEnableChainChunkLoading;
        useChunkCachedHarvest = syncedUseChunkCachedHarvest;
        crazyMode = syncedCrazyMode;
        chainIdleTimeoutSeconds = syncedChainIdleTimeoutSeconds <= 0 ? -1 : Math.max(1, syncedChainIdleTimeoutSeconds);
        chainIdleCountdownSeconds = syncedChainIdleCountdownSeconds <= 0 ? -1
            : Math.max(1, syncedChainIdleCountdownSeconds);
        stopOnUnbreakable = syncedStopOnUnbreakable;
        chainCooldownTicks = Math.max(0, syncedChainCooldownTicks);
        xpDropMode = Math.max(0, Math.min(1, syncedXpDropMode));
        mergeXPOrbs = syncedMergeXPOrbs;
        fireBreakEvent = syncedFireBreakEvent;
    }

    /**
     * Updates {@link #chainActivationMode} in memory and writes only that value
     * back to the config file immediately, without touching any other entries.
     *
     * @param mode 0 = hold to activate; 1 = click to toggle
     */
    public static void saveChainActivationMode(int mode) {
        chainActivationMode = mode;
        if (clientConfiguration == null) return;
        clientConfiguration.get(CLIENT_CATEGORY, "chainActivationMode", 0, CHAIN_ACTIVATION_MODE_COMMENT, 0, 1)
            .set(mode);
        clientConfiguration.save();
    }

    public static void saveHudPos(int x, int y) {
        hudPosX = x;
        hudPosY = y;
        if (clientConfiguration == null) return;
        clientConfiguration.get(CLIENT_CATEGORY, "hudPosX", 5)
            .set(x);
        clientConfiguration.get(CLIENT_CATEGORY, "hudPosY", 5)
            .set(y);
        clientConfiguration.save();
    }

    /** Reload only client-side options from disk. */
    public static void reloadClientOnly() {
        if (clientConfiguration == null) return;
        clientConfiguration.load();
        loadClientOnlyInternal();
        clampClientMiningToServerCaps();
        clampClientPreviewToServerCaps();
        if (clientConfiguration.hasChanged()) {
            clientConfiguration.save();
        }
    }

    public static void clearServerRuntimeOverridesAndReloadClient() {
        runtimeServerMaxBigRadius = Integer.MAX_VALUE;
        runtimeServerMaxBlockLimit = Integer.MAX_VALUE;
        runtimeServerMaxSmallRadius = Integer.MAX_VALUE;
        runtimeServerMaxTunnelWidth = Integer.MAX_VALUE;
        runtimeServerMaxPreviewBigRadius = Integer.MAX_VALUE;
        runtimeServerMaxPreviewBlockLimit = Integer.MAX_VALUE;
        runtimeServerUsePreview = true;
        reloadClientOnly();
    }

    public static MinerConfig buildClientMinerConfigForSync() {
        MinerConfig cfg = new MinerConfig();
        cfg.bigRadius = Math.max(0, Math.min(clientBigRadius, runtimeServerMaxBigRadius));
        cfg.blockLimit = Math.max(0, Math.min(clientBlockLimit, runtimeServerMaxBlockLimit));
        cfg.smallRadius = Math.max(0, Math.min(clientSmallRadius, runtimeServerMaxSmallRadius));
        cfg.tunnelWidth = Math.max(0, Math.min(clientTunnelWidth, runtimeServerMaxTunnelWidth));
        cfg.useChainDoneMessage = useChainDoneMessage;
        return cfg;
    }

    public static boolean isPreviewEnabled() {
        return usePreview && runtimeServerUsePreview;
    }

    public static void clampClientMiningToServerCaps() {
        clientBigRadius = Math.max(0, Math.min(clientBigRadius, runtimeServerMaxBigRadius));
        clientBlockLimit = Math.max(0, Math.min(clientBlockLimit, runtimeServerMaxBlockLimit));
        clientSmallRadius = Math.max(0, Math.min(clientSmallRadius, runtimeServerMaxSmallRadius));
        clientTunnelWidth = Math.max(0, Math.min(clientTunnelWidth, runtimeServerMaxTunnelWidth));
        clientBlockSwapRadius = Math.max(0, Math.min(clientBlockSwapRadius, runtimeServerMaxBlockSwapRadius));
        clientBlockSwapLimit = Math.max(0, Math.min(clientBlockSwapLimit, runtimeServerMaxBlockSwapLimit));
    }

    public static void clampClientPreviewToServerCaps() {
        previewBigRadius = Math.max(0, Math.min(previewBigRadius, runtimeServerMaxPreviewBigRadius));
        previewBlockLimit = Math.max(0, Math.min(previewBlockLimit, runtimeServerMaxPreviewBlockLimit));
    }

    private static void loadClientOnlyInternal() {
        if (clientConfiguration == null) return;
        usePreview = clientConfiguration.getBoolean(
            "usePreview",
            CLIENT_CATEGORY,
            true,
            "When true, block outlines are rendered around all blocks that would be included in the "
                + "current chain while the chain key is held.");
        useChainDoneMessage = clientConfiguration.getBoolean(
            "useChainDoneMessage",
            CLIENT_CATEGORY,
            true,
            "When true, a summary message is shown in chat after each chain operation finishes, "
                + "reporting the number of blocks mined and the time taken.");
        clientBigRadius = clientConfiguration.getInt(
            "clientBigRadius",
            CLIENT_CATEGORY,
            8,
            0,
            Integer.MAX_VALUE,
            "Client preferred chain radius. Effective value is clamped by server max.");
        clientBlockLimit = clientConfiguration.getInt(
            "clientBlockLimit",
            CLIENT_CATEGORY,
            1024,
            0,
            Integer.MAX_VALUE,
            "Client preferred chain block limit. Effective value is clamped by server max.");
        clientSmallRadius = clientConfiguration.getInt(
            "clientSmallRadius",
            CLIENT_CATEGORY,
            2,
            0,
            Integer.MAX_VALUE,
            "Client preferred chain adjacency radius. Effective value is clamped by server max.");
        clientTunnelWidth = clientConfiguration.getInt(
            "clientTunnelWidth",
            CLIENT_CATEGORY,
            1,
            0,
            Integer.MAX_VALUE,
            "Client preferred tunnel width. Effective value is clamped by server max.");
        previewBigRadius = clientConfiguration.getInt(
            "previewBigRadius",
            CLIENT_CATEGORY,
            8,
            0,
            Integer.MAX_VALUE,
            "Maximum search radius for the client-side block-outline preview. "
                + "Effective value is clamped by server max preview radius.");
        previewBlockLimit = clientConfiguration.getInt(
            "previewBlockLimit",
            CLIENT_CATEGORY,
            256,
            0,
            Integer.MAX_VALUE,
            "Maximum number of blocks shown in the client-side block-outline preview. "
                + "Effective value is clamped by server max preview block limit.");
        clientBlockSwapRadius = clientConfiguration.getInt(
            "clientBlockSwapRadius",
            CLIENT_CATEGORY,
            8,
            0,
            Integer.MAX_VALUE,
            "Client preferred block swap radius. Effective value is clamped by server max.");
        clientBlockSwapLimit = clientConfiguration.getInt(
            "clientBlockSwapLimit",
            CLIENT_CATEGORY,
            1024,
            0,
            Integer.MAX_VALUE,
            "Client preferred block swap limit. Effective value is clamped by server max.");
        hudPosX = clientConfiguration.getInt(
            "hudPosX",
            CLIENT_CATEGORY,
            5,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            "HUD X position in screen pixels (origin at top-left).");
        hudPosY = clientConfiguration.getInt(
            "hudPosY",
            CLIENT_CATEGORY,
            5,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            "HUD Y position in screen pixels (origin at top-left).");
        chainActivationMode = clientConfiguration
            .getInt("chainActivationMode", CLIENT_CATEGORY, 0, 0, 1, CHAIN_ACTIVATION_MODE_COMMENT);
        suppressIngameInfoHud = clientConfiguration
            .getBoolean("suppressIngameInfoHud", CLIENT_CATEGORY, false, SUPPRESS_INGAMEINFO_HUD_COMMENT);
        hudAnimationStyle = clientConfiguration
            .getInt("hudAnimationStyle", CLIENT_CATEGORY, 0, 0, 1, HUD_ANIMATION_STYLE_COMMENT);
        renderStyle = clientConfiguration.getInt("renderStyle", CLIENT_CATEGORY, 0, 0, 1, RENDER_STYLE_COMMENT);
        blockScrollOnChainKey = clientConfiguration
            .getBoolean("blockScrollOnChainKey", CLIENT_CATEGORY, true, BLOCK_SCROLL_ON_CHAIN_KEY_COMMENT);
        smartToolSwitchEnabled = clientConfiguration
            .getBoolean("smartToolSwitchEnabled", CLIENT_CATEGORY, true, SMART_TOOL_SWITCH_ENABLED_COMMENT);
        smartToolSwitchActivationMode = clientConfiguration.getInt(
            "smartToolSwitchActivationMode",
            CLIENT_CATEGORY,
            1,
            0,
            1,
            SMART_TOOL_SWITCH_ACTIVATION_MODE_COMMENT);
        smartToolSwitchDurabilityScore = clientConfiguration.getBoolean(
            "smartToolSwitchDurabilityScore",
            CLIENT_CATEGORY,
            true,
            "When true (default), tools with more remaining durability are preferred.");
        smartToolSwitchFullInventory = clientConfiguration.getBoolean(
            "smartToolSwitchFullInventory",
            CLIENT_CATEGORY,
            false,
            "When true, the smart tool switch scans all 36 inventory slots instead of only the hotbar.");
        preferredTools = clientConfiguration.getString(
            "preferredTools",
            CLIENT_CATEGORY,
            "",
            "Comma-separated tool registry names in preferred order (e.g. minecraft:diamond_pickaxe).");
    }

    public static void setLegacyServerPreviewCaps(int maxBigRadius, int maxBlockLimit) {
        runtimeServerMaxPreviewBigRadius = Math.max(0, maxBigRadius);
        runtimeServerMaxPreviewBlockLimit = Math.max(0, maxBlockLimit);
        clampClientPreviewToServerCaps();
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent event) {
        if (!event.modID.equalsIgnoreCase(EZMiner.MODID)) return;
        EZMiner.LOG.info("Config change event triggered, reloading...");
        reloadClientOnly();
    }

    public static void register() {
        Config instance = new Config();
        MinecraftForge.EVENT_BUS.register(instance);
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
    }

    /** Writes all current server-side config values to the server config file. */
    public static void saveServerConfig() {
        if (serverConfiguration == null) {
            EZMiner.LOG.warn("saveServerConfig skipped: serverConfiguration is null");
            return;
        }
        // Use get() overloads with comment so Forge writes # comment lines above each property
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "bigRadius",
                8,
                "Maximum radius (in blocks) for chain and blast operations.",
                0,
                Integer.MAX_VALUE)
            .set(bigRadius);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "blockLimit",
                1024,
                "Maximum number of blocks harvested in a single chain operation.",
                0,
                Integer.MAX_VALUE)
            .set(blockLimit);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "smallRadius",
                2,
                "Adjacency detection radius for chain mode.",
                0,
                Integer.MAX_VALUE)
            .set(smallRadius);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "tunnelWidth",
                1,
                "Half-width of the tunnel in Tunnel blast mode (0 = 1-wide, 1 = 3-wide).",
                0,
                Integer.MAX_VALUE)
            .set(tunnelWidth);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "breakPerTick",
                16,
                "Maximum blocks broken per server tick during chain mining.",
                1,
                512)
            .set(breakPerTick);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "cachedBreakPerTick",
                64,
                "Maximum blocks broken per server tick during cached chain mining.",
                1,
                1024)
            .set(cachedBreakPerTick);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "crazyMode",
                false,
                "Remove the per-tick block limit. May cause lag on very large veins.")
            .set(crazyMode);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "dropImmediately",
                false,
                "Spawn drops per-block immediately instead of batching at chain end.")
            .set(dropImmediately);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "xpDropMode",
                1,
                "XP drop mode: 0 = Immediate (per-block), 1 = Delayed (at chain end).",
                0,
                1)
            .set(xpDropMode);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "mergeXPOrbs",
                true,
                "Merge accumulated XP into a single large orb when the chain ends.")
            .set(mergeXPOrbs);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableCachedChain",
                false,
                "Enable cached chain sub-modes (experimental, may have bugs with some ores).")
            .set(enableCachedChain);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableBlockSwapMode",
                false,
                "Enable the Block Swap special sub-mode.")
            .set(enableBlockSwapMode);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "blockSwapRadius",
                8,
                "Maximum search radius for block swap mode.",
                0,
                Integer.MAX_VALUE)
            .set(blockSwapRadius);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "blockSwapLimit",
                1024,
                "Maximum blocks that can be swapped in a single operation.",
                0,
                Integer.MAX_VALUE)
            .set(blockSwapLimit);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "searchWorkerThreads",
                3,
                "Number of background worker threads for parallel block search (0 = disabled).",
                0,
                8)
            .set(searchWorkerThreads);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "searchBudgetPerYield",
                0,
                "Search check interval: 0 = every position (safest), higher = faster but coarser pausing.",
                0,
                4096)
            .set(searchBudgetPerYield);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "useDualFrontierBfs",
                false,
                "Use O(1) queue-based BFS frontier instead of distance-sorted queue.")
            .set(useDualFrontierBfs);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "usePrimitiveVisitedSet",
                true,
                "Use primitive long hash set for visited tracking (reduces memory allocations).")
            .set(usePrimitiveVisitedSet);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableConfigValidation",
                true,
                "Validate config values after load and log warnings for issues.")
            .set(enableConfigValidation);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableSafeReflection",
                true,
                "Use safe reflection wrappers with error guards.")
            .set(enableSafeReflection);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableMixinCapabilityGates",
                true,
                "Only apply mixins when target class matches expectations (requires restart).")
            .set(enableMixinCapabilityGates);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableMainThreadGuard",
                true,
                "Verify network packet handlers run on the main thread.")
            .set(enableMainThreadGuard);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableChainWatchdog",
                true,
                "Tick-based watchdog to force-cancel stuck chain operations.")
            .set(enableChainWatchdog);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "chainWatchdogTimeoutTicks",
                100,
                "Ticks without progress before watchdog fires (100 ticks = 5 seconds).",
                20,
                1200)
            .set(chainWatchdogTimeoutTicks);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableDropFallbackChain",
                true,
                "Try fallback positions for drop spawning if player chunk is unloaded.")
            .set(enableDropFallbackChain);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableBudgetDeadline",
                false,
                "[EXPERIMENTAL] Search threads yield at tick boundaries via deadline timestamp.")
            .set(enableBudgetDeadline);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableToolBreakHandoff",
                true,
                "Signal client to switch tools when the current tool is about to break.")
            .set(enableToolBreakHandoff);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "toolBreakHandoffTimeoutTicks",
                5,
                "Max ticks waiting for client tool switch (5 ticks = 0.25s).",
                1,
                40)
            .set(toolBreakHandoffTimeoutTicks);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "suppressHodgepodgeWarnings",
                true,
                "Suppress off-thread-read warnings for EZMiner background threads.")
            .set(suppressHodgepodgeWarnings);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "useChunkCachedHarvest",
                false,
                "[EXPERIMENTAL] Faster block harvest via direct sub-chunk writes. Use with caution.")
            .set(useChunkCachedHarvest);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableChainChunkLoading",
                false,
                "Load unloaded chunks during chain mining instead of stopping at boundaries.")
            .set(enableChainChunkLoading);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "chainIdleTimeoutSeconds",
                50,
                "Seconds without progress before idle countdown begins (-1 = disabled).",
                -1,
                Integer.MAX_VALUE)
            .set(chainIdleTimeoutSeconds);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "chainIdleCountdownSeconds",
                10,
                "Countdown seconds before auto-cancelling an idle chain (-1 = disabled).",
                -1,
                Integer.MAX_VALUE)
            .set(chainIdleCountdownSeconds);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "addExhaustion",
                0.025,
                "Food exhaustion per block mined during a chain (negative values restore food).",
                -Double.MAX_VALUE,
                Double.MAX_VALUE)
            .set(addExhaustion);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "dropToPlayer",
                true,
                "Spawn batched drops at player feet (true) or at the first mined block (false).")
            .set(dropToPlayer);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "serverUsePreview",
                true,
                "Server global preview switch (false disables preview for all clients).")
            .set(serverUsePreview);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "serverMaxPreviewBigRadius",
                8,
                "Server-side maximum allowed preview radius.",
                0,
                Integer.MAX_VALUE)
            .set(serverMaxPreviewBigRadius);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "serverMaxPreviewBlockLimit",
                1024,
                "Server-side maximum allowed preview block count.",
                0,
                Integer.MAX_VALUE)
            .set(serverMaxPreviewBlockLimit);
        serverConfiguration
            .get(
                "minesweeperProbeCooldownSeconds",
                Configuration.CATEGORY_GENERAL,
                5.0,
                "Cooldown in seconds between minesweeper auto-flag operations.",
                0.1,
                3600.0)
            .set(minesweeperProbeCooldownSeconds);
        serverConfiguration
            .get(
                "sudokuProbeCooldownSeconds",
                Configuration.CATEGORY_GENERAL,
                5.0,
                "Cooldown in seconds between sudoku auto-fill operations.",
                0.1,
                3600.0)
            .set(sudokuProbeCooldownSeconds);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "stopOnUnbreakable",
                false,
                "Stop the chain immediately when encountering an unharvestable block.")
            .set(stopOnUnbreakable);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "fireBreakEvent",
                false,
                "Fire the Forge BreakEvent for each chained block (enables protection mod compatibility).")
            .set(fireBreakEvent);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "chainCooldownTicks",
                0,
                "Cooldown in ticks between chain uses (0 = no cooldown, 20 = 1 second).",
                0,
                Integer.MAX_VALUE)
            .set(chainCooldownTicks);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "chainWatchdogTimeoutTicks",
                100,
                "Ticks without progress before watchdog fires (100 ticks = 5 seconds).",
                20,
                1200)
            .set(chainWatchdogTimeoutTicks);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "enableToolBreakHandoff",
                true,
                "Signal client to switch tools when the current tool is about to break.")
            .set(enableToolBreakHandoff);
        serverConfiguration
            .get(
                Configuration.CATEGORY_GENERAL,
                "toolBreakHandoffTimeoutTicks",
                5,
                "Max ticks waiting for client tool switch (5 ticks = 0.25s).",
                1,
                40)
            .set(toolBreakHandoffTimeoutTicks);
        serverConfiguration.save();
    }

    /** Writes all current client-side config values to the client config file. */
    public static void saveClientConfig() {
        if (clientConfiguration == null) return;
        // Use get() overloads with comment so Forge writes # comment lines above each property
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "usePreview",
                true,
                "Show block-outline preview around chain-targeted blocks while the chain key is held.")
            .set(usePreview);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "useChainDoneMessage",
                true,
                "Show a chat summary after each chain operation finishes (blocks mined and time taken).")
            .set(useChainDoneMessage);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "clientBigRadius",
                8,
                "Preferred chain mining radius (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(clientBigRadius);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "clientBlockLimit",
                1024,
                "Preferred chain mining block limit (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(clientBlockLimit);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "clientSmallRadius",
                2,
                "Preferred adjacency detection radius for chain mode (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(clientSmallRadius);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "clientTunnelWidth",
                1,
                "Preferred tunnel half-width for Tunnel blast mode (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(clientTunnelWidth);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "previewBigRadius",
                8,
                "Max search radius for the client-side block-outline preview (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(previewBigRadius);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "previewBlockLimit",
                256,
                "Max number of blocks rendered in the client-side outline preview (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(previewBlockLimit);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "clientBlockSwapRadius",
                8,
                "Preferred block swap search radius (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(clientBlockSwapRadius);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "clientBlockSwapLimit",
                1024,
                "Preferred max blocks per block-swap operation (capped by server max).",
                0,
                Integer.MAX_VALUE)
            .set(clientBlockSwapLimit);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "chainActivationMode",
                0,
                "How the chain key activates mining: 0 = Hold to keep active, 1 = Toggle on/off.",
                0,
                1)
            .set(chainActivationMode);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "suppressIngameInfoHud",
                false,
                "Hide InGame Info XML HUD while EZMiner's own HUD is visible to prevent overlap.")
            .set(suppressIngameInfoHud);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "hudAnimationStyle",
                0,
                "HUD brand animation style: 0 = Rainbow Bounce, 1 = Wave Highlight.",
                0,
                1)
            .set(hudAnimationStyle);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "renderStyle",
                0,
                "Preview outline render style: 0 = Native wireframe, 1 = Modern two-pass.",
                0,
                1)
            .set(renderStyle);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "blockScrollOnChainKey",
                true,
                "Block hotbar scrolling while the chain key is held (reserved for sub-mode switching).")
            .set(blockScrollOnChainKey);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "smartToolSwitchEnabled",
                true,
                "Master switch for Smart Tool Switching. When false the feature is completely disabled.")
            .set(smartToolSwitchEnabled);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "smartToolSwitchActivationMode",
                1,
                "Smart Tool Switch key mode: 0 = Hold, 1 = Toggle.",
                0,
                1)
            .set(smartToolSwitchActivationMode);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "smartToolSwitchDurabilityScore",
                true,
                "Prefer tools with more remaining durability when multiple tools have equal harvest level.")
            .set(smartToolSwitchDurabilityScore);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "smartToolSwitchFullInventory",
                false,
                "Scan all 36 inventory slots instead of only the 9-slot hotbar for tool switching.")
            .set(smartToolSwitchFullInventory);
        clientConfiguration
            .get(
                CLIENT_CATEGORY,
                "preferredTools",
                "",
                "Comma-separated tool registry names in preferred order (e.g. minecraft:diamond_pickaxe).")
            .set(preferredTools);
        clientConfiguration.save();
    }
}
