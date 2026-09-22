# 幻翼爆破 Phantom Blast

> Minecraft **1.21.1** + **NeoForge** 模组
> 新增一只独立生物 **苦力怕幻翼**（`phantomblast:creeperphantom`）——  
> 行为、属性、掉落、音效、生成方式全部与幻翼一致，唯一区别是**俯冲撕咬命中玩家时引爆苦力怕级爆炸**。

---

## 1. 功能

本模组目前包含 **两只原创生物**：

| 生物 | 注册名 | 一句话 | 在哪一节 |
| --- | --- | --- | --- |
| 苦力怕幻翼 | `phantomblast:creeperphantom` | 会自爆的幻翼，生成方式与幻翼完全一致 | §1.1 ~ §5 |
| 水下苦力怕 | `phantomblast:water_creeper` | 水里比船还快的猎手，会撞船、自爆、跳上岸 | **§10** |

---

### 1.1 苦力怕幻翼：生物定位

**苦力怕幻翼**是一只全新的生物，不是对原版幻翼的改造。它继承原版幻翼的全部行为
（盘旋 → 嘶吼 → 俯冲 → 撕咬 → 脱离、怕猫、64 格锁敌、被阳光点燃、和平模式消失、体型随机缩放），
在此基础上加了一条：**咬中玩家的瞬间，在命中点引爆一个与苦力怕完全等价的爆炸**。

> **原版幻翼完全不受影响。** 它依然是原版的行为、原版的模型、原版的生成方式。
> 苦力怕幻翼和原版幻翼可以同时存在于同一个世界里，互不干扰。

### 1.2 与原版幻翼的差异

| 项目      | 原版幻翼                   | 苦力怕幻翼                         |
| ------- | ---------------------- | ---------------------------- |
| 注册名     | `minecraft:phantom`    | `phantomblast:creeperphantom` |
| 模型 / 贴图 | 原版 `PhantomModel`      | 幻翼翅膀 + 苦力怕身子（自定义）            |
| 俯冲击中玩家  | 造成撕咬伤害                 | 撕咬伤害 **+ 苦力怕级爆炸**            |
| **属性**  | 生命 20 / 攻击 6+体型 / 跟随 16 | **完全相同**                     |
| **AI**  | 全套俯冲、盘旋、怕猫逻辑           | **完全相同**（继承来的）               |
| **掉落**  | 幻翼膜                    | **完全相同**（复用原版掉落表）            |
| **音效**  | 幻翼叫 / 扇翅 / 俯冲嘶吼 / 死亡   | **完全相同**                     |
| **粒子**  | 翅膀下的孢子粒子               | **完全相同**                     |
| **命中箱** | 0.9 × 0.5 格            | **完全相同**                     |
| **生成方式** | 夜晚 + 3 天不睡 + 天空生成      | **完全相同**（见第 4 节）             |
| **刷怪蛋**  | `幻翼刷怪蛋`                | `苦力怕幻翼刷怪蛋`（自定义配色，见 4.5）      |

### 1.3 爆炸参数

| 参数      | 默认值    | 说明                                                           |
| ------- | ------ | ------------------------------------------------------------ |
| 威力      | `3.0`  | 与 `Creeper#explosionRadius` 默认值一致                            |
| 破坏方块    | 是      | 使用 `ExplosionInteraction.MOB`，受 `mobGriefing` 游戏规则约束（和苦力怕一样） |
| 引燃      | 否      | 原版苦力怕也不引燃                                                    |
| 粒子 / 音效 | 原版爆炸效果 | 由 `Level#explode` 自动同步给客户端                                   |

**所有参数都可以在配置文件里改**（见第 6 节），包括「只炸实体不炸地形」「生物免疫自己的爆炸」
「降低对被咬玩家的伤害」等。

> ⚠️ 默认配置是**完全忠实**的苦力怕级爆炸：被咬中的玩家会吃到  
> `撕咬伤害（6 + 体型）` **加上** `近距离爆炸伤害（威力 3.0 时约 49 点）`。  
> 即使穿钻石套也基本必死。觉得太狠请改 `hitPlayerDamageMultiplier`。

---

## 2. 环境要求

| 项目     | 版本                                                              |
| ------ | --------------------------------------------------------------- |
| Minecraft | 1.21.1                                                          |
| NeoForge | 21.1.172（`build.gradle` 中 `neo_version`，可用 `[21.1.0,)` 区间内任意版本） |
| Java     | 21                                                              |
| 模组加载器侧   | 自定义模型 + 独立实体，**客户端必须安装**；服务器也要装                                 |

---

## 3. 构建

```bash
# 项目根目录
./gradlew build          # Windows: gradlew.bat build
```

产物：`build/libs/phantomblast-1.1.1.jar`

- 只有**不带 `-sources`** 的那个 jar 是可安装的模组文件。
- 想直接跑一个带模组的开发客户端：`./gradlew runClient`；跑服务端：`./gradlew runServer`。

### ⚠️ `build.gradle` 里的 `mods {}` 块不能删

```groovy
neoForge {
    version = project.neo_version

    mods {                       // ← 必须显式声明，否则模组根本不会初始化
        "${mod_id}" {
            sourceSet sourceSets.main
        }
    }
    ...
}
```

**ModDevGradle 2.x 不再自动把 `sourceSets.main` 当成模组。** 少了这一块会出现一组极具迷惑性的症状：

| 现象 | 原因 |
| --- | --- |
| 模组列表里**能看到**这个模组 | `neoforge.mods.toml` 被正常读取 |
| 但 `@Mod` 类的构造函数**从不执行** | 类没被扫描到 |
| 实体全部没注册（`/summon` 找不到） | 上面那条的直接后果 |
| `config/phantomblast-common.toml` 不生成 | 同上 |
| 数据包标签报 `missing following references: phantomblast:xxx` | 同上 |
| **世界加载失败 / 卡在「正在准备生成世界」** | biome modifier 引用的实体不存在，注册表加载中断 |

排查这个坑花了很久，因为"模组被识别了"这个假象太有说服力。
**验证方法**：在构造函数第一行写个文件，看它会不会出现。

### 构建踩坑备忘（本机环境）

1. **Gradle 发行版**：`services.gradle.org` 在国内会 `Read timed out`。本机的 Gradle 8.14.3 已放进  
   `C:\Users\star\.gradle\wrapper\dists\gradle-8.14.3-bin\`，直接跑 `gradlew` 即可。  
   若哪天又被清理掉，用腾讯镜像手动补：
   ```bash
   curl -L -o gradle-8.14.3-bin.zip https://mirrors.cloud.tencent.com/gradle/gradle-8.14.3-bin.zip
   # 目录名 = MD5(distributionUrl) 转 36 进制，本项目对应 cv11ve7ro1n3o1j4so8xd9n66
   cp gradle-8.14.3-bin.zip ~/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/
   ```
2. **C 盘只剩几个 GB**。NeoForge 依赖和 MC 反编译产物缓存在 `C:\Users\star\.gradle\caches\`  
   （约 520MB，**别删**——删了要重新反编译 MC，很慢）。
3. **中文编码**：`build.gradle` 里 `options.encoding` / `filteringCharset` 两行不能删，  
   否则源码里的中文和 `neoforge.mods.toml` 的中文都会乱码。  
   `gradle.properties` **不能写中文**（Java Properties 按 ISO-8859-1 读取），  
   模组中文简介请写在 `src/main/resources/META-INF/neoforge.mods.toml` 里。

### 安装

1. 装好 NeoForge 1.21.1 的客户端 / 服务端。
2. 把 `phantomblast-1.1.1.jar` 丢进 `.minecraft/mods/`（或服务端 `mods/`）。
3. 启动过一次后，会在 `config/phantomblast-common.toml` 生成配置文件。

---

## 4. 自然生成（与原版幻翼完全一致）

### 4.1 原版幻翼是怎么生成的

幻翼**不走**普通刷怪系统 —— 它没有 `SpawnPlacements` 注册，也不占刷怪上限。
它由 `net.minecraft.world.level.levelgen.PhantomSpawner` 这个 `CustomSpawner` 直接生成，
挂在 `MinecraftServer#createLevels` 里传给**主世界** `ServerLevel` 的那份固定列表上。

判定顺序（每一项都原样保留）：

1. 本 tick 允许刷敌对生物；
2. 游戏规则 `doInsomnia` 为 true；
3. 40~120 秒的尝试间隔计时器到期（`(60 + rand(60)) * 20` tick）；
4. 夜晚判定：`getSkyDarken() < 5` **且**该维度有天空光；
5. 逐个遍历非旁观玩家，触发 `PlayerSpawnPhantomsEvent`；
6. 玩家头顶能看到天（或被事件 ALLOW 强制放行）；
7. 当前难度 `isHarderThan(rand * 3)`；
8. 玩家距上次睡觉的 tick 数满足 `rand.nextInt(TIME_SINCE_REST) >= 72000`（连续 3 个游戏日没睡）；
9. 生成点 = 玩家上方 20~34 格、水平 ±10 格；
10. 该位置是合法的空生成方块；
11. 刷出 1 ~（难度等级 + 1）只，各自 `finalizeSpawn` 后入场。

### 4.2 我们怎么做到"一致"

`com.phantomblast.entity.PhantomBlastSpawner` 是上面这段逻辑的**逐行复刻**，唯一的差别是把
`EntityType.PHANTOM` 换成了 `ModEntities.BLAST_PHANTOM`，并把几个硬编码常量提成了配置项
（**默认值与原版一字不差**）。

`PlayerSpawnPhantomsEvent` 也**照常触发**。代价是别的模组每轮会收到两次事件（原版幻翼一次、
我们一次）；好处是"装了反幻翼模组就不刷"这类行为对我们的生物同样生效 —— 这才叫一致。

### 4.3 怎么注入的（不需要 AT / Mixin）

`ServerLevel#customSpawners` 是 private 字段，那份列表是 `MinecraftServer` 里的局部变量，
常规做法得上访问转换器或 Mixin。

但 NeoForge 已经开了口子：`ServerLevel` 构造末尾会调用
`EventHooks#getCustomSpawners(this, 原始列表)`，里面 post 了 **`ModifyCustomSpawnersEvent`**。
订阅它就能往列表里加自己的生成器 —— 零侵入、零反射、零 Mixin。
注入代码在 `com.phantomblast.entity.PhantomBlastSpawnHook`。

**维度判定**：那个事件对每个 `ServerLevel` 都会触发。如果无脑添加，苦力怕幻翼就会在下界 / 末地
也刷出来 —— 那就跟幻翼不一致了。判据很直接：

> 看这个维度的生成器列表里**有没有 `PhantomSpawner`**。  
> 有 → 原版这里会刷幻翼 → 我们也加；没有 → 跳过。

这样"生成方式与幻翼一致"是自动成立的，将来原版改了规则我们也跟着走。

### 4.4 怎么快速测试

自然生成要**连续 3 个游戏日不睡觉**，正常玩很难触发。三个更快的办法：

**① 刷怪蛋** —— 创造模式「刷怪蛋」标签页里的**苦力怕幻翼刷怪蛋**，对着地面右键直接生成一只。

**② 指令** ——

```
/summon phantomblast:creeperphantom ~ ~10 ~
```

> 苦力怕幻翼**没有** `noSummon()`（原版幻翼也没有），所以 `/summon` 可以直接用。

**③ 调低门槛** —— 配置里把 `minTicksSinceRest` 改成 `24000`（1 个游戏日），
或者把 `attemptIntervalMinSeconds` / `attemptIntervalMaxSeconds` 调小。

想立刻被咬到，用 `/time set midnight`，然后站在开阔地等它盘旋几轮。
`debugLog = true` 可以在日志里看到引爆坐标。

### 4.5 刷怪蛋

| 项 | 值 |
| --- | --- |
| 物品 ID | `phantomblast:creeperphantom_spawn_egg` |
| 中文名 | 苦力怕幻翼刷怪蛋 |
| 获取 | 创造模式「刷怪蛋」标签页，或 `/give @s phantomblast:creeperphantom_spawn_egg` |
| 生成类型 | `MobSpawnType.SPAWN_EGG` |

#### 它没有自己的贴图 —— 一行模型就够了

原版刷怪蛋的外观不是每个生物一张图，而是只有**两张公用贴图**
（`minecraft:item/spawn_egg` 蛋壳 + `minecraft:item/spawn_egg_overlay` 斑点），
靠**两层 tint 染色**染出各自颜色。所以物品模型文件只有：

```json
{ "parent": "minecraft:item/template_spawn_egg" }
```

> 顺便验证过一个容易踩的坑：`template_spawn_egg` 里的纹路是相对路径
> （`"layer0": "item/spawn_egg"`）。我们从自己的命名空间继承这个模型，
> 会不会把纹理解析成 `phantomblast:item/spawn_egg`？答案是**不会** ——
> `BlockModel.Deserializer#parseTextureLocationOrReference` 走的是
> `ResourceLocation.tryParse()`，没有冒号的一律按默认命名空间 `minecraft` 解析。
> （`BlockModel.isTextureReference` 只处理 `#` 开头的同模型引用。）

#### 配色：从贴图里统计出来的，不是随手挑的

| 用途 | 颜色 | 来源 |
| --- | --- | --- |
| 蛋壳底色 | `#405080` 深蓝紫 | 贴图里**幻翼翅膀**的主色（占非透明像素约 3.3%） |
| 斑点高光 | `#80D070` 亮绿 | 贴图里**苦力怕皮肤**的主色 |

一个代表翅膀（幻翼血统），一个代表身体（苦力怕血统）—— 正好对应这只生物的构成。

参考：原版苦力怕刷怪蛋 = `0x0DA70B` / `0x000000`，
原版幻翼刷怪蛋 = `0x43518A` / `0x88FF00`。
我们的和幻翼那套同色系，但底色更深、绿点更柔和，放一起能看出是"亲戚"又不至于认错。

想换配色改 `ModItems` 里那两个常量即可。

#### 为什么用 `DeferredSpawnEggItem` 而不是原版 `SpawnEggItem`

原版那个构造函数收的是**实体类型实例**，NeoForge 已经把它标了 `@Deprecated`：

```java
/** @deprecated Forge: Use DeferredSpawnEggItem instead for suppliers */
@Deprecated
public SpawnEggItem(EntityType<? extends Mob> type, int bg, int hl, Item.Properties props)
```

物品注册发生在实体类型可用之前，用 `Supplier` 延迟取值才安全。

而且 `DeferredSpawnEggItem` 顺手把另外三件事也办了，我们一行代码都不用写：

- **发射器行为** —— `FMLCommonSetupEvent` 里自动 `DispenserBlock.registerBehavior(egg, ...)`，
  刷怪蛋丢进发射器能正常发射生物；
- **刷怪笼兼容** —— 把「实体类型 → 刷怪蛋」登记进 `TYPE_MAP`，
  刷怪蛋才能对着刷怪笼右键设置生物类型（`SpawnEggItem#byId` 会优先查这张表）；
- **客户端染色** —— `RegisterColorHandlersEvent.Item` 自动注册 tint，
  不需要自己写 `ItemColor`。

---

## 5. 实现原理

### 5.1 为什么是 `extends Phantom` 而不是从 `Monster` 重写

原版幻翼的全部行为都写在 `Phantom` 的**包私有内部类**里：
`PhantomAttackPlayerTargetGoal`（锁玩家 64 格）、`PhantomAttackStrategyGoal`（盘旋→嘶吼→俯冲）、
`PhantomSweepAttackGoal`（俯冲撕咬 + 怕猫）、`PhantomCircleAroundAnchorGoal`（绕锚点盘旋）、
`PhantomMoveControl` / `PhantomLookControl` / `PhantomBodyRotationControl`（飞行手感）。

这些内部类都引用 `Phantom.this`，而它们是在 `Phantom#registerGoals` 里实例化的。
`registerGoals` 是 `protected` —— 所以**只要继承并且不覆写它，子类就自动拿到一整套与原版
逐字节相同的行为**，不需要把上千行 AI 抄一遍，也不需要 Mixin。

**这样实现的好处**：版本升级时最不容易炸。原版一改俯冲节奏，我们跟着变，不用维护分叉。

### 5.2 为什么挂在 `LivingDamageEvent.Post` 上

俯冲撕咬发生在 `Phantom.PhantomSweepAttackGoal#tick()`：

```java
if (Phantom.this.getBoundingBox().inflate(0.2F).intersects(livingentity.getBoundingBox())) {
    Phantom.this.doHurtTarget(livingentity);      // ← 全游戏唯一让它造成近战伤害的地方
    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;  // 打完立刻脱离
    Phantom.this.level().levelEvent(1039, ...);   // 播放嘶吼音效
}
```

而 `Phantom` **没有**重写 `doHurtTarget`，走的是 `Mob` 的通用实现，最终落到 `LivingEntity#actuallyHurt`。

所以在 NeoForge 事件层面，「俯冲咬中玩家」完全等价于：

> 一次伤害序列的**伤害源实体是苦力怕幻翼**，且**受击者是玩家**。

选 `Post` 而不是 `Pre` / `LivingIncomingDamageEvent`，是因为 `Post` 只在**血量真的被扣掉之后**
才带最终值触发 —— 被无敌帧、盾牌格挡、抗性完全吸收的伤害不会误触发爆炸，符合"击中"语义。

### 5.3 三个必须踩对的坑

**坑 1：`DamageSource#getEntity()` 返回的是 causingEntity，不是 directEntity。**

如果按直觉写 `level.explode(blast, x, y, z, 3.0F, MOB)`，生成的爆炸伤害源会让本方法再次匹配到
`source.getEntity() instanceof PhantomBlastEntity`，直接进入 **爆炸 → 伤害 → 爆炸** 的无限递归
（一瞬上千次爆炸，崩服）。因此代码里显式构造了一个**没有任何实体归属**的伤害源：

```java
level.explode(
    null,                                        // 爆炸源实体 = null（关键）
    level.damageSources().explosion(null, null), // 无归属的爆炸伤害源
    calculator, x, y, z, power, fire, interaction);
```

副作用可接受甚至更好：死亡信息走 `DamageSource#getKillCredit()` 回退分支，取玩家"最后攻击者"——
而最后攻击者正是那只苦力怕幻翼，所以消息依然显示"被幻翼炸死了"。

另外还加了一道 `detonating` 静态闸门做兜底重入保护。

**坑 2：击退不受 `shouldDamageEntity` 约束。**

`Explosion#explode()` 里的逻辑是并列的两段：

```java
if (damageCalculator.shouldDamageEntity(this, entity)) {
    entity.hurt(damageSource, damageCalculator.getEntityDamageAmount(this, entity));
}
double knock = ... * damageCalculator.getKnockbackMultiplier(entity);   // ← 照样执行
```

所以要让生物「免疫自己的爆炸」，必须**同时**重写 `shouldDamageEntity`（免伤）
和 `getKnockbackMultiplier`（免击退），只重写前者的话它还是会被自己的冲击波掀飞。

**坑 3：威力 3.0 的近身爆炸会秒杀它自己。**

它只有 20 点血、无爆炸抗性，近距离爆炸伤害约 49 点。
默认 `phantomImmune = true` 让它炸完能继续盘旋、再次俯冲，更像"会自爆的掠食者"；
想要同归于尽就改成 `false`。

**坑 4（这次新增）：`Mob#getLootTable()` 是 `final` 的。**

想让它掉幻翼膜，覆写 `getLootTable()` 直接编译不过。正确的钩子是
`Mob#getDefaultLootTable()`（`protected`）—— `Mob#getLootTable()` 的实现是
`return this.lootTable == null ? this.getDefaultLootTable() : this.lootTable;`，
"默认掉落表"这个分支才是留给子类的。见 `PhantomBlastEntity`。

### 5.4 冷却

上一次引爆的游戏刻写在实体自身的持久化数据里（`Entity#getPersistentData()`），
随实体一起存盘 / 销毁，不需要额外维护 `Map` 也不会内存泄漏。

原版俯冲冷却本身就有 8~12 秒（`(8 + rand(4)) * 20` tick），这里的 40 tick 冷却只是防止
异常情况下的连环引爆。

---

## 6. 配置文件

`config/phantomblast-common.toml`

```toml
[general]
    # 总开关。false 时苦力怕幻翼只俯冲撕咬，不爆炸。
    # 注意：这个开关不影响原版幻翼 —— 它从头到尾都没被改过。
    enabled = true

[explosion]
    # 爆炸威力。原版苦力怕 = 3.0，TNT = 4.0，凋灵生成时 = 7.0。
    explosionPower = 3.0
    # 是否破坏方块。true = 苦力怕级（受 mobGriefing 约束）；false = 只炸实体、地形完好。
    destroyBlocks = true
    # 爆炸是否引燃周围方块。原版苦力怕不引燃。
    setFire = false
    # 苦力怕幻翼是否免疫自己引发的爆炸。
    phantomImmune = true

[damage]
    # 爆炸对所有实体的伤害倍率。
    damageMultiplier = 1.0
    # 额外作用于「被咬中的那名玩家」的伤害倍率。
    hitPlayerDamageMultiplier = 1.0
    # 同一只生物两次引爆的最小间隔（tick）。
    cooldownTicks = 40

[spawn]
    # 以下默认值均与原版幻翼 PhantomSpawner 的硬编码常量一致。
    naturalSpawn = true              # 是否参与自然生成
    minTicksSinceRest = 72000        # 距上次睡觉至少多少 tick（72000 = 3 个游戏日）
    skyDarkenThreshold = 5           # getSkyDarken() 小于它才算夜晚
    spawnIntervalMinSeconds = 60     # 生成尝试间隔下限（秒）
    spawnIntervalMaxSeconds = 120    # 生成尝试间隔上限（秒，不含）

[debug]
    debugLog = false
```

### 常用调参方案

| 想要的效果 | 怎么改 |
| --- | --- |
| 保护建筑，只炸人 | `destroyBlocks = false` |
| 太致命了，想能活下来 | `hitPlayerDamageMultiplier = 0.4` |
| 只惩罚周围的怪，不额外伤害被咬的人 | `hitPlayerDamageMultiplier = 0.0` |
| 更猛一点，像 TNT | `explosionPower = 4.0` |
| 自爆后必死（同归于尽） | `phantomImmune = false` |
| 暂时禁掉爆炸，只保留生物 | `enabled = false` |
| 世界里根本不想刷它出来 | `naturalSpawn = false`（之后只能用 `/summon`） |
| 想让它频繁出现 | `minTicksSinceRest = 24000`（改成 1 个游戏日） |
| 想确认到底有没有触发 | `debugLog = true`，日志里会打坐标和玩家名 |

---

## 7. 自定义模型

苦力怕幻翼用的是主人的 **「幻翼翅膀 + 苦力怕身子」** 混合模型。

### 文件

| 文件 | 作用 |
| --- | --- |
| `client/model/PhantomBlastModel.java` | 模型几何 + 动画（**自动生成，别手改**） |
| `client/PhantomBlastRenderer.java` | 渲染器（只注册给苦力怕幻翼） |
| `client/PhantomBlastClient.java` | 客户端注册：模型图层 + 渲染器 |
| `assets/phantomblast/textures/entity/creeperphantom.png` | 贴图 64×64 |
| `assets/phantomblast/lang/{en_us,zh_cn}.json` | 生物名称本地化 |

模型源工程：`vanilla-reference/bbmodel/phantomcreeper.bbmodel`
生成器：`vanilla-reference/tools/bbmodel_to_java.py`（改完 `.bbmodel` 重跑即可）

模型图层注册在 `phantomblast:creeperphantom` 下，**不会**和原版的 `minecraft:phantom` 图层撞车 ——
原版幻翼继续用它自己那份模型。

### 坐标换算

Blockbench 的 Modded Entity 格式 ↔ MC Java 实体模型之间是确定的映射：

```
java.x = -bb.x       java.y = 24 - bb.y       java.z = bb.z
java.xRot = -rad(bb.rx)   java.yRot = -rad(bb.ry)   java.zRot = +rad(bb.rz)
```

生成器对 **10 个立方体逐个做了世界 AABB 交叉验证，最大偏差 1.8e-15**（机器精度）。

### ⚠️ 一个必须知道的原版限制

**原版模型系统里立方体不能自带旋转** —— `CubeListBuilder.addBox()` 根本没有旋转参数，
只有 `PartPose`（部件级）有。而主人的模型里有 5 个立方体带 90°/180° 旋转（躯干横躺、四条腿），
所以生成器把它们各自提成了一个独立的「合成子部件」（`body_pivot`、`xxx_leg_pivot`），
把旋转转移到 `PartPose` 上。位置和外观完全等价。

> 顺带提醒：Blockbench 自带的 "Modded Entity → Java" 导出会**直接把立方体级旋转丢掉**。
> 如果哪天你直接用 Blockbench 导出，躯干会立起来、腿会乱掉 —— 那是它的锅，不是模型的锅。

### 动画

与原版幻翼**完全一致**：扇翅 ±16°、摆尾 0°~−10°。
四条腿挂在 `tail_base` / `tail_tip` 上，所以会跟着尾巴一起晃（后腿幅度是前腿的两倍）——
这是刻意设计，没有单独写腿部动画。

### 没有接的东西

- **眼睛发光层**：原版 `PhantomRenderer` 挂了 `PhantomEyesLayer`，用 `phantom_eyes.png`
  以 `RenderType.eyes` 画自发光眼睛。那层 UV 是按**原版幻翼的头**排布的，
  而我们的头换成了苦力怕头（UV 偏移 32,38），照搬会在莫名其妙的位置冒出 4 个绿点，
  所以**没挂**。新贴图里苦力怕脸本身就有眼睛。
- **头部跟随**：原版幻翼的头是固定的，这里也保持固定。想加就改
  `PhantomBlastModel.setupAnim` 里的 `head.yRot` / `head.xRot` —— 但注意
  `PhantomBlastRenderer.setupRotations` 已经把俯仰角作用到整只生物上了，头再跟一次会转两倍。

### 保留的原版渲染器补偿（别删）

`PhantomBlastRenderer` 里这几处是从原版抄的，删了模型会坐偏 / 俯冲时不会翻转：

```java
poseStack.scale(f, f, f);                      // 体型缩放 1 + 0.15 * phantomSize
poseStack.translate(0.0F, 1.3125F, 0.1875F);   // 坐落在命中箱正确位置
poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));  // 俯冲时整体俯仰
```

---

## 8. 已知边界

- **不影响原版幻翼**：从头到尾没有 Mixin、没有 AT、没有覆盖原版渲染器或生成器。
  卸载本模组后原版幻翼毫无痕迹（已经刷出来的苦力怕幻翼会变成"未知实体"）。
- **不影响对非玩家生物的攻击**：原版幻翼的目标选择器（`PhantomAttackPlayerTargetGoal`）
  只锁玩家，所以"咬到非玩家生物"这种情况原版根本不存在，不需要额外处理。
- **创造模式的玩家不会触发**：原版对创造玩家本来就不结算伤害。
- **盾牌完全格挡时不触发**：`newDamage` 被压成 0，判定为"没咬中"。
  如果你希望"举盾也算咬中也要炸"，把判断从 `LivingDamageEvent.Post` 换成
  `LivingIncomingDamageEvent` 即可。
- **多人服务器**：客户端必须安装（自定义模型 + 自定义实体）。
- **下界 / 末地不刷**：与原版幻翼一致，只在主世界自然生成。

---

## 9. 源码结构

```
src/main/java/com/phantomblast/
├── PhantomBlast.java                       模组主入口：实体 + 物品 + 属性 + 创造栏 + 生成规则 + 配置
├── entity/
│   ├── PhantomBlastEntity.java             苦力怕幻翼本体（extends Phantom）
│   ├── WaterCreeperEntity.java             水下苦力怕本体（extends Monster）
│   ├── ModEntities.java                    两个 EntityType 的注册
│   ├── PhantomBlastSpawner.java            幻翼系生成器（对标原版 PhantomSpawner）
│   └── PhantomBlastSpawnHook.java          把生成器注入 ServerLevel
├── ai/
│   ├── WaterCreeperSwellGoal.java          引信（对标原版 SwellGoal）
│   ├── WaterCreeperChaseGoal.java          追击 + 撞船
│   └── WaterCreeperBeachAssaultGoal.java   上岸突袭（跳出水面 → 落地自爆）
├── item/
│   └── ModItems.java                       两颗刷怪蛋（DeferredSpawnEggItem）
├── explosion/
│   ├── SwoopExplosionHandler.java          俯冲击中 → 引爆
│   └── PhantomBlastExplosionCalculator.java 爆炸参数（免伤 / 免击退 / 伤害倍率）
├── config/
│   └── PhantomBlastConfig.java             全部配置项
└── client/
    ├── PhantomBlastClient.java             客户端注册（仅 Dist.CLIENT）
    ├── PhantomBlastRenderer.java           苦力怕幻翼渲染器
    ├── WaterCreeperRenderer.java           水下苦力怕渲染器（复用海豚模型）
    └── model/PhantomBlastModel.java        模型几何 + 动画（自动生成）

src/main/resources/
├── META-INF/neoforge.mods.toml
├── pack.mcmeta
├── assets/phantomblast/
│   ├── lang/{en_us,zh_cn}.json
│   ├── models/item/*_spawn_egg.json                刷怪蛋模型（只继承原版模板）
│   └── textures/entity/creeperphantom.png
└── data/
    ├── minecraft/tags/entity_type/can_breathe_under_water.json   ← 让水下苦力怕能水下呼吸
    └── phantomblast/neoforge/biome_modifier/water_creeper_spawns.json
```

---

## 10. 水下苦力怕 Water Creeper

| 项 | 值 |
| --- | --- |
| 注册名 | `phantomblast:water_creeper` |
| 中文名 | 水下苦力怕 |
| 基类 | `Monster`（**不是** `Creeper`，原因见 10.2） |
| 生成 | 海洋 + 河流，水下，阴暗环境 |
| 模型 | **当前复用原版海豚模型与贴图** |
| 刷怪蛋 | 苦力怕绿底 + 水蓝色斑点 |

### 10.1 需求 → 实现对照

| 需求 | 实现 |
| --- | --- |
| 生成在海洋、其它水域 | biome modifier：拆成 `water_creeper_ocean.json` + `water_creeper_river.json` 两个文件（原因见下） |
| 与普通苦力怕相似，夜晚或阴暗环境生成 | 生成谓词复用 `Monster#isDarkEnoughToSpawn`（难度 + 亮度），外加"头顶必须是水" |
| 游动速度比船还快 | `MOVEMENT_SPEED = 1.5`。参照：海豚 **1.2**、僵尸 0.23、玩家疾跑 0.13；船的最大速度约 0.4 |
| 主动攻击玩家 | `FOLLOW_RANGE = 32` + `NearestAttackableTargetGoal<Player>` |
| 撞击船上的玩家，造成伤害并打断行动 | `WaterCreeperChaseGoal` 碰撞箱相交即触发 `WaterCreeperEntity#ram()`：对玩家造成伤害 + 给船一个横向冲量把它撞偏 |
| 准备爆炸时膨胀 | 与苦力怕同款引信：`swell` 每 tick +1，30 tick 后引爆；渲染器按 `CreeperRenderer` 的四次方曲线放大 |
| 跳上岸后立即爆炸 | `WaterCreeperBeachAssaultGoal`：玩家在岸上且在 14 格内时冲过去，3.5 格内朝玩家抛出水；落地瞬间由 `aiStep` 判定引爆 |

### 10.2 ⚠️ biome modifier 的 `biomes` 只能写「单个标签字符串」

这是踩过坑的，**实测结论**（在本地开发服务器上逐个格式验证过）：

| `biomes` 的写法 | 结果 |
| --- | --- |
| `"#minecraft:is_ocean"` | ✅ 可行 |
| `["#minecraft:is_ocean", "#minecraft:is_river"]` | ❌ `Not a JSON object: "#minecraft:is_river"` |
| `[{"tag": "minecraft:is_ocean"}, ...]` | ❌ `Failed to parse` |

**一个 biome modifier 只能作用于一个标签。** 想覆盖海洋 + 河流，就写两个文件 ——
这就是 `water_creeper_ocean.json` 和 `water_creeper_river.json` 的由来。

> 失败代价很高：`biomes` 解析失败会让**整个 `RegistryDataLoader` 加载中断**，
> 表现为**建新存档卡在「正在准备生成世界」、老存档进不去**。
> 不是"这个生成项不生效"这么轻——是整个数据包加载失败。

顺带一提，`spawners` 字段两种都收（NeoForge 用了 `Codec.either(listOf, single)`），
写单对象或数组都可以。

### 10.3 三个技术要点

**① 水下呼吸靠的是实体类型标签，不是覆写方法。**

`LivingEntity#canBreatheUnderwater()` 是 **`final`** 的，覆写不了。它的实现是：

```java
public final boolean canBreatheUnderwater() {
    return this.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER);
}
```

所以正解是把实体类型加进 `#minecraft:can_breathe_under_water` 标签
（`data/minecraft/tags/entity_type/can_breathe_under_water.json`）。
**注意不能加 `"replace": true`** —— 那会把原版列表整个顶掉，海豚、鱿鱼、守卫者全都会开始淹死。

副作用要说清楚：进了这个标签就等于承认自己是水生生物，**三叉戟的穿刺附魔会对它造成额外伤害**。
这个惩罚是合理的，留着。

**② 基类为什么不是 `Creeper`。**

`Creeper` 的整套 AI 建立在陆地寻路之上，甚至专门有一个 `WaterAvoidingRandomStrollGoal`
（怕水）—— 和我们的需求正好相反。所以从 `Monster` 起，只借它的**引信与爆炸数值**
（30 tick 引信、半径 3、`ExplosionInteraction.MOB`），移动层整个换成水生那一套：

```java
this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
this.lookControl = new SmoothSwimmingLookControl(this, 10);
// createNavigation → WaterBoundPathNavigation
```

这几个参数**直接抄的海豚**。海豚是原版唯一"在水里高速追船"的生物，
它的手感参数不值得我另起炉灶。

**③ 上岸之所以要重写 `canContinueToUse`。**

`Goal` 默认的 `canContinueToUse()` 就是再调一次 `canUse()`。
而 `canUse()` 要求 `mob.isInWater()` —— 它一跳起来就离开水面，
`canUse()` 立刻变 false，目标被中止、突击标志被清掉，**落地就不会爆炸了**。

所以 `WaterCreeperBeachAssaultGoal` 重写了它：只要已经进入突击状态就咬着不放，
直到落地引爆（实体消失）或目标消失。这个"有去无回"的语义正是需求要的。

### 10.4 怎么测试

```
/summon phantomblast:water_creeper ~ ~ ~
```

或者用创造模式里的「水下苦力怕刷怪蛋」。想现场看效果：

1. 找一片海，`/time set midnight`（白天浅水够亮，它不会刷）；
2. 划船在水面上飘着 —— 它应该会高速冲过来撞船，把船撞偏；
3. 跳下水 —— 它会贴上来膨胀，1.5 秒后爆炸；
4. 逃到岸上 —— 它应该会跳出水面追上来，落地即爆。

`debugLog` 配置目前只覆盖幻翼那边，水下苦力怕暂时没有日志输出。

### 10.5 已知限制

- **模型是借的。** 现在用的是原版海豚模型和贴图，命中箱也跟着海豚走（0.9 × 0.6）。
  换自定义模型时，改 `WaterCreeperRenderer` 的两行（图层 + 贴图）和 `ModEntities` 的尺寸即可，
  膨胀与踩水逻辑不用动。
- **岸特别高的地方它上不去。** 跳跃初速固定（水平 0.35 / 垂直 0.5），大约能上 1.5 格。
  躲到悬崖上就是安全的 —— 这个我认为是合理的，不是缺陷。
- **爆炸参数目前写死在实体类里**（半径 3、引信 30 tick），没有做成配置项。
  幻翼那套配置在 `[explosion]` 段，两边暂时没有统一。想调的话说一声。

