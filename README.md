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

从玩家击中的方块出发，自动搜索并连锁所有**相同类型**的相邻方块。距离越近的方块越先被挖掘，呈现由近及远的球形扩散效果。

### 爆破模式

对目标区域内的方块进行批量挖掘，共有 6 种子模式：

| 子模式 | 说明 |
|--------|------|
| 无差别爆破 | 挖掉范围内所有可采掘的方块 |
| 同类筛选 | 仅挖掘与目标方块相同类型的方块 |
| 隧道爆破 | 以玩家朝向为轴线，挖出指定宽度的隧道 |
| 矿石爆破 | 仅挖掘矿石类方块 |
| 爆破伐木 | 仅挖掘木头类方块（适合批量伐树） |
| 一键收作物 | 右键触发，范围内所有成熟作物（含 IC2 作物）自动收割 |

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

- `dropToPlayer = true`（默认）：掉落物投放在**玩家当前脚下**
- `dropToPlayer = false`：掉落物投放在**最初挖掘的那个方块的中心点**

所有物品堆叠均严格遵守 `maxStackSize` 限制，不会出现超量堆叠的情况。

---

## 配置文件

EZMiner 将配置文件分为两个，分别存放在不同位置：

| 文件 | 路径 | 说明 |
|------|------|------|
| **客户端配置** | `config/EZMiner/EZMiner.cfg` | 玩家本地设置，每个客户端独立 |
| **服务端配置** | `EZMiner/EZMiner_Server.cfg` | 服务器管理员设置（位于游戏根目录下）；单人游戏时在 `.minecraft/EZMiner/`，多人服务端时在 `./EZMiner/` |

大多数选项支持 `/EZMiner reloadConfig` 热重载，无需重启游戏。

### 服务端配置（`EZMiner/EZMiner_Server.cfg`）

由管理员统一设置，客户端的实际生效值不会超过此处的上限。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `bigRadius` | `8` | 最大连锁/爆破半径（格） |
| `blockLimit` | `1024` | 每次连锁最多挖掘的方块数 |
| `smallRadius` | `2` | 连锁模式邻接检测半径（格）——在此半径内的相同方块视为"相邻" |
| `tunnelWidth` | `1` | 隧道爆破的半宽度（格） |
| `breakPerTick` | `16` | 每个服务端 tick 最多破坏的方块数（最高 64）——数值越低对 TPS 影响越小 |
| `addExhaustion` | `0.025` | 每挖一个方块消耗的饥饿值（负值可恢复饥饿） |
| `dropToPlayer` | `true` | 掉落物处理方式（`true` = 直接掉落到玩家脚下（默认）；`false` = 掉落在首个被挖掘的方块处） |
| `serverUsePreview` | `true` | 服务器全局预览开关，`false` 时禁用所有客户端的预览功能 |
| `serverMaxPreviewBigRadius` | `8` | 服务端允许的最大预览半径 |
| `serverMaxPreviewBlockLimit` | `1024` | 服务端允许的最大预览方块数 |
| `minesweeperProbeCooldownSeconds` | `5` | 特殊模式/扫雷自动标记操作的冷却时间（秒） |

### 客户端配置（`config/EZMiner/EZMiner.cfg`）

由玩家自行调整，实际生效值受服务端上限约束。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `usePreview` | `true` | 是否显示方块边框预览 |
| `useChainDoneMessage` | `true` | 连锁结束后是否在聊天栏显示统计消息 |
| `clientBigRadius` | `8` | 客户端期望的连锁半径（受服务端上限约束） |
| `clientBlockLimit` | `1024` | 客户端期望的最大挖掘方块数（受服务端上限约束） |
| `clientSmallRadius` | `2` | 客户端期望的邻接检测半径（受服务端上限约束） |
| `clientTunnelWidth` | `1` | 客户端期望的隧道半宽度（受服务端上限约束） |
| `previewBigRadius` | `8` | 客户端预览的最大搜索半径（受服务端上限约束） |
| `previewBlockLimit` | `256` | 客户端预览最多显示的方块数（受服务端上限约束） |
| `chainActivationMode` | `0` | 连锁键激活方式：`0` = **按住**激活（默认），`1` = **点击切换**开关 |
| `hudPosX` / `hudPosY` | `5` / `5` | HUD 在屏幕上的像素坐标（原点在左上角） |
| `suppressIngameInfoHud` | `false` | 按住连锁键时临时隐藏 InGame Info XML 的 HUD，防止两者重叠（需安装 InGame Info XML）|
| `hudAnimationStyle` | `0` | HUD 标题动画风格：`0` = 彩虹弹跳（默认），`1` = 波浪高亮 |
| `renderStyle` | `0` | 方块边框预览渲染风格：`0` = 原生（单通道线框，默认），`1` = 现代（双通道渲染，可见边实心、遮挡边半透明） |

### 时运破上限（服务端配置，Mixin 功能，默认关闭）

> ⚠️ **以下三项位于服务端配置文件（`EZMiner/EZMiner_Server.cfg`），通过 Mixin 在 JVM 启动时注入，修改后必须重启游戏才能生效，无法通过 `/EZMiner reloadConfig` 热重载。**

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enableUnlimitedOreFortune` | `false` | 启用后，GregTech 与 BartWorks 矿石将响应超过时运 III 的附魔等级，产出更多掉落物 |
| `maxFortuneLevel` | `3` | 矿石实际响应的最高时运等级上限（仅在 `enableUnlimitedOreFortune = true` 时生效，最高 255） |
| `enableFortuneForPlacedOre` | `false` | 启用后，玩家手动放置的矿石也视为天然矿石，同样享受时运加成 |

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
/EZMiner reloadConfig          # 重载配置文件（仅 OP，服务端生效后自动同步所有在线玩家）
/EZMiner reloadClientConfig    # 重载本地客户端配置（无需 OP 权限）
```

在不重启游戏的情况下重新读取配置文件，修改立即生效。  
**注意：时运破上限相关选项（`enableUnlimitedOreFortune` / `maxFortuneLevel` / `enableFortuneForPlacedOre`）通过 Mixin 在 JVM 启动时注入，不受热重载影响，修改后需完整重启游戏。**

---

## 兼容性

- Minecraft **1.7.10**
- Forge **10.13.4.1614**
- GregTech: New Horizons（GTNH）整合包
