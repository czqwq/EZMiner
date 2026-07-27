# TODO

## P1 — 性能验证

1. **profiling useDualFrontierBfs/usePrimitiveVisitedSet** — spark 实测大矿脉(blockLimit 4096+,3 worker)对比开/关的搜索耗时+GC。有收益→默认开+README记录;无收益→删flag减组合面。

## P2 — 架构偿债

2. **config 同步去位置参数** — `PacketSaveServerConfig` 仍30参构造,`PacketServerConfig` 12参。统一 no-arg ctor + 具名字段,或键值对序列化,新增项不碰包结构。

3. **blockLimit 上限务实化** — 允许 `Integer.MAX_VALUE`,实际 visited/drop/preview 远低于此。设务实上限(如 1<<20)+config注释说明。

4. **resumable 遍历重构** — 若重做:实现 `ChainBlockMatcher` 接口复用 `checkCanAddImpl`;邻居覆盖完整 `[-r,r]³`;origin 只入队一次;blockLimit→DONE。

## P3 — 解耦高耦合低内聚模块

5. **EZMinerConfigGui 数据驱动重排** (1973行→~1200)
   - 问题:行→字段映射重复在 `getRowLabelKey`/`initGui`/`drawTab`/`updateScrolledPositions`/`mouseClicked`/`keyTyped` 5处,加1字段改10+处,220处裸访问 `Config.`。
   - 方案:定义 `ConfigRowDef{i18nKey,row,section,widgetType,getter,setter,default}`,两份 `List<ConfigRowDef>`(客户端/服务端tab)驱动所有方法迭代。加字段→一行描述符。

6. **拆分 Config.java God Object** (1704行,43依赖文件)
   - 约90静态字段混放:服务端/客户端/runtime覆盖/I/O/sync/clamp。
   - 拆为 `ConfigServer`(服务端字段+load/save)+`ConfigClient`(客户端字段+load/save)+`ConfigRuntimeBridge`(runtime覆盖+sync+clamp+buildMinerConfig)。可增量迁移。

7. **提取 Manager 职责:DropManager + SpecialModeTickManager** (579行→~360)
   - Manager 同时处理:事件订阅、连锁启动、掉落收集(4级回退链)、config同步、缓存预计算、特殊模式tick。
   - `DropManager`: `onHarvestDrops`/`flushDrops`/`flushDropsWithFallback`/`clearDrops`,持有 `ChainDropCollector`+`XPDropHandler`。
   - `SpecialModeTickManager`: `tickSpecialMode`/`resendMinesweeperMarks`/`resendSudokuFills`,持有扫雷/数独 handler。

8. **解耦 core↔chain 双向依赖**
   - `chain.planning` import 12个 `core.founder` 类;`core.Manager`/`BaseOperator` import `chain.execution`/`planning`/`state`。
   - `LegacyFounderPlanningFactory` 移至 `core`;chain 对 `Manager` 的依赖通过接口反转(chain 定义接口,core 实现)。
