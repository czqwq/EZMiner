# EZMiner 后续计划(TODO)

> 来源:2026-07-17 对 commit `d6dbd51`(feat: optional performance optimize in Config)的代码审查及修复。
> 本次已修复的问题见 [review-summary.md](./review-summary.md);以下为**遗留/后续**事项,按优先级排序。

## P1 — 正确性遗留

### 1. 补齐旧服务端字段的 GUI 同步(继承自 d6dbd51 之前的历史问题)
`PacketServerConfig`(S→C)目前只同步:radius/limit 上限、preview 上限、`breakPerTick`、blockSwap 三项、`enableBlockSwapMode`,以及本次新增的 `searchBudgetPerYield` / `useDualFrontierBfs` / `usePrimitiveVisitedSet`。

以下字段**可在 OP GUI 编辑但未同步**,专用服上 GUI 显示的是客户端本地配置文件的值,OP 保存任意设置都会把它们静默重置:

`cachedBreakPerTick`、`dropImmediately`、`addExhaustion`、`dropToPlayer`、`minesweeperProbeCooldownSeconds`、`sudokuProbeCooldownSeconds`、`enableCachedChain`、`searchWorkerThreads`、`suppressHodgepodgeWarnings`、`enableChainChunkLoading`、`useChunkCachedHarvest`、`crazyMode`、`chainIdleTimeoutSeconds`、`chainIdleCountdownSeconds`、`stopOnUnbreakable`、`chainCooldownTicks`、`xpDropMode`、`mergeXPOrbs`、`fireBreakEvent`

**做法**:沿用本次的模式 —— `PacketServerConfig` 加字段 + `buildForPlayer` 赋值 + Handler 调 `Config.applyServerRuntime*()`(参考 `applyServerRuntimePerformance`,见 `Config.java`)。一次性补齐,或干脆把 config-sync 改为键值对/对象序列化(见 #4)。

### 2. MT 路径批内世界读取仍不受 pause 门控
多线程搜索的 worker 在一个 `invokeAll` 批内(最多 8 节点 × ~124 邻居,或一条 shell strip)的世界读取无法被 tick-end `pause()` 中断 —— 与 d6dbd51 之前行为一致,founder 线程现在恢复了每批之间的 `waitUntil()`。若实测出现跨 tick 读取问题,可考虑:缩小批大小、或 worker 批内主动检查 `paused`(只读检查、不 park)提前返回。

## P2 — 评估与精简

### 3. 用实测数据决定两个存留 flag 的去留
`useDualFrontierBfs` 与 `usePrimitiveVisitedSet` 目前没有任何 profiling 数据支撑。用 spark 在典型场景(大 GT 矿脉、blockLimit 4096+、3 worker)对比开/关的搜索耗时与 GC 压力:
- 有收益 → 在 README 记录数据,考虑改为默认开;
- 无收益 → 删除 flag(减少 2×2×ST/MT 的组合测试面)。

注意:`useDualFrontierBfs` 会改变 blockLimit 截断时的选块集合(FIFO 波前 vs 距离有序),对用户可见,文档需说明。

### 4. config 同步协议重构(消除位置参数)
`PacketSaveServerConfig` 仍是 30 个位置参数(本次已停止增长,新字段走具名字段赋值);`PacketServerConfig` 12 个。长期方案:统一改为 no-arg ctor + 具名字段赋值,或键值对(NBT/JSON)序列化,新增配置项不再触碰包结构。

### 5. `blockLimit` 上限务实化
配置允许 `Integer.MAX_VALUE`,实际内存/性能上限远低于此(visited 集、drop 收集、preview 顶点)。建议设一个务实上限(如 1<<20)并在 config 注释说明。

## P3 — 质量与偿债

### 6. 为纯逻辑加最小单测
项目无测试("validation is done by building")。本次 review 发现的壳层漏棱、邻居迭代不对称都是**一发单测就能抓住**的纯几何逻辑。建议给以下内容加 JUnit(不依赖 MC 类):
- `BasePositionFounder.encodePos` 的单射性/边界值;
- 壳层面分解的覆盖完整性(枚举 R=1..3 与朴素三重循环对比);
- 双 frontier BFS 与 PriorityQueue BFS 在小型模拟网格上的选块集合一致性(vein ≤ blockLimit 时)。

### 7. 若重做 resumable 遍历(原 `ResumableChainTraverser` 已删除)
- 实现现存的 `chain/planning/ChainBlockMatcher` 接口(目前 0 实现),复用 `ChainPositionFounder.checkCanAddImpl` 的匹配逻辑(含 sampleTileEntity 的 GT 矿判别与 `skipHarvestCheck`),不要再复制;
- 邻居迭代器需覆盖完整 `[-r, r]³`(旧实现 dx 永不为负、dz=-r 从不被处理);
- origin 只入队一次;达到 blockLimit 时置 DONE;
- 单独 PR,附上 #6 的网格单测。

### 8. GUI 既有代码清理(IDE 已有告警)
`EZMinerConfigGui`:重复代码段(按钮 case 模板)、未使用的 `activationModeValue()`/`hudAnimStyleValue()`/`renderStyleValue()`、未使用变量 `fx`、`initServerFields` 中恒为 288 的形参 `w`。顺手清理即可,不影响功能。

### 9. 文档
- README 增加"性能调优"一节:三个存留开关的含义、默认值、适用场景(等 #3 的数据);
- CLAUDE.md 的架构段落更新:`consumeBudget()` 的 founder-thread-only 契约、visited 集封装(`markVisited`/`isVisited`/`clearVisited` 为唯一入口)。
