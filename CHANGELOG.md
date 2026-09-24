# 改动记录 Changelog

本文件记录本模组的所有重要改动。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

**下载**：见 [GitHub Releases](https://github.com/q769429109-gif/creeper-phantom/releases)。

---

## [1.9.1] — 2026-09-24

### 修复

- **启动崩溃：`Cannot get config value before config is loaded.`**
  - 1.9.0 把属性数值改成读配置之后，游戏**在模组加载阶段就崩**：日志刷满
    `Cowardly refusing to send event … to a broken mod state`，
    崩溃报告 `crash-*-fml.txt` 直接点名 `WaterCreeperEntity.createAttributes`。
  - 根因：`createAttributes()` 是在 `EntityAttributeCreationEvent` 里被调用的，
    而该事件由 `GameData#postRegisterEvents → CommonHooks#modifyAttributes`
    在**注册表后期**抛出 —— **早于 `ModConfigSpec` 加载**。
    在那一刻读配置必然抛 `IllegalStateException`，并把整个模组状态打成 broken。
  - 修复：`createAttributes()` 内恢复写死默认值（与配置默认值保持一致）；
    配置里的真值改由新增的 `applyConfiguredAttributes()` 在**实体构造时**写入
    （构造函数即"实体生成时"，配置早已就绪），并在改完生命上限后重设满血。
  - 副作用（已同步写进配置文件注释）：**改配置只对之后新生成的个体生效**，
    存档里已有的个体保留自己的数值；重新刷一只即可看到新数值。

## [1.9.0] — 2026-09-24

### 新增

- **苦力怕海豚的全部参数搬进配置文件**：新增 `[waterCreeper]` 段
  （下辖 `.attributes` / `.explosion` / `.ai` / `.ram`），共 17 项可调 ——
  `explode` / `naturalSpawn` / `maxHealth` / `attackDamage` / `movementSpeed` /
  `swimSpeed` / `followRange` / `knockbackResistance` / `explosionPower` /
  `destroyBlocks` / `setFire` / `damageMultiplier` / `fuseTicks` /
  `chargedMultiplier` / `swellRange` / `beachAssault` / `ramCooldownTicks`。

### 变更

- `HybridCreeperExplosionCalculator` 由「苦力怕幻翼专用」改为**两只生物共用**
  （`hitTarget` 加判空保护）。
- README「配置」章节重写：新增「想调什么 → 改哪一项」对照表，
  并删掉已过时的「苦力怕海豚不读配置文件」一句。
- 各项默认值与原本的硬编码值一一对应，不动配置文件即维持原手感。

> ⚠️ **1.9.0 存在启动崩溃**（原因与修复见 [1.9.1]），请直接使用 **1.9.1**。

## [1.8.0] — 2026-09-24

### 变更

- **苦力怕海豚的生成范围扩大到「任何水域」**（原为海洋 + 河流）。
  - 生物群系修饰符由两份（`#minecraft:is_ocean` / `#minecraft:is_river`）
    **合并为一份** `water_creeper_any_water.json`，作用于 `#minecraft:is_overworld`。
    合并而**不是新增**，是为了避免海洋里被**加权两次**（`add_spawns` 是追加语义）；
    实测每个群系里该条目只出现 **1** 次。
  - 结果：**湖泊、池塘、沼泽乃至沙漠里的水洼**都会刷 —— 只要是水。
  - 海洋/河流里的权重不变（仍是 `weight 1`，与墨鱼、海豚同池）。
- **生成谓词放宽：不再要求「上方一格也是水」**，1 格深的浅水/小水坑现在也会刷。
  - 原守卫的前提是"按 1.8 格高的生物想"；本生物命中箱只有 **0.6 格高**，
    站在一格水里时身体整段都在那一格水方块内部、**根本不会露头**，
    所以守卫是多余的 —— 副作用却是把小水坑全部排除。
  - 安全性由其它环节兜底（已实测）：`IN_WATER` 保证生成点是水且上方非红石导体；
    **充水台阶 / 楼梯 / 栅栏等含水的实心方块会被 `noCollision(spawnAABB)` 挡掉**
    （实测充水台阶：`checkSpawnRules=true` 但 `noCollision=false` → 不生成）。

### 实测（开发服 `runServer` 打印真实生成表）

| 生物群系 | 该条目出现次数 | `water_creature` 生成表 |
| --- | --- | --- |
| `ocean` | 1 | `[squid(1), dolphin(1), water_creeper(1)]` |
| `deep_cold_ocean` | 1 | `[squid(3), water_creeper(1)]` |
| `river` / `frozen_river` | 1 | `[squid(2), water_creeper(1)]` |
| `plains` / `forest` / `swamp` / `desert` / `savanna` / `jungle` / `mushroom_fields` / `snowy_plains` | 1 | `[water_creeper(1)]` |

**12 / 12 命中。** 1 格深水坑：`isSpawnPositionOk / checkSpawnRules /
checkSpawnObstruction / checkSpawnPosition / noCollision` 全 `true`。

### ⚠️ 手感变化（本次是明确要求，先说清）

- **任何水域都可能出现它** —— 包括家门口 1 格深的水坑。
- 刷新配额仍是 `water_creature` 的**上限 5**，所以一个小水池里最多同时存在 5 只。
- 在非海洋/河流群系里这个池子**只有它一个成员**，所以水坑一旦合格就是 100% 刷它。

---

## [1.7.1] — 2026-09-24

### 修复

- **苦力怕海豚一条都不自然生成**。根因**不在**生成表 —— 生物群系修饰符、
  权重、位置类型全都注册正确（开发服实测：`deep_cold_ocean` 的
  `water_creature` 表 = `[squid(3), water_creeper(1)]`）。
  卡住它的是自然生成的**最后一道闸门** `Mob#checkSpawnObstruction`：

  ```java
  // Mob 的默认实现（给陆地生物用的）
  return !level.containsAnyLiquid(this.getBoundingBox()) && level.isUnobstructed(this);
  ```

  即"**身体包围盒里不能有液体**"。我们的生物泡在水里，这一条**永远为假** ——
  于是 `IN_WATER` 位置类型、生物群系权重、附加谓词、`noCollision`
  全都通过，最后还是被这一句否掉。症状就是"**生成表里有它，世界里一条都没有**"。

  - **修复**：在 `WaterCreeperEntity` 覆写
    `checkSpawnObstruction(LevelReader)` → `return level.isUnobstructed(this);`，
    与**原版每一个水生生物逐字一致** —— `WaterAnimal`（墨鱼/海豚/鱼）、
    `Guardian`、`Axolotl`、`Strider`，以及**同为 `Monster` 的 `Drowned`（溺尸）**。
    溺尸能刷的水域，它现在也能刷。
  - **实测证据**（开发服 `runServer` 打印）：修复前 `checkSpawnObstruction=false`，
    修复后 `true`，整条链 `checkSpawnPosition / isSpawnPositionOk /
    checkSpawnRules / noCollision` 全部 `true`；对照组**原版苦力怕**
    在同一池水里仍为 `false`（判定没被削弱）。

### 说明

- 生成范围仍是**海洋 + 河流**生物群系（与墨鱼一致）。**湖泊/池塘/沼泽**
  所在的生物群系（平原、森林、沼泽…）原版 `water_creature` 表**本身就是空的**，
  所以那些地方不会有它 —— 想扩大到"任何水域"需要另加生物群系修饰符。

---

## [1.7.0] — 2026-09-24

### 变更

- **苦力怕幻翼生成数量翻倍**：一次生成尝试刷出的数量乘 2
  （原版幻翼按难度刷 1~N 只，本模组 ×2）。新增配置项
  `spawnCountMultiplier`（默认 2；改成 1 即与原版一致）。
- **苦力怕海豚：改成和墨鱼同一个刷新池**（`MONSTER` → `WATER_CREATURE`）。
  - **不再要求"阴暗环境"** —— 水里**任何时段**都能刷（与墨鱼一致），
    之前只在黑水里刷，实际等于"看不见它"；
  - 刷新配额与墨鱼、海豚共用（上限 **5**），节奏也跟着"刷动物"那一 tick
    （每 400 tick 一次），所以出现频率**真的和墨鱼相当**；
  - 生成权重/数量对齐墨鱼：海洋 `weight 1 / min1 / max4`、
    河流 `weight 2 / min1 / max4`（原先 40/30，明显过密）；
  - 位置判定仍是自写的"全身在水里"，**没有**照抄墨鱼的
    `checkSurfaceWaterAnimalSpawnRules` —— 那会把生成点限制在海平面以下 13 格内，
    我们要的是**深水区也能刷**。
  - Java 类仍是 `Monster`：刷新分类与 Java 基类是两件事。

---

## [1.6.0] — 2026-09-24

### 变更

- **生物改名**：「水下苦力怕 / Water Creeper」→ **「苦力怕海豚 / Creeper Dolphin」**。
  只改**显示名**（语言文件 + 文档 + 代码注释）——
  实体注册 id `hybridcreeper:water_creeper` **保持不变**，
  所以已有存档、`/summon` 指令、刷怪蛋都不受影响。
- **游动速度减半**：`SWIM_SPEED` **15 → 7.5**（`0.02 × 7.5 = 0.15 格/tick ≈ 3 格/秒`）。
  实测 15 倍"移速太快"。只影响**水里**；陆地/上岸突袭走的仍是 `MOVEMENT_SPEED`（1.5，未动）。
- README 同步：原名与"模型暂时借用原版海豚"等过时描述一并订正。

---

## [1.5.2] — 2026-09-24

### 修复

- **左右胸鳍长到背上了**：`.bbmodel` 里两片胸鳍的 **rotation 丢失**
  （origin 还在），于是它们没被甩到身体两侧，而是卡在躯干宽度以内、
  从躯干顶部戳出去 —— 看着就像长在背上。
  已把原版海豚的旋转补回立方体上：
  `left_fin` → `[-60, 0, 120]`、`right_fin` → `[-60, 0, -120]`。
  现在两片胸鳍的世界坐标与原版海豚**逐位一致**（x 伸到 ±5.71，超出躯干的 ±4）。

### 说明

- 同类问题的通用判断法：**分组/立方体的 `origin` 和 `rotation` 是两件事**，
  只继承 origin 而丢掉 rotation，部件的位置看着"差不多"、姿态却是错的。
  转换后建议把每个立方体的世界 AABB 与原版对照。

---

## [1.5.1] — 2026-09-24

### 修复

- **左右胸鳍在游戏里"消失"**：`.bbmodel` 里残留了一块**隐藏的「海豚身体」方块**
  （`visibility = false`，但在 Blockbench 里 `export` 仍为 true）。
  它被隐藏所以编辑时看不见，导出到游戏后却把两片胸鳍**整个包在体积内部**
  （胸鳍 y16–20 / z−1..6 完全落在它 y15–22 / z−5..8 之内）。
  已从 `.bbmodel` 里删除该残留方块，胸鳍恢复可见。
- 顺带把生成器补强：**导出前会提醒 `visibility=false` 的元素**
  （Blockbench 隐藏≠不导出，这种残留最容易漏）。

---

## [1.5.0] — 2026-09-24

### 新增

- **水下苦力怕的游动动画**：直接采用 Blockbench 工程 `creeperdolphin.bbmodel`
  里的 `swim` 动画，由生成器把关键帧曲线导出成 `setupAnim`
  （线性插值，角度按 `java = -rad(bb)` 换算）。
  一个循环 = 1.0472 秒（20.944 tick），身体轻摆 + 四条腿划水，首尾相等、循环无缝。
  改动画只需改 `.bbmodel` 后重跑生成器。

### 修复

- **背鳍错位**：上一版的背鳍是平的（`.bbmodel` 里没给旋转），
  贴在躯干上像一块错位的方块。现背鳍带 −50° 旋转，与 Blockbench 完全一致。
- **模型层级改为与 Blockbench 骨头 1:1**：每个 group 生成一个部件，
  这样 `.bbmodel` 的动画（animator 按骨头 uuid 索引）能直接落到对应部件上。
- 校验升级为**逐立方体 ×8 角点**交叉验证（能识别 ±90° 旋转的方向错误），
  10 个立方体最大偏差 3.55e-15。

### 说明

- 部件名沿用 Blockbench 工程：`tail` 这个骨头（uuid 继承自原版海豚的尾巴）
  在本模型里装的是**四条腿** —— 名字保持不变，免得和工程脱节。
- 本版取代 1.4.0 里"沿用原版海豚动画"的做法。

---

## [1.4.0] — 2026-09-24

### 变更

- **水下苦力怕换上专属模型与贴图**：不再是"借用原版海豚模型 + 海豚贴图"，
  改为自绘的 **海豚身体 + 苦力怕躯干 / 头 / 四条腿** 混合模型
  （Blockbench 工程 `creeperdolphin.bbmodel`，配套 64×64 专属贴图
  `textures/entity/water_creeper.png`）。
  - 模型几何由工具 `gen_water_creeper_model.py` 反向换算生成，
    并对每个立方体做了世界 AABB 交叉验证（最大偏差 ~1e-15）；
  - 水中动画沿用原版海豚：身体俯仰/偏航跟随朝向，游动时叠加摆尾振荡，
    四条腿随游动轻轻划水；
  - 模型图层走本模组私有命名空间（`hybridcreeper:water_creeper`），
    **原版海豚完全不受影响**；
  - 引信膨胀、闪白、闪电充能能量层的逻辑一概不变。

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

[1.9.1]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.9.1
[1.9.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.9.0
[1.8.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.8.0
[1.7.1]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.7.1
[1.7.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.7.0
[1.6.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.6.0
[1.5.2]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.5.2
[1.5.1]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.5.1
[1.5.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.5.0
[1.4.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.4.0
[1.3.1]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.3.1
[1.3.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.3.0
[1.2.0]: https://github.com/q769429109-gif/creeper-phantom/releases/tag/v1.2.0
