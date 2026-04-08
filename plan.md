# EZMiner 全模块低耦合重构实施计划（Refactor Plan）

> 目标：拆分高耦合模块，去除对旧 `Manager` / `Founder` / `Operator` 的核心依赖，建立统一状态源、分层决策执行、可并行分片搜索、主线程世界写入、完整生命周期清理能力。

## 0. 需求确认与边界

- [x] 明确重构范围：决策层、执行层、渲染层、状态层、模式层、网络同步层
- [x] 明确核心原则：状态统一 / 规划执行分离 / 预览执行分离 / 并行仅计算 / 模式只装配
- [ ] 明确兼容策略：旧 API 保留窗口、迁移开关、回滚路径
- [ ] 明确阶段验收标准：每阶段可编译、可运行、行为不回退
- [x] 新增要求：允许启用 Mixin 与 `@LateMixin` 晚加载能力，并纳入重构执行清单

## 1. 当前架构问题清单（已识别）

- [x] `Manager` 同时承担输入态、执行态、掉落聚合、生命周期控制，职责过重
- [x] `MinerModeState` 既是状态又是工厂（直接创建 Founder），模式层与规划层耦合
- [x] `BaseOperator` 同时承担执行调度、饥饿语义、VP 集成、消息发送，执行层边界不清晰
- [x] `MinerRenderer` 直接复用 Founder 搜索逻辑，预览生命周期与执行规则/线程模型耦合
- [x] 客户端与服务端各自维护 mode/key/runtime 片段状态，存在漂移风险
- [x] 网络包语义偏“字段同步”，缺少“会话/状态机”语义
- [x] 生命周期清理只覆盖登出，缺少切维度/重生/单人退出等统一清理路径

## 2. 目标目录与模块边界

- [x] 新建 `chain/state/`：只定义状态与状态服务
- [x] 新建 `chain/mode/`：只定义模式与注册装配
- [x] 新建 `chain/planning/`：只定义搜索与筛选
- [x] 新建 `chain/execution/`：只定义主线程执行与掉落聚合
- [x] 新建 `chain/client/preview/`：只定义客户端预览控制与状态
- [x] 新建 `chain/network/`：只定义输入命令与状态同步协议
- [x] 新建 `chain/lifecycle/`：只定义玩家会话生命周期事件处理

## 3. 状态层（单一权威状态源）

### 3.1 服务端权威状态对象

- [x] `ChainPlayerState`：玩家长期状态（配置快照、模式选择、输入态）
- [x] `ChainRuntimeState`：运行态（是否执行中、队列深度、计数、耗时、错误码）
- [x] `ChainSession`：一次连锁会话上下文（origin、seed、dimension、启动 tick、sessionId）
- [x] `ChainRequest`：输入请求（按键变化、模式切换、触发方块）
- [x] `ChainStateService`：按 UUID 管理状态读写、原子转移、生命周期清理

### 3.2 客户端状态对象

- [x] `ChainClientState`：仅维护输入态+显示态（禁止维护权威执行态）
- [x] 建立服务端→客户端状态镜像协议（只读投影，禁止客户端反写权威态）
- [x] 消除客户端直接推断“执行中”的逻辑，全部由 `PacketChainStateSync` 提供

## 4. 模式层（只做装配，不做业务）

- [x] `ChainMode`：主模式标识（枚举/键）
- [x] `ChainModeDefinition`：主模式定义（展示名、子模式集合、策略绑定）
- [x] `ChainModeRegistry`：主模式注册
- [x] `ChainModeBootstrap`：初始化装配入口
- [x] `ChainSubModeDefinition`：子模式定义（matcher/filter/traverser 组合）
- [x] `ChainSubModeRegistry`：子模式注册
- [x] `ChainSubModeBootstrap`：子模式启动装配
- [x] 从旧 `MinerModeState#createPositionFounder` 迁移为“策略对象组合”，移除直接 new Founder（已改为委托 planning runtime factory）

## 5. 规划层（搜索与筛选，禁止写世界）

- [x] `ChainPlanner`：规划入口，接收 session + mode definition，输出候选流/分片计划
- [x] `ChainPlanningStrategy`：策略接口（链式、爆破、隧道、矿石、伐木、作物）
- [x] `ChainTraverser`：遍历器（BFS/PQBFS/方向隧道等）
- [x] `ChainBlockMatcher`：方块匹配器（同类/矿石/木头/作物）
- [x] `ChainCandidateFilter`：过滤器（边界、脚下保护、可挖掘性、区块加载性）
- [x] `ChainPlanningRuntimeFactory`：按模式组装 planner runtime
- [ ] 规划输出统一为“只读候选动作描述”，不携带世界修改副作用

## 6. 执行层（主线程执行）

- [x] `ChainExecutor`：会话级执行调度（tick 预算、节流、结束条件）
- [x] `ChainActionExecutor`：动作执行接口
- [x] `BlockHarvestActionExecutor`：真实挖掘执行（仅主线程）
- [x] `ChainDropCollector`：掉落聚合与投放策略（玩家脚下/起点）
- [ ] 将饥饿替换语义、VP 集成、异常上报从旧 `BaseOperator` 拆分为独立策略/拦截器
- [ ] 保证：搜索线程只产生候选，世界写入/掉落实体生成全部在服务端主线程

## 7. 客户端预览层（与执行生命周期隔离）

- [x] `ChainPreviewState`（由 `ChainPreviewController` 内部维护）
- [x] `ChainPreviewController`：目标变化、冻结/解冻、队列消费、渲染缓存更新
- [ ] 复用“模式规则定义”，但不复用执行会话与执行控制器
- [ ] 预览搜索任务独立调度，不读写服务端权威执行态
- [x] HUD 数据来源统一：显示态来自 `ChainClientState` + `PacketChainStateSync`

## 8. 网络同步层（输入命令 + 权威状态同步）

- [x] `PacketKeyState`：客户端输入态变化（按下/松开/toggle）
- [x] `PacketChainModeSwitch`：模式切换命令
- [x] `PacketChainStateSync`：服务端权威运行态同步（count/elapsed/session/mode snapshot）
- [ ] 协议改造为“命令 + 权威回传”，避免双向盲写同一字段
- [ ] 补充非法状态保护（过期 sessionId、跨维度旧包、重复包去重）

## 9. 生命周期与清理（稳定性关键）

- [x] 统一接入：登录、登出、切维度、重生、单人退出、世界卸载
- [x] 所有路径都必须中止规划任务、清空执行队列、重置 session、释放渲染状态（服务端会话路径已统一）
- [x] 防止离线玩家残留任务继续访问 world/player 引用
- [ ] 防止跨维度旧 session 误执行

## 10. 并行 Tick 分片策略（性能与安全）

- [ ] 将规划层任务接入并行 Tick，仅执行纯计算分片
- [ ] 分片预算化（每 tick 最大节点检查数/最大队列推进数）
- [ ] 明确线程可见性与中断协议（pause/resume/interrupt 一致）
- [ ] 大半径场景下确保不触发异步区块生成（保持 blockExists 防线）
- [ ] 建立性能基线：TPS 影响、平均每 tick 规划耗时、峰值队列长度

## 10.1 Mixin / @LateMixin 边界策略（新增）

- [ ] 将 `usesMixins` 调整为可启用方案并补齐基础配置（仅在必要场景开启）
- [ ] 仅在边界事件缺口处使用 Mixin（例如生命周期/输入补钩子），不承载业务决策
- [ ] 对可能冲突的注入点采用 `@LateMixin` 晚加载策略，降低与整合包模组冲突概率
- [ ] 建立 Mixin 注入白名单与回退开关，确保线上故障可快速降级

## 11. 迁移路径（分阶段替换旧核心）

- [x] Phase A：引入新状态层与模式注册层，旧逻辑继续跑（双轨）
- [ ] Phase B：规划层替换 Founder（新 planner 输出仍交给旧执行桥接）
- [ ] Phase C：执行层替换 Operator（移除旧 BaseOperator 主职责）
- [ ] Phase D：预览层替换 MinerRenderer 的 Founder 依赖
- [ ] Phase E：网络包迁移到 KeyState/ModeSwitch/StateSync
- [ ] Phase F：删除旧 Manager/Founder/Operator 业务路径，仅保留兼容适配壳（若需要）

## 12. Bug 排查与修复总清单（随阶段勾选）

### 12.1 已发现高风险点

- [ ] 状态漂移：客户端 chainActive 与服务端 inPressChainKey 不一致
- [ ] 生命周期缺口：切维度/重生后旧任务未彻底清理
- [ ] 线程边界：搜索线程潜在读取失效 world/player 引用
- [ ] 网络乱序：模式切换包与按键包到达顺序导致错模态执行
- [ ] 预览-执行耦合：预览冻结逻辑与执行启动时机竞态

### 12.2 必做验证项

- [ ] 单人：按住/切换模式下连续连锁稳定
- [ ] 多人：并发连锁互不干扰，状态不串线
- [ ] 登出/重生/切维度：无残留线程、无 NPE、无鬼会话
- [ ] 大矿脉压力：TPS 可接受、无主线程阻塞尖峰
- [ ] 掉落策略：堆叠、投放位置、Bandit 共存行为正确

## 13. 交付与文档

- [ ] 更新开发文档：新分层架构图、状态机图、网络时序图
- [ ] 更新 README 的“架构/兼容性/调试”章节
- [ ] 提供迁移说明：旧类映射到新类的对照表
- [ ] PR checklist 全量对齐本计划勾选项

## 14. 当前执行状态（本次）

- [x] 完成代码库架构勘察
- [x] 完成高耦合点识别
- [x] 完成分层重构路线设计
- [x] 已将需求与实施清单写入 `plan.md`
- [x] 进入 Phase A 实施
- [x] 启动 Phase B：规划层替换入口（Founder 构建已迁移至 planning runtime factory 兼容桥）
- [x] 生命周期治理：新增 chain/lifecycle 并统一玩家/世界事件运行态清理
- [x] 客户端显示态收敛：HUD 执行态改为 ChainClientState + PacketChainStateSync 投影
