# Review — 解耦 / 冗余模块 / 公开 chain-toggle API（Task 5）

- 日期:2026-07
- 范围:docs/todo.md P3 #5–#8 + 公开 API 设计
- 证据方式:全部用 read/grep 读取实际文件,给出 file:line

---

## A) 解耦计划(按本次会话可行性排序)

### A1. EZMinerConfigGui 数据驱动重排(todo #5)

**现状(已核实)**:`client/gui/EZMinerConfigGui.java` 共 **2182 行**。同一份「行 → 字段」映射以 **switch/手写语句重复 10+ 处**:

| 关注点 | 位置 |
|---|---|
| `getRowLabelKey()` 客户端/server switch(≈160 个 case) | `EZMinerConfigGui.java:1179-1341` |
| `initClientFields()` / `initServerFields()` 字段创建(硬编码行号) | `:1554-1632` |
| `initGui()` 按钮创建 + `newOptionButton()` 行号 | `:297-650` |
| `updateScrolledPositions()` `getControlY(row)` + `setScrolledButtonY` | `:1442-1539` |
| `mouseClicked()` 每个字段 `mouseClicked` | `:1050-1090` |
| `keyTyped()` 每个字段 `textboxKeyTyped` | `:1094-1139` |
| `drawClientTab()` / `drawServerTab()` draw 调用 + section header Y | `:1663-1786` |
| `actionPerformed()` 每个按钮 toggle case | `:739-985` |
| `updateTabVisibility()` 每个按钮 ID 可见性集合 | `:1838-1902` |
| `applyAndSaveClientConfig()` / `applyAndSaveServerConfig()` 解析 | `:1961-2056` |
| `clientFieldFor()` switch | `:1934-1959` |

另有约 **45 个 `private GuiButton btn*` / `GuiTextField tf*` 字段**(`:156-232`)、45+ 个 `BTN_*` 常量(`:50-91`)、30 个 server 位置参数构造 `PacketSaveServerConfig`(`:1981-2017`)。

**方案**:定义
```java
enum WidgetType { TEXT_INT, TEXT_DOUBLE, TOGGLE, CYCLE }
record ConfigRowDef(int id, int row, int section, String i18nKey, WidgetType type,
                    IntSupplier getter, IntConsumer setter, int min, int displayFallback /*serverValueForDisplay*/)
```
客户端/服务端各一份 `List<ConfigRowDef>`(用 `@FunctionalInterface` 而非 record,因 Jabel 支持 record 也 OK)。所有 `initGui/updateScrolledPositions/mouseClicked/keyTyped/draw*/actionPerformed/updateTabVisibility/apply*Save*` 改为**遍历这份列表**,按 `row/widgetType` 分发。加一行字段 → 追加一个 def,不再改 10 处。

- **移动内容**:把行号、i18n key、getter/setter、default/min、sync-target(可并入 `ClientCapSyncTarget`)集中到 def。
- **风险**:中——GUI 布局回归(多行 label、section gap 全在 `getRowHeight/isSectionBreak/getContentRowHeight`,需保留);`actionPerformed` 里 3 个 2 态 cycle(HUD anim/render style)与 toggle 行为不同,需保留开关。
- **工作量**:大(约 1200→ 行净减,连同 `packet` 构造具名字段化一并做最划算——见 P2 #2)。

### A2. Config God-Object 拆分(todo #6)

**现状(已核实)**:`Config.java` 共 **1881 行**,约 **90 个静态字段**混放:
- Server authoritative: `Config.java:21-312`(`bigRadius`..`enableFortuneForPlacedOre`)
- Server-only performance/stability: `:168-311`
- Client: `:314-424`(含 runtime overrides `:360-370`)
- I/O:`init/load`(`:450-493`)、`loadServerOnlyInternal`(`:495-916`)、`loadClientOnlyInternal`(`:1080-1218`)
- Sync/clamp:`applyServerRuntimeLimits`(`:918-941`)、`applyServerRuntimePerformance`(`:949-954`)、`applyServerRuntimeConfig`(`:962-995`)、`clampClient*`(`:1064-1078`)、`buildClientMinerConfigForSync`(`:1047-1058`)
- Save:`saveServerConfig`(`:1242-1691`)、`saveClientConfig`(`:1694-1880`)

依赖:grep 显示 `Config.` 被 **43+ 文件**引用(整个代码库),故必须**增量**。

**方案**:拆三个类,`Config` 保留为**门面(delegation façade)**:
- `ConfigServer`(server 字段 + `loadServerOnlyInternal` + `saveServerConfig`)
- `ConfigClient`(client 字段 + `loadClientOnlyInternal` + `saveClientConfig` + `saveChainActivationMode/saveHudPos`)
- `ConfigRuntimeBridge`(runtime 覆盖 + `applyServerRuntime*` + `clampClient*` + `buildClientMinerConfigForSync` + `isPreviewEnabled`)
- `Config` 保留静态转发(如 `public static int bigRadius { get{return ConfigServer.bigRadius;} set{...}}`),或把静态字段物理迁移后对旧调用点批量替换。

- **风险**:高(跨 43 文件);**若非全部迁移,优先纯搬字段+方法不动调用点**,把拆类做成「文件重组」而非「API 变更」。
- **工作量**:大;可作为 A1 GUI def 化之后的第二步(两处共享同一字段集合,联合收益)。

### A3. Manager → DropManager + SpecialModeTickManager(todo #7)

**现状(已核实)**:`core/Manager.java` 693 行,职责混杂:事件订阅、连锁启动、掉落收集、config 同步、缓存预计算、特殊模式 tick。

- 掉落:字段 `dropCollector`(`:93`)、`onHarvestDrops`(`:254-270`)、`onEntityJoinWorld`(TiC,BANDIT 分支 `:289-315`)、`flushCollectedDrops`(`:318-334`)、`flushDrops`(`:352-383`)、`flushDropsWithFallback`(`:389-417`)、`getFallbackSpawn`(`:423-436`)、`clearDrops`(`:439-442`)、`XPDropHandler` 使用。→ **`DropManager`**
- 特殊模式:字段 `minesweeperHandler/sudokuHandler/blockSwapHandler/prospectHandler/plantingHandler`(`:94-98`)、`tickSpecialMode`(`:615-641`)、`resendMinesweeperMarks`(`:650-652`)、`resendSudokuFills`(`:654-656`)、`cleanupState` 中 reset(`:471-475`)。→ **`SpecialModeTickManager`**
- `preCalcEngine`+`ChainPreCalcCache`(`:112`, `:463/563/586/610`)与 config 同步(`receiveClientConfig` `:483-493`)留在 `Manager`。

**方案**:`DropManager` 持 `ChainDropCollector`+`XPDropHandler`,`SpecialModeTickManager` 持 5 个 handler。`Manager` 委托。`Event` 订阅回调留在 `Manager`(避免改 Forge 注册),但把逻辑体搬入子管理器。
- **风险**:低——纯搬方法+新增两个组合字段,无行为变化。
- **工作量**:中。

### A4. core↔chain 双向依赖(todo #8)

**现状(已核实,双向编译环)**:
- **core → chain**:`core/Manager.java:26-39`、`core/BaseOperator.java:17-30`、`core/MinerModeState.java:4`、`core/founder/PlantingPositionFounder.java:11`
- **chain → core**:`chain/planning/LegacyFounderPlanningFactory.java:9-23`(**12 个 core.founder 导入**)、`ChainPreCalcEngine.java:22-25`、`ChainPlanningRuntimeFactory.java:9-11`、`LegacyFounderPlanningTask.java:6`、`ChainPlanner/ChainPlanningStrategy/ChainTraverser:9`、`chain/state/ChainPlayerState.java:5-6`、`chain/execution/{ChainDropCollector,BlockSwapModeHandler,CropHarvestActionExecutor,VisualProspectingBridge,ChainExecutionErrorReporter}`、`chain/lifecycle/ChainLifecycleService.java:10`

**方案(本次可行)**:
1. **`LegacyFounderPlanningFactory` 移到 `core.founder`**:它本质是「按 `MinerModeState` 装配 founder」,与 chain 层无关。移动后 `ChainPlanningRuntimeFactory.createFounderForMode/createTaskForMode`(保留在 chain/planning 作为门面)改为调用 `core.founder` 的新类。这删掉 **12 个反向导入**。
2. **`chain/execution/ChainExecutionErrorReporter.java:8` 依赖 `core.Manager`**:将 error 上报改为回调接口(`chain` 定义接口,`core.Manager` 实现)或直接传 `MinerConfig`/UUID。
3. **`chain/lifecycle/ChainLifecycleService.java:10` 依赖 `core.Manager`**:`onPlayerLogout/…/onWorldUnload` 接收 `Map<UUID,Manager>` 是管理器操作,把 `managers.remove/stopRuntime` 逻辑留在 `core.PlayerManager` 内,chain 层只做 `chainStateService`/`CooldownTracker` 清理 → 去掉对 `Manager` 的导入。
4. 其余 `chain` → `core.{MinerConfig,MinerModeState,ItemStackKey,founder.DeterminingIdentical,crop.CropAdapterRegistry}` 是与 `core` 的数据/工厂依赖,方向合理(core 是被依赖的低层),**不动**。

- **风险**:中——LegacyFounderPlanningFactory 被 `MinerRenderer.java:218`(客户端 server? 不,`MinerRenderer` 是 client,在 client 侧用 `createFounderForMode` 做 preview)与 `BaseOperator.java:90`(server)使用,移动包名需同步两处 import;建议保留原类名仅换包,或加 `@Deprecated` 委托。
- **工作量**:中(小改动、大收益——解除编译环并切方向)。

---

## B) 冗余 / 死代码清单(均已 grep 验证无调用方)

> 注:docs/review-summary.md 已列的三个已删类(`ResumableChainTraverser`/`SearchEventBus`/`LongOpenHashSet`)不在此列。

### B1. `chain/mode` 整个子系统 —— 写后从无读取(最大死块)
- `chain/mode/ChainModeRegistry.java:7`(`register/get/all`)
- `chain/mode/ChainSubModeRegistry.java:7`
- `chain/mode/ChainMode.java:3`(enum BLAST/CHAIN/SPECIAL)
- `chain/mode/ChainModeDefinition.java:10`、`chain/mode/ChainSubModeDefinition.java:6`
- `chain/mode/ChainModeBootstrap.java:3`、`chain/mode/ChainSubModeBootstrap.java:3`
- 挂载点:`EZMiner.java:45-46`(两个 `static final` 注册表)、`CommonProxy.preInit:50-51`(`bootstrap` 填充)

**证据**:grep `chainModeRegistry|chainSubModeRegistry` 仅命中定义 + `CommonProxy:50-51` 填充,**零 `get()/all()` 读取方**。真正的模式权威是 `core/MinerModeState.java`(静态数组 `MAIN_MODES/BLAST_MODES/CHAIN_MODES/SPECIAL_MODES` + int 字段),由 `KeyListener.syncModeToServer`(`:176-179`)、`PacketChainModeSwitch.Handler:63-66`、`client/ClientStateContainer.java:14` 读写。整个 `chain/mode` 包是 **write-only 脚手架,应整体删除**(或作为未来注册 API 的雏形保留骨架,但当前无消费方)。

### B2. `chain/planning` 的陈旧 planner 脚手架(接口/门面从未被调用)
- `ChainBlockMatcher.java:7`(todo #7 提到将来「复用 `checkCanAddImpl`」)——当前 grep 无调用方
- `ChainTraverser.java:11`(interface `traverse`)— 无调用方
- `ChainCandidateFilter.java:7`(interface `allow`)— 无调用方
- `ChainPlanner.java:14` + `ChainPlanningStrategy.java:11` + `ChainPlanningRuntimeFactory.create()`(`:20-21`)——`chainPlanningRuntimeFactory.create(` **无调用方**;`.plan` 从未执行。被实际消费的只有 `createFounderForMode`/`createTaskForMode`。

**证据**:grep `ChainPlanner|ChainTraverser|ChainCandidateFilter|ChainBlockMatcher|chainPlanningRuntimeFactory\.create` 仅命中定义与 `new ChainPlanner`(在无调用方的 `create` 内)。

### B3. 单方法死代码
- `core/MinerModeState.previousMainMode()` — `MinerModeState.java:75-78`,无调用方(见 B 末尾说明)。

### B4. 不属于死代码(澄清,避免误删)
- `CachedPositionsPlanningTask` —— 被 `core/BaseOperator.java:100,121,125-127` 使用
- `ChainPlanningRuntimeFactory.createFounderForMode/createTaskForMode` —— 被 `MinerRenderer.java:218`、`BaseOperator.java:90` 使用
- 5 个稳定性 flag(`enableBudgetDeadline`/`enableMainThreadGuard`/`enableMixinCapabilityGates`/`enableSafeReflection`/`suppressHodgepodgeWarnings`/`enableConfigValidation`)——均有真消费方(`Pauseable.java:121/157/241`、`MainThreadEnforcer.java:51`、`MixinCapabilityPlugin.java:49`、`SafeReflection.java:43`、`ConfigValidator.java:29`、`EZMiner.java:78`),**不删**
- `serverUsePreview`/`runtimeServerUsePreview`/`isPreviewEnabled` —— 被 `HudRenderer:79`、`MinerRenderer:121`、`KeyListener:130` 消费
- `ChainPreCalcCache.computeHash/computeTypeHash` —— 被 `ChainPreCalcEngine.java:135,286` 消费
- `MinerModeState.previousMainMode()`(**`core/MinerModeState.java:75-78`**)—— grep 显示 `KeyListener` 只有 `nextMainMode()`(V 键循环,`KeyListener.java:69`)与 `previousSubMode()`(滚轮反向,`KeyListener.java:168`)有调用;`previousMainMode` **无调用方,可删**。
  - 澄清:`previousBlastMode/previousChainMode/previousSpecialMode`(`MinerModeState.java:115/136/182`)被 `previousSubMode`(`:92-94`)传递调用,**不是**死代码。

---

## C) 公开 chain-toggle API 设计

目标:其他 mod 可编程地开始/停止连锁会话、设 main/sub-mode、设 key-hold 激活,且遵循「客户端发 `PacketKeyState` / 服务端经 `Manager` 为权威」的服务端权威规则。

### C.1 运行时架构(已核实接线点)

- **服务端权威状态** = 每个玩家的 `ChainPlayerState`(`chain/state/ChainPlayerState.java:11`),由 `EZMiner.chainStateService.getOrCreate(UUID)` 管理,含 `minerModeState`、`keyPressed`、`runtimeState`。
- **客户端 → 服务端 key 输入**:`PacketKeyState`(`chain/network/PacketKeyState.java:34-69`),`Handler.onMessage` 里 `state.keyPressed = msg.pressed`(server 权威),并处理 block-swap 清除 / minesweeper·sudoku 重发。
- **客户端 → 服务端 mode 输入**:`PacketChainModeSwitch`(`chain/network/PacketChainModeSwitch.java:55-69`),`Handler.onMessage` 把四个 int 写入 `state.minerModeState.*`(server 权威,并 clamp)。
- **服务端实际连锁引擎**:`core/Manager.onBlockBreak`(`core/Manager.java:124-145`)在 `isKeyPressed()` 且 `isInOperate()` 时 `startChain`(`:504-515`)→ `BaseOperator`。
- **客户端镜像**:`ClientProxy.clientState`(`ClientStateContainer.java:14`)持有 `minerModeState` + `chainClientState.keyPressed`。

### C.2 建议 API(`com.czqwq.EZMiner.api.EZMinerAPI`)

```java
public final class EZMinerAPI {
    private EZMinerAPI() {}

    // ---------- 服务端权威(可在 server 侧由任意线程/事件调用)----------

    /** 强制设置某个玩家 chain key 的按下状态。仅 server;等价于收到可信任的 PacketKeyState。 */
    public static void setChainKeyHeld(UUID player, boolean held) {
        Manager mgr = PlayerManager.instance.managers.get(player);
        if (mgr == null) return;                        // 非在线/非本服
        EZMiner.chainStateService.getOrCreate(player).keyPressed = held;
        // 镜像 PacketKeyState.Handler:42-66 的重发/清除逻辑
        if (!held && mgr.isBlockSwapMode()) EZMiner.network.network.sendTo(new PacketBlockSwapClear(), mgr.player);
        if (held && mgr.isSpecialMinesweeperMode()) mgr.resendMinesweeperMarks(mgr.player);
        else if (held && mgr.isSpecialSudokuMode()) mgr.resendSudokuFills(mgr.player);
    }

    /** 当前是否处于连锁激活(key 按下且操作进行中或可触发)。 */
    public static boolean isActive(UUID player) {
        ChainPlayerState s = EZMiner.chainStateService.getOrCreate(player);
        return s.keyPressed || s.runtimeState.inOperate;
    }

    /** 写入 server 权威 mode(与 PacketChainModeSwitch.Handler 相同的 clamp)。 */
    public static void setMainMode(UUID player, int mainMode)          { setMode(player, mainMode, -1, -1, -1); }
    public static void setBlastSubMode(UUID player, int idx)           { setMode(player, -1, idx, -1, -1); }
    public static void setChainSubMode(UUID player, int idx)           { setMode(player, -1, -1, idx, -1); }
    public static void setSpecialSubMode(UUID player, int idx)         { setMode(player, -1, -1, -1, idx); }
    public static void setMode(UUID player, int main, int blast, int chain, int special) { ... }

    // ---------- 客户端入口(仅 @SideOnly(CLIENT),供发送 PacketKeyState)----------

    /** 客户端发起 key 按下/释放 —— 发送 PacketKeyState 到 server(等价 KeyListener.startChain/stopChain 的发送段)。 */
    @SideOnly(Side.CLIENT)
    public static void pressChainKeyClient()  { EZMiner.network.network.sendToServer(new PacketKeyState(true)); }
    @SideOnly(Side.CLIENT)
    public static void releaseChainKeyClient() { EZMiner.network.network.sendToServer(new PacketKeyState(false)); }
}
```

### C.3 接线点(精确)

| 目标 | 接线到现有代码 |
|---|---|
| `setChainKeyHeld`(server) | 复用 `PacketKeyState.Handler.onMessage` 的 body(`PacketKeyState.java:40-67`);`PlayerManager.instance.managers`(`PlayerManager.java:29`)取 `Manager` |
| `isActive` | `ChainStateService.getOrCreate`(`ChainStateService.java:18-20`)读 `keyPressed`/`runtimeState.inOperate`(`ChainPlayerState.java:17`、`ChainRuntimeState`) |
| `setMode`/`setMainMode`/`set*SubMode` | 复用 `PacketChainModeSwitch.Handler` 的 clamp+写入(`PacketChainModeSwitch.java:60-67`),写 `state.minerModeState`(`ChainPlayerState.java:15`) |
| 客户端 `press/releaseChainKeyClient` | 复用 `KeyListener.startChain:118` / `stopChain:137` 的 `sendToServer(new PacketKeyState(...))` 段 |

### C.4 服务端权威规则(明示)

1. **server 侧 API(`setChainKeyHeld`/`setMode`)是权威写入**:直接改 `ChainPlayerState`,与收到可信 `PacketKeyState`/`PacketChainModeSwitch` 等价。
2. **client 侧 API 只发 `PacketKeyState`/`PacketChainModeSwitch`**(`pressChainKeyClient`),由 server `Handler` 落盘 —— 不直接改 server 状态,保持「客户端提议、服务端权威」。
3. `isActive` 是**只读查询**,不触发开启 —— 若要「程序化开始一次连锁」,正确流程是 `setChainKeyHeld(player,true)` + 模拟一次 BreakEvent 起点(MinerEngine 由 `Manager.onBlockBreak` 自然触发;若外部 mod 想指定起点,应 `Manager.startChain(pos, player)` —— `startChain` 为 private,需将其 `public` 化,或经 `setChainKeyHeld` 后再正常挖方块)。
4. 所有写入需 `PlayerManager.instance != null`(服务端已启动),且 `player == EntityPlayerMP`。

> 迁移通道:若把 B1 的 `chain/mode` 注册表保留,可让 `setMainMode/setSubMode` 未来按**稳定字符串 id**(`blast_all` 等)而非裸 int 索引,比 `MinerModeState` 的 int 索引更稳;但当前 int 索引与 `PacketChainModeSwitch` 一致,直接复用即可。
