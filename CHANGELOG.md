# 改动记录 Changelog

本文件记录本模组的所有重要改动。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

**下载**：见 [GitHub Releases](https://github.com/q769429109-gif/creeper-phantom/releases)。

---

## [1.3.1] — 2026-09-23

### 修复

- **水下苦力怕在水中移动过慢（严重手感问题）**：
  根因是 `LivingEntity.travel()` 把水中的速度系数**硬编码成 `0.02F`**，
  只有 `NeoForgeMod.SWIM_SPEED`（或 `WATER_MOVEMENT_EFFICIENCY`）属性能放大它。
  此前水下苦力怕没有注册 `SWIM_SPEED`，导致 `SmoothSwimmingMoveControl` 设的速度、
  `MOVEMENT_SPEED = 1.5` 全被无视，实际只有约 **0.02 格/tick（≈0.4 米/秒），几乎不动**——
  也就是 1.2.0 说的"在水里比船还快"其实一直是坏的。
  现已在 `WaterCreeperEntity.createAttributes()` 注册
  `NeoForgeMod.SWIM_SPEED = 15.0`（即 `0.02 × 15 = 0.3 格/tick ≈ 6 米/秒`），
  恢复"高速撞船"的设计手感。**想调快慢只改这一个值即可。**

---

## [1.3.0] — 2026-09-23

**首个公开发布版本。**

### 新增

- **⚡ 闪电充能** —— 两只生物都能像原版苦力怕那样被闪电劈成「充能」状态：
  - 被劈中后**爆炸半径翻倍**（3.0 → 6.0）。倍率逐字对齐
    `Creeper#explodeCreeper()` 里的 `float f = this.isPowered() ? 2.0F : 1.0F;`
  - 充能是**永久的** —— 劈过一次就一直保持，淋雨、泡水都不会掉，与原版一致；
  - 存档键名沿用原版的 `powered`，且**只在为真时才写**
    （原版同样如此，读的时候缺键 `getBoolean` 返回 false）；
  - 任何来源的闪电都生效：雷雨天自然落雷、**引雷附魔的三叉戟**、
    `/summon lightning_bolt`；
  - 水下苦力怕**在水里同样能被劈中** —— 原版闪电的实体判定不做水中过滤
    （范围 `[x±3, y−3~y+9, z±3]`）。
- **充能时的视觉表现**：身上裹一层滚动的蓝色能量（`PoweredOverlayLayer`）。
  滚动参数与顶点色与 `EnergySwirlLayer` 完全一致。
  贴图 `charged_energy.png` 是一张 **128×128 纯算法生成的无缝图**，
  同一张同时服务两只 UV 排布完全不同的生物，且不含任何 Mojang 素材衍生。

### 变更

- 模组简介改为提及闪电充能。

### 说明

- `1.2.0` 的全部内容都包含在 `1.3.0` 里，**`1.2.0` 未曾公开发布**。

---

## [1.2.0] — 2026-09-23

### 新增

- **水下苦力怕**（`hybridcreeper:water_creeper`）—— 水生敌对生物：
  - 生成于海洋与河流水下，阴暗环境；
  - 游动速度 **1.5**（海豚 1.2、船约 0.4），在水里比船还快；
  - 高速撞击船上的玩家，造成伤害并把船撞偏，打断玩家行动；
  - 贴身膨胀约 1.5 秒后引爆（机制同苦力怕）；
  - 玩家靠近岸边时跳出水面突袭，落地立即爆炸，跳跃高度约 1.5 格；
  - 当前**借用原版海豚模型与贴图**（命中箱 0.9 × 0.6 也随之）。
- 水下苦力怕刷怪蛋（苦力怕绿底 + 水蓝色斑点）。

### 变更

- **全量改名为「杂交苦力怕 / Hybrid Creeper」**（原「幻翼爆破 / Phantom Blast」）：
  - 模组 ID：`phantomblast` → `hybridcreeper`
  - 实体 ID：`phantomblast:creeperphantom` → `hybridcreeper:creeperphantom`（水下苦力怕同理）
  - 配置文件：`config/phantomblast-common.toml` → `config/hybridcreeper-common.toml`
  - Java 包：`com.phantomblast` → `com.hybridcreeper`
  - 构建产物：`phantomblast-x.y.z.jar` → `hybridcreeper-x.y.z.jar`
- **README 重写为使用者向**：只保留安装、玩法、配置、注意事项；
  开发者向内容（构建流程、实现原理、源码结构）移出，不再随仓库发布。
- 模组简介改为覆盖两只生物（原来只描述了苦力怕幻翼）。

### 修复

- README 中苦力怕幻翼的英文名误写为 `Blast Phantom`，订正为 `Creeper Phantom`
  （游戏内显示名一直是正确的，仅文档有误）。

### 升级注意

**已有存档里由开发版刷出的生物会变成「未知实体」。** 实体 ID 的命名空间已改变，
这是不可逆的。删掉旧 jar 换上新 jar 即可，没有其他需要处理的事项。

---

## [1.1.1] — 2026-09-22

### 修复

- **修复世界无法加载（严重）**：安装后新建存档卡在「正在准备生成世界」、旧存档进不去。
  两个独立根因：
  1. `build.gradle` 的 `neoForge {}` 块缺少 `mods {}` 声明 —— ModDevGradle 2.x
     不再自动把 `sourceSets.main` 当作模组，导致 `@Mod` 类从未被扫描、构造函数从未执行、
     所有实体都没有注册，最终 biome modifier 引用到不存在的实体而使注册表加载中断；
  2. biome modifier 的 `biomes` 字段**只能写单个标签字符串**，写成数组会导致数据包解析失败。
     拆分为 `water_creeper_ocean.json` 与 `water_creeper_river.json` 两个文件。

---

## [1.1.0] — 2026-09-22

### 新增

- **苦力怕幻翼改为独立生物**（此前是直接改造原版幻翼）。
  **原版幻翼恢复完全原版的行为、模型与生成方式**，两者可在同一世界共存。
- 与幻翼完全一致的自然生成：复刻原版 `PhantomSpawner` 的全部判定
  （`doInsomnia` 规则、40~120 秒尝试间隔、`getSkyDarken() < 5`、
  距上次睡觉 72000 tick、玩家上方 20~34 格、`isValidEmptySpawnBlock`、
  按难度刷 1~N 只），并通过 NeoForge 的 `ModifyCustomSpawnersEvent` 注入，
  无需 Mixin / AT。`PlayerSpawnPhantomsEvent` 照常触发。
- 自定义模型：**幻翼的翅膀 + 苦力怕的身子**，动画与原版幻翼一致（扇翅 ±16°、摆尾）。
- 苦力怕幻翼刷怪蛋（深蓝紫蛋壳 `#405080` + 亮绿斑点 `#80D070`，取自模型贴图主色）。
- 刷新位置硬编码常量提为配置项（**默认值与原版一致**）。

### 变更

- 实体注册名 `blast_phantom` → `creeperphantom`。
- 显示名「爆破幻翼 / Blast Phantom」→「苦力怕幻翼 / Creeper Phantom」。

---

## [1.0.0] — 2026-09-21

### 新增

- 首个可运行版本：**改造原版幻翼**，在其俯冲撕咬命中玩家时，
  于命中点引爆一个与苦力怕等价的爆炸（威力 3.0、`ExplosionInteraction.MOB`、不引燃）。
- 自定义模型与贴图（幻翼翅膀 + 苦力怕身子）。
- 配置文件：爆炸威力、是否破坏方块、是否引燃、幻翼是否免疫、伤害倍率、冷却、调试日志。

### 说明

- 本版本直接覆盖了原版 `PhantomRenderer`，**会改变原版幻翼的外观与行为**。
  该设计已于 1.1.0 废弃，改为新增独立生物。

---

[1.3.1]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.3.1
[1.3.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.3.0
[1.2.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.2.0
