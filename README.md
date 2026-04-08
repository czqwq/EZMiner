# EZMiner

> 🌐 [English README](README_en.md)

一个专为 **GregTech: New Horizons (GTNH)** 设计的高性能连锁采矿模组，适用于 Minecraft 1.7.10。

---

## 功能概览

- 按住连锁键即可启动连锁，松开立即停止；或通过配置改为**点击切换**模式
- 支持**连锁模式**与**爆破模式**两大主模式
- 爆破模式下拥有 5 种子模式，可用鼠标滚轮切换
- 按住连锁键时，左上角 HUD 实时显示当前模式链与已连锁方块数
- 对准方块时，方块边框会被实时渲染高亮（可穿透遮挡），直观展示将被连锁的范围
- 所有配置均可通过配置文件热重载（`/EZMiner reloadConfig`），无需重启游戏
- 完整中英文本地化支持
- 安装 [Visual Prospecting](https://github.com/GTNewHorizons/VisualProspecting) 后，挖矿时自动同步矿脉信息至地图标注

---

## 按键绑定

| 按键 | 默认键 | 说明 |
|------|--------|------|
| 连锁键 | `` ` ``（波浪号/反引号） | 按住启动/松开停止（按住模式）；或点击切换开关（切换模式，见配置）|
| 模式切换键 | `V` | 切换主模式（连锁 ↔ 爆破） |
| 鼠标滚轮 | — | 按住连锁键时，滚轮切换当前主模式的子模式 |

---

## 主模式

### 连锁模式

从玩家击中的方块出发，以**同心圆层**向外扩展，自动搜索并连锁所有**相同类型**的相邻方块。  
搜索以优先队列实现，距离越近的方块越先被挖掘，视觉上呈现由近及远的球形扩散效果。

### 爆破模式

对目标区域内的方块进行批量挖掘，共有 5 种子模式：

| 子模式 | 说明 |
|--------|------|
| 无差别爆破 | 挖掉范围内所有可采掘的方块 |
| 同类筛选 | 仅挖掘与目标方块相同类型的方块 |
| 隧道爆破 | 以玩家朝向为轴线，挖出指定宽度的隧道 |
| 矿石爆破 | 仅挖掘矿石类方块 |
| 爆破伐木 | 仅挖掘木头类方块（适合批量伐树） |

---

## HUD 显示

按住连锁键时，屏幕左上角显示：

```
[EZMiner] ■ 连锁已启用
  ○─ {主模式}
  └─ {子模式}
  └─ 已连锁方块: N        ← 仅在连锁进行中显示
```

---

## 方块轮廓预览

按住连锁键并对准方块时，模组会在客户端实时渲染出所有将被连锁的方块的边框高亮。

- 渲染可穿透遮挡，即便方块被其他方块遮住也能看到轮廓
- 仅渲染玩家视距范围内的方块，超出视距的方块正常连锁但不渲染，避免性能问题
- 对准空气或实体时自动停止渲染
- 可在配置中关闭此功能（`usePreview = false`）

---

## 掉落物处理

连锁过程中产生的所有掉落物会被**统一收集**，待连锁结束后一次性投放，避免大量实体同时刷新造成卡顿。

- `dropToInventory = true`（默认）：掉落物投放在**玩家当前脚下**
- `dropToInventory = false`：掉落物投放在**最初挖掘的那个方块的中心点**

所有物品堆叠均严格遵守 `maxStackSize` 限制，不会出现超量堆叠的情况。

---

## 配置文件

配置文件位于 `config/EZMiner/EZMiner.cfg`，支持 `/EZMiner reloadConfig` 热重载。

### 服务端限制（管理员设置上限）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `bigRadius` | `8` | 最大连锁/爆破半径（格） |
| `blockLimit` | `1024` | 每次连锁最多挖掘的方块数 |
| `smallRadius` | `2` | 连锁模式邻接检测半径（格）——在此半径内的相同方块视为"相邻" |
| `tunnelWidth` | `1` | 隧道爆破的半宽度（格） |

### 客户端/玩家设置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `addExhaustion` | `0.025` | 每挖一个方块消耗的饥饿值（负值可恢复饥饿） |
| `dropToPlayer` | `true` | 掉落物处理方式（`true` = 直接掉落到玩家脚下（默认）；`false` = 掉落在首个被挖掘的方块处） |
| `usePreview` | `true` | 是否显示方块边框预览 |
| `useChainDoneMessage` | `true` | 连锁结束后是否在聊天栏显示统计消息 |
| `chainActivationMode` | `0` | 连锁键激活方式：`0` = **按住**激活（默认），`1` = **点击切换**开关 |

---

## Visual Prospecting 集成

安装 [Visual Prospecting](https://github.com/GTNewHorizons/VisualProspecting) 后，EZMiner 在挖掘 GregTech 矿石方块时会**自动将矿脉信息同步到地图标注**，无需手动右键每个矿石。所有挖矿模式均支持此功能。

---

## 安全特性

- 挖掘前检查工具耐久，耐久不足 1 时自动停止连锁（防止工具损坏）
- 每挖一个方块扣除对应的饥饿值，防止无限免费采矿
- 不会挖掘玩家脚下的方块，防止玩家悬空坠落
- 不会响应假人（FakePlayer）的操作，防止假人利用连锁功能
- 玩家下线时立即终止所有连锁操作，防止服务端报错
- 完整的错误捕获与日志记录，便于问题排查

---

## 热重载指令

```
/EZMiner reloadConfig
```

在不重启游戏的情况下重新读取配置文件，修改立即生效。

---

## 兼容性

- Minecraft **1.7.10**
- Forge **10.13.4.1614**
- GregTech: New Horizons（GTNH）整合包
- 默认 **不启用 Mixin**（`usesMixins = false`），优先使用 Forge/FML 事件与分层模块实现功能

---

## 架构（重构后）

```
输入命令层（客户端）:
  KeyListener -> PacketKeyState / PacketChainModeSwitch

权威状态层（服务端）:
  ChainStateService
    ├─ ChainPlayerState（长期状态）
    ├─ ChainRuntimeState（运行态）
    └─ ChainSession（会话）

规划层（只读世界）:
  chain/planning/*  +  ParallelTick 纯计算任务

执行层（主线程写世界）:
  BaseOperator -> ChainExecutor / BlockHarvestActionExecutor
              -> ChainDropCollector / 饥饿策略 / VP 桥接

同步层:
  PacketChainStateSync（服务端权威回传）

客户端显示层:
  ChainClientState + ChainPreviewController + HudRenderer/MinerRenderer
```

### 运行态状态机（简化）

1. 客户端按键变化发送命令包  
2. 服务端更新权威状态，满足条件后启动会话  
3. 规划线程产出候选方块（不写世界）  
4. 主线程执行收获并同步 `PacketChainStateSync`  
5. 结束时统一掉落投放并清理会话/运行态

### 网络时序（简化）

1. C -> S: `PacketKeyState`（按下/松开）  
2. C -> S: `PacketChainModeSwitch`（模式切换）  
3. S -> C: `PacketChainStateSync`（session/count/elapsed/inOperate）

---

## 调试与排障

- 推荐先执行：`./gradlew spotlessApply build`
- 重点日志入口：
  - 执行错误：`ChainExecutionErrorReporter`
  - 生命周期清理：`chain/lifecycle/ChainLifecycleService`
  - 状态同步：`chain/network/PacketChainStateSync`
- 常见排查：
  - 预览不更新：检查客户端 `ChainPreviewController` 冻结/解冻状态
  - 多人串线：检查 `sessionId` 与 UUID 维度匹配
  - 掉落异常：检查 `Manager.onHarvestDrops` 与 Bandit 共存分支

---

## 迁移对照（旧 -> 新）

| 旧职责/类 | 新职责/类 |
|---|---|
| `Manager` 运行态拼装 | `ChainStateService` + `ChainPlayerState`/`ChainRuntimeState` |
| `MinerModeState#createPositionFounder` | `chain/mode/*` + `ChainPlanningRuntimeFactory` |
| `BaseOperator` 内聚执行逻辑 | `ChainExecutor` + `ChainActionExecutor` + 独立策略组件 |
| 旧预览与执行耦合 | `ChainPreviewController` + `ChainClientState` |
| 字段式状态同步 | 命令包（`PacketKeyState`/`PacketChainModeSwitch`）+ 权威回传（`PacketChainStateSync`） |
