# Review 总结:d6dbd51 性能优化提交的审查与修复

- **审查对象**:commit `d6dbd51` "feat: optional performance optimize in Config"(14 文件,+1141/-57)
- **审查方式**:max-effort 多角度审查(逐行扫描 / 删除行为审计 / 跨文件追踪 / 复用与架构评估 / 查漏扫描),每条发现均经过验证
- **修复结果**:本次修复净变化 **+209/-811**,`./gradlew spotlessApply build` 通过(含 checkstyle)
- **日期**:2026-07-17

## 一、审查结论(TL;DR)

d6dbd51 引入 5 个实验性性能开关及配套实现。方向合理(减少装箱、O(R²) 壳扫描、O(1) frontier、协作式让出),但存在 **2 个默认配置下即生效的 bug** 和多个开关组合损坏。根因:**"接受一个方块位置"的入口没有唯一化**(`addResult` / MT worker 直写 / traverser 自立门户三套并存),导致开关彼此不正交。

处置策略:P0/P1 修复,净负收益的两个特性(`SearchEventBus`、`ResumableChainTraverser`)整体删除,手写容器用 fastutil 替换。

## 二、发现与处置对照表

| # | 严重度 | 问题 | 处置 |
|---|--------|------|------|
| 1 | **P0** | `consumeBudget()` 在默认 `searchBudgetPerYield=0` 时是纯 no-op,却替换了链式 BFS 里**全部** `waitUntil()`/`isInterrupted()` —— tick 窗口暂停契约与取消机制双双失效,僵尸线程扫完整个可达空间 | ✅ 已修:budget=0 改为**每次调用**执行暂停/中断检查(恢复旧契约);budget=N 每 N 次一查 |
| 2 | **P0** | 壳层面分解漏掉 4 条平行 x 轴的棱线(R=1 时 26 格只扫 22),爆破模式留"筋" | ✅ 已修:y-面改用完整 z 范围,覆盖完整且无重复 |
| 3 | P1 | `usePrimitiveVisitedSet=true` → `visitedPositions=null` → MT worker lambda NPE 且被未 `.get()` 的 Future 静默吞掉(链只挖 1 格无日志);`NoOpPositionFounder` 构造器在服务端主线程 NPE | ✅ 已修:visited 字段私有化,`markVisited()`/`isVisited()`/`clearVisited()` 为唯一入口,编译器强制不变式 |
| 4 | P1 | 4 个新服务端字段未被 `PacketServerConfig` 同步,GUI 用客户端本地值初始化并发回 —— 专用服上 OP 保存任意设置会静默重置这些字段 | ✅ 已修:`PacketServerConfig` 同步 3 个存留字段,Handler 经 `Config.applyServerRuntimePerformance()` 应用(与 `enableBlockSwapMode` 同模式) |
| 5 | P1 | MT worker 绕过 `addResult` → 绕过事件总线的 generation 门控与 primitive set | ✅ 已修:worker 改走 `markVisited`;总线本身已删除 |
| 6 | P1 | `addResult` 在总线拒绝时不增 `curCount` → 被取消的 founder 连 blockLimit 出口都没有 | ✅ 随总线删除而消失 |
| 7 | P1 | `SearchEventBus` 的 generation 机制是安慰剂:publish 有 TOCTOU 竞态、正常结束路径 `unRegistry()` 不 increment、总线 per-operator 生命周期使跨链泄漏本不可能;还引入多一跳队列 + 最多 1 tick 延迟 | ✅ **整体删除**(类 + 接线 + Config/GUI/packet/lang 表面) |
| 8 | P1 | `encodePos` 在世界边界角 `(-30M, 0, -30M)` 返回 0,踩中手写 `LongOpenHashSet` 的 0 哨兵抛异常;`Pauseable.run()` 无 finally,异常后 `stopped` 永不置位 | ✅ 已修:`run()` 加 try/finally;哨兵问题随 fastutil 替换消失 |
| 9 | P1 | `budgetRemaining` 被共享池 worker 并发递减(数据竞争);founder 暂停会把**共享** `SearchWorkerPool` 的 worker park 住,拖累其他玩家 | ✅ 已修:`consumeBudget()` 限定 founder 线程,worker 调用直接放行(由 `invokeAll` 批边界控制) |
| 10 | P2 | `ResumableChainTraverser`:292 行死代码(从未实例化、flag 无消费者),且含 ≥5 个潜伏 bug(邻居迭代不对称、无去重时不终止、origin 双重入队、`isDone()` 永假、匹配逻辑复制且漂移) | ✅ **整体删除**(类 + `useResumableTraverser` 配置项);重做方案见 docs/todo.md #7 |
| 11 | P2 | 手写 `LongOpenHashSet` 与 GTNH classpath 上已有的 fastutil 同名类重复;单一写锁可能负优化;容量溢出;javadoc 与实现不符 | ✅ 已删,改用 `it.unimi.dsi.fastutil` + `LongSets.synchronize()`,`dependencies.gradle` 加 `compileOnly` |
| 12 | P2 | 单线程 BFS 用 `ConcurrentLinkedQueue` 当 frontier(逐 offer 节点分配吃掉换 PQ 的收益) | ✅ 已改 `ArrayDeque` |
| 13 | P2 | `PacketSaveServerConfig` 膨胀到 34 个位置参数、尾部 3 个相邻 boolean(换位静默编译) | ✅ 构造器回退到 30 参(与已发布版本二进制兼容),新字段在 GUI 调用点用具名字段赋值 |
| 14 | P2 | 旗标蔓延:5 flag × ST/MT = 64 组合,≥3 组合损坏、1 个死 flag | ✅ 缓解:删 2 个 flag,入口唯一化后剩余 flag 正交;存留 flag 的去留见 docs/todo.md #3 |
| 15 | 小项 | GUI 死常量 id=29、lang 文案与新语义不符、budget 消耗顺序等 | ✅ 已清理;lang 文案(中英)按新语义重写 |

## 三、本次修复的文件清单

**删除**
- `chain/planning/ResumableChainTraverser.java`(-292)
- `chain/planning/SearchEventBus.java`(-103)
- `utils/LongOpenHashSet.java`(-155)

**修改**
- `thread/Pauseable.java` — `consumeBudget()` 契约修复(founder-thread-only、budget=0=逐位置检查);`run()` try/finally
- `core/founder/BasePositionFounder.java` — 壳层面分解修复;visited 集私有化 + `markVisited`/`isVisited`/`clearVisited` 封装;fastutil primitive set;移除总线接线
- `core/founder/ChainPositionFounder.java` — 两个 MT worker lambda 改走 `markVisited`;ST dual-frontier 改 `ArrayDeque`
- `core/founder/NoOpPositionFounder.java` — `clearVisited()`
- `core/BaseOperator.java`、`chain/planning/ChainPlanningTask.java`、`chain/planning/LegacyFounderPlanningTask.java` — 移除总线接线
- `Config.java` — 删 `useSearchEventBus`/`useResumableTraverser`;新增 `applyServerRuntimePerformance()`;注释按新语义重写
- `network/PacketServerConfig.java` — 同步 3 个性能字段(`buildForPlayer` 字段赋值,不增长位置参数)
- `network/PacketSaveServerConfig.java` — 构造器回退 30 参;新字段走 public 字段;Handler 移除总线项
- `client/gui/EZMinerConfigGui.java` — 移除总线行与死常量;行数 34→33;保存改 no-arg ctor + 具名字段赋值
- `dependencies.gradle` — `compileOnly('it.unimi.dsi:fastutil:8.5.18')`
- `lang/en_US.lang`、`lang/zh_CN.lang` — 删总线 key,文案按新语义重写

## 四、行为变化说明(面向用户/服主)

- **默认配置**:行为回归 d6dbd51 之前的安全语义(搜索线程逐方块响应 tick 暂停与取消),并**额外修复了爆破模式漏挖 4 条棱**的问题(该 bug 只存在于 d6dbd51 这一个未发布提交中)。
- **配置项变化**:`useSearchEventBus`、`useResumableTraverser` 已移除(旧配置文件中的残留键无害);`searchBudgetPerYield` 语义微调 —— 0 = 每方块检查(默认、最安全),N = 每 N 方块检查一次。
- **`useDualFrontierBfs`**:当矿脉超过 blockLimit 时,选中的方块集合可能与 PriorityQueue 模式不同(波前序 vs 距离序)——这是该模式的固有属性,注释已如实说明。
- **依赖**:新增 fastutil `compileOnly`(GTNH 运行环境自带,无发布体积变化)。

## 五、验证

- ✅ `./gradlew spotlessApply build` 通过(Jabel + Mixin AP + checkstyle)
- ✅ 全库 grep 确认无已删类/flag 的残留引用;`visitedPositions`/`visitedPrimitive` 仅存在于 `BasePositionFounder` 私有封装内
- ⬜ 建议游戏内手测(见 docs/todo.md 引言):默认配置爆破无残棱、链挖中途松键线程即停、`usePrimitiveVisitedSet+MT` 整脉可挖、专用服 GUI 保存不重置

## 六、遗留事项

见 [docs/todo.md](./todo.md) —— 最重要的一条:**旧服务端字段(`fireBreakEvent`、`crazyMode` 等 19 项)存在与 #4 同源的 GUI 重置问题**(d6dbd51 之前就有,本次按范围只修了新增字段)。
