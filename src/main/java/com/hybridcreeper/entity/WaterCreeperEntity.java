package com.hybridcreeper.entity;

import com.hybridcreeper.ai.WaterCreeperBeachAssaultGoal;
import com.hybridcreeper.ai.WaterCreeperSwellGoal;
import com.hybridcreeper.ai.WaterCreeperChaseGoal;
import com.hybridcreeper.config.HybridCreeperConfig;
import com.hybridcreeper.explosion.HybridCreeperExplosionCalculator;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 苦力怕海豚 Creeper Dolphin —— 水域专属的高速自爆猎手。
 *
 * <h2>定位</h2>
 * <p>它填补的是原版的一个空白：<b>水里没有会爆炸的威胁</b>。
 * 原版的水生敌对生物只有守卫者（激光，靠蓄力）和溺尸（近战），
 * 玩家在水面上划船基本是安全的。苦力怕海豚把这份安全感拿掉：</p>
 * <ul>
 *   <li><b>比船快</b> —— 移动速度属性 1.5，而原版海豚是 1.2、玩家疾跑才 0.13。
 *       在水里追船是稳赢的；</li>
 *   <li><b>主动索敌</b> —— 跟随范围 32 格，主动锁定玩家；</li>
 *   <li><b>撞船</b> —— 撞上船会给船上玩家造成伤害并把船撞偏（见 {@code WaterCreeperChaseGoal}）；</li>
 *   <li><b>膨胀自爆</b> —— 与苦力怕同款的引信（30 tick / 1.5 秒），威力 3.0；</li>
 *   <li><b>上岸突袭</b> —— 玩家缩在岸上时，它会跳出水面，落地即爆
 *       （见 {@code WaterCreeperBeachAssaultGoal}）。</li>
 * </ul>
 *
 * <h2>基类为什么是 {@link Monster} 而不是 {@link net.minecraft.world.entity.monster.Creeper}</h2>
 * <p>{@code Creeper} 的全部 AI 都建立在陆地寻路（{@code GroundPathNavigation}）和
 * {@code WaterAvoidingRandomStrollGoal} 之上 —— 那是"怕水"的设计，我们要的正好相反。
 * 所以从 {@code Monster} 起，只借苦力怕的<b>引信与爆炸逻辑</b>（数值与 {@code Creeper} 完全一致），
 * 移动层全部换成水生那一套。</p>
 *
 * <h2>水下呼吸</h2>
 * <p>{@code LivingEntity#canBreatheUnderwater()} 是 <b>final</b> 的，无法覆写，它的判据是
 * <b>实体类型标签</b> {@code #minecraft:can_breathe_under_water}。
 * 所以我们在数据包里把自己的实体类型加进那个标签
 * （{@code data/minecraft/tags/entity_type/can_breathe_under_water.json}），
 * 而不是去骗过什么方法。顺带一个副作用：加了标签就等于承认自己是水生生物，
 * 三叉戟的穿刺附魔会对我们造成额外伤害 —— 这个惩罚是合理的，留着。</p>
 *
 * <h2>水流</h2>
 * <p>{@code isPushedByFluid()} 返回 false（与海豚一致），否则水流会把它冲到不知道哪里去。</p>
 */
public class WaterCreeperEntity extends Monster implements PoweredMob {

    /* ------------------------------------------------------------------
     * 同步数据
     * ------------------------------------------------------------------ */

    /** 引信方向：1 = 正在膨胀，-1 = 正在收缩。与苦力怕同名同义。 */
    private static final EntityDataAccessor<Integer> DATA_SWELL_DIR =
            SynchedEntityData.defineId(WaterCreeperEntity.class, EntityDataSerializers.INT);

    /**
     * 是否处于"上岸突袭"状态。
     *
     * <p>同步到客户端只为一件事：让渲染器在它腾空上岸时给出更醒目的视觉提示
     * （比如膨胀得更快）。逻辑全部在服务端跑。</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_BEACH_ASSAULT =
            SynchedEntityData.defineId(WaterCreeperEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 充能状态（被闪电劈中）。
     *
     * <p>必须同步到客户端 —— 蓝色能量层靠它决定画不画。
     * 与 {@code HybridCreeperEntity} 里那个是同一套做法，
     * 之所以各写一份而不用共用字段，见 {@link PoweredMob} 的类注释。</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_POWERED =
            SynchedEntityData.defineId(WaterCreeperEntity.class, EntityDataSerializers.BOOLEAN);

    /* ------------------------------------------------------------------
     * 引信状态（逐字对标 Creeper）
     * ------------------------------------------------------------------ */

    private int oldSwell;
    private int swell;

    /**
     * 引信长度。原版苦力怕 = 30 tick（1.5 秒）。
     *
     * <p>默认值来自配置 {@code waterCreeper.explosion.fuseTicks}，在构造时写入；
     * 之后的读档（NBT {@code Fuse}）可以按只覆盖它 —— 与苦力怕的存档格式保持一致。</p>
     */
    private int maxSwell;

    /**
     * 爆炸半径。原版苦力怕 = 3。
     *
     * <p>默认值来自配置 {@code waterCreeper.explosion.explosionPower}，在构造时写入；
     * 之后的读档（NBT {@code ExplosionRadius}）可以按只覆盖它。</p>
     */
    private int explosionRadius;

    /** 撞船冷却。防止一帧内把船和玩家连撞十几次。 */
    private int ramCooldown;

    public WaterCreeperEntity(EntityType<? extends WaterCreeperEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;

        // 引信与爆炸半径的默认值都来自配置 —— 这样改配置不需要改代码。
        // （存档里的旧个体会用自己的 NBT 值覆盖，见两个字段的注释。）
        this.maxSwell = HybridCreeperConfig.WATER_CREEPER_FUSE_TICKS.get();
        this.explosionRadius = (int) Math.round(HybridCreeperConfig.WATER_CREEPER_EXPLOSION_POWER.get());

        // 属性数值同样来自配置，**必须在这里读**：
        // 构造函数是"实体生成时"执行的，配置早已加载完；
        // 而 createAttributes() 跑在配置加载之前，在那里读会直接崩游戏（见该方法的注释）。
        this.applyConfiguredAttributes();

        // 移动与视线：直接采用原版海豚那一套参数（85 / 10 是转向上限，
        // 0.02F 是水中加速度，0.1F 是出水后的加速度，最后一项开启重力辅助）。
        // 海豚是原版唯一"高速追船"的生物，它的手感参数不值得我另起炉灶。
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
    }

    /**
     * 把配置里的属性数值真正写进这只生物 —— 由构造函数调用（即<b>实体生成时</b>）。
     *
     * <p>{@link #createAttributes()} 里的常数只是"出厂默认值"，配置的真值在这里覆盖。
     * 之所以拆成两步，是因为属性表创建得太早（早于配置加载），见
     * {@link #createAttributes()} 的详细说明。</p>
     *
     * <p><b>副作用（已在配置文件的注释里告知用户）</b>：改配置只对之后新生成的个体生效，
     * 存档里已有的个体保持它自己被同步/存档的数值 —— 属性是每个实体私有的，这里不做回扫。
     * 想立刻看到效果，重新刷一只即可。</p>
     */
    private void applyConfiguredAttributes() {
        this.applyAttribute(Attributes.MAX_HEALTH,           HybridCreeperConfig.WATER_CREEPER_MAX_HEALTH.get());
        this.applyAttribute(Attributes.MOVEMENT_SPEED,       HybridCreeperConfig.WATER_CREEPER_MOVEMENT_SPEED.get());
        this.applyAttribute(Attributes.ATTACK_DAMAGE,        HybridCreeperConfig.WATER_CREEPER_ATTACK_DAMAGE.get());
        this.applyAttribute(Attributes.FOLLOW_RANGE,         HybridCreeperConfig.WATER_CREEPER_FOLLOW_RANGE.get());
        this.applyAttribute(Attributes.KNOCKBACK_RESISTANCE, HybridCreeperConfig.WATER_CREEPER_KNOCKBACK_RESISTANCE.get());
        // 水里速度 = 0.02 × 本值（倍数语义见 createAttributes 里的注释）
        this.applyAttribute(NeoForgeMod.SWIM_SPEED,          HybridCreeperConfig.WATER_CREEPER_SWIM_SPEED.get());

        // 生命上限被改写后要重新回满 ——
        // 父类构造里那次 setHealth(getMaxHealth()) 用的还是属性表里的默认上限，已经过时了。
        this.setHealth(this.getMaxHealth());
    }

    /**
     * 改单条属性的基值。
     *
     * <p>{@code getAttribute} 正常情况下不会返回 null（属性表里都注册过），
     * 这里仍然判空：万一将来有人删掉某条属性，也只是那一条不生效，
     * 而不是让整只生物生成失败。</p>
     */
    private void applyAttribute(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /* ------------------------------------------------------------------
     * 闪电充能（对齐原版 Creeper）
     * ------------------------------------------------------------------ */

    @Override
    public boolean isPowered() {
        return this.entityData.get(DATA_POWERED);
    }

    @Override
    public void setPowered(boolean powered) {
        this.entityData.set(DATA_POWERED, powered);
    }

    // 充能状态的读写统一放在下面的「存档」一节 —— 那里已经有
    // addAdditionalSaveData / readAdditionalSaveData 在处理引信与爆炸半径。
    // 一个类只能有一对这两个方法，各写一份会以「已在类中定义」编译失败。

    /**
     * 被闪电劈中时充能。
     *
     * <p>{@code super.thunderHit} 必须调 —— 它负责原版雷电的点燃与伤害。
     * 具体链路见 {@code HybridCreeperEntity#thunderHit} 的注释。</p>
     *
     * <p>在水里被劈中的情况说明一下：{@code super} 会给它点火，
     * 但它在水里，火会立刻被浇灭，所以实际只剩"充能 + 雷电伤害"。
     * 这个结果是合理的，不需要特殊处理。</p>
     */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        super.thunderHit(level, bolt);
        this.setPowered(true);
    }

    /* ------------------------------------------------------------------
     * 属性
     * ------------------------------------------------------------------ */

    /**
     * 属性表 —— <b>这里只能写死常数，绝对不能读配置</b>。
     *
     * <h2>为什么不能读配置（v1.9.0 的启动崩溃事故）</h2>
     * <p>本方法是在 {@code EntityAttributeCreationEvent} 里被调用的，而那个事件由
     * {@code GameData#postRegisterEvents → CommonHooks#modifyAttributes} 在
     * <b>注册表后期</b>抛出 —— <b>比 {@code ModConfigSpec} 加载更早</b>。
     * 在这里读 {@code HybridCreeperConfig} 会抛：</p>
     * <pre>
     *   java.lang.IllegalStateException: Cannot get config value before config is loaded.
     *     at ModConfigSpec$ConfigValue.get
     *     at WaterCreeperEntity.createAttributes
     *     at HybridCreeper.onEntityAttributeCreation
     * </pre>
     * <p>后果不是"这一项不生效"，而是<b>模组状态直接被打成 broken</b>、
     * 游戏在启动阶段就崩（日志里满屏 {@code Cowardly refusing to send event … to a broken mod state}）。</p>
     *
     * <p>所以配置里的真值改由 {@link #applyConfiguredAttributes()} 在<b>实体生成时</b>写入 ——
     * 那时配置早已就绪。下表的常数必须与配置默认值<b>保持一致</b>。</p>
     *
     * <table border="1">
     *   <tr><th>属性</th><th>默认</th><th>配置项</th><th>参照</th></tr>
     *   <tr><td>MAX_HEALTH</td><td>20</td><td>{@code maxHealth}</td><td>与苦力怕一致</td></tr>
     *   <tr><td>MOVEMENT_SPEED</td><td><b>1.5</b></td><td>{@code movementSpeed}</td>
     *       <td>海豚 1.2、玩家疾跑 0.13、僵尸 0.23。<b>管陆地</b>（含上岸突袭）</td></tr>
     *   <tr><td>ATTACK_DAMAGE</td><td>4</td><td>{@code attackDamage}</td><td>撞击伤害（爆炸另算）</td></tr>
     *   <tr><td>FOLLOW_RANGE</td><td>32</td><td>{@code followRange}</td><td>主动索敌距离</td></tr>
     *   <tr><td>KNOCKBACK_RESISTANCE</td><td>0.3</td><td>{@code knockbackResistance}</td>
     *       <td>免得被自己的爆炸掀得满海乱飞</td></tr>
     *   <tr><td>SWIM_SPEED<small>（NeoForge 扩展）</small></td><td><b>7.5</b></td>
     *       <td>{@code swimSpeed}</td>
     *       <td><b>管水里</b>。水里速度 = 0.02 × 本值，与 MOVEMENT_SPEED 无关</td></tr>
     * </table>
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 1.5)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                // 游泳速度倍率（NeoForge 标准扩展属性，默认 1.0）。
                // 关键：LivingEntity.travel 在水里把速度系数先锁死成硬编码的 0.02，
                // 只有这个属性（或 WATER_MOVEMENT_EFFICIENCY）能把它放大；
                // 否则 SmoothSwimmingMoveControl 设的速度、MOVEMENT_SPEED 全被无视，
                // 结果就是"在水里几乎不动"。
                // 7.5 倍 ≈ 0.15 格/tick（约 3 格/秒）—— 原为 15 倍，主人反馈"移速太快"故减半。
                // 注意：这只影响水里；陆地/登陆突袭走的是上面的 MOVEMENT_SPEED。
                .add(NeoForgeMod.SWIM_SPEED, 7.5);
    }

    /**
     * 充能后的爆炸半径倍率 —— 改成读配置（{@code waterCreeper.explosion.chargedMultiplier}）。
     *
     * <p>{@link PoweredMob} 里的默认实现是返回写死的 2.0F（对齐原版苦力怕），
     * 这里覆写它只是为了让这个数值也能被调。</p>
     */
    @Override
    public float explosionRadiusMultiplier() {
        return this.isPowered()
                ? HybridCreeperConfig.WATER_CREEPER_CHARGED_MULTIPLIER.get().floatValue()
                : 1.0F;
    }

    /* ------------------------------------------------------------------
     * 移动层
     * ------------------------------------------------------------------ */

    /**
     * 水中寻路。
     *
     * <p>{@code WaterBoundPathNavigation} 只在<b>水中</b>寻路（它的
     * {@code canUpdatePath} 会检查 {@code isInWater}），这正是我们要的：
     * 它不会跑到岸上去，想上岸只能靠 {@code WaterCreeperBeachAssaultGoal} 那一次跳跃。</p>
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    /**
     * 不被流体推动 —— 与海豚一致，否则它会被洋流冲得失去方向。
     *
     * <p>注意这里覆写的是<b>带 {@code FluidType} 参数</b>的那个重载。无参的
     * {@code Entity#isPushedByFluid()} 已被 NeoForge 标记为过时
     * （{@code @Deprecated // Forge: Use FluidType sensitive version}），
     * 而它的替代版在自己的默认实现里仍然会回头调用无参版
     * （{@code self().isPushedByFluid() && type.canPushEntity(self())}）——
     * 所以覆写新版本是一步到位，也顺手消掉了编译警告。</p>
     */
    @Override
    public boolean isPushedByFluid(FluidType type) {
        return false;
    }

    /**
     * 允许"身体浸在液体里"生成 —— <b>水生生物自然生成的关键一环</b>。
     *
     * <h2>不覆写会怎样（这就是 2026-09-24 那个"一条都不刷"的根因）</h2>
     * <p>自然生成的最后一道闸门是 {@code EventHooks#checkSpawnPosition}
     * （NeoForge），它默认走：</p>
     * <pre>
     *   mob.checkSpawnRules(level, spawnType) &amp;&amp; mob.checkSpawnObstruction(level)
     * </pre>
     * <p>而 {@code Mob#checkSpawnObstruction} 的默认实现是</p>
     * <pre>
     *   return !level.containsAnyLiquid(this.getBoundingBox()) &amp;&amp; level.isUnobstructed(this);
     * </pre>
     * <p>—— <b>"身体包围盒里不能有液体"</b>。这是给陆地生物准备的判定：它们确实该在干燥处落地。
     * 我们的生物泡在水里，这一条<b>永远为假</b>，于是前面所有条件（位置类型
     * {@code IN_WATER}、生物群系权重、附加谓词、{@code noCollision}）全部通过，
     * 最后仍然被这一句否掉 —— 表现为"生成表里有它，世界里一条都没有"。</p>
     *
     * <h2>为什么这么写</h2>
     * <p>原版每一个"在水里刷"的生物都覆写了本方法，且实现完全一致 ——
     * {@link net.minecraft.world.entity.animal.WaterAnimal}（墨鱼/海豚/鱼）、
     * {@link net.minecraft.world.entity.monster.Drowned}（<b>同样是 {@code Monster}，
     * 同样用 {@code IN_WATER} + {@code MONSTER/WATER_CREATURE} 生成</b>）、
     * {@code Guardian}、{@code Axolotl}、{@code Strider} 都是这一句：</p>
     * <pre>
     *   return level.isUnobstructed(this);
     * </pre>
     * <p>只保留"别卡在方块里"，去掉"不能有液体"。本方法逐字对齐它们，
     * 所以溺尸能刷的水域，它也能刷。</p>
     *
     * <p>注意：{@code Monster#getMobType()} 返回 {@code UNDEAD}，所以它和溺尸一样
     * 会溺水 —— 这个由 {@code data/minecraft/tags/entity_type/can_breathe_under_water.json}
     * 把本实体加进 {@code EntityTypeTags.CAN_BREATHE_UNDER_WATER} 解决
     * （{@code LivingEntity#canBreatheUnderwater()} 是 final 的，只能走标签）。</p>
     */
    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /* ------------------------------------------------------------------
     * AI
     * ------------------------------------------------------------------ */

    @Override
    protected void registerGoals() {
        // 1. 引信：贴脸就开始膨胀
        this.goalSelector.addGoal(1, new WaterCreeperSwellGoal(this));
        // 2. 上岸突袭：玩家缩在岸上时跳出去自爆
        this.goalSelector.addGoal(2, new WaterCreeperBeachAssaultGoal(this));
        // 3. 追击 + 撞船
        this.goalSelector.addGoal(3, new WaterCreeperChaseGoal(this));
        // 4. 没事时随机游动
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 10));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        // 主动锁定玩家
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    /* ------------------------------------------------------------------
     * 引信：每 tick 的推进
     * ------------------------------------------------------------------ */

    @Override
    public void tick() {
        if (this.isAlive()) {
            this.oldSwell = this.swell;

            int dir = this.getSwellDir();
            if (dir > 0 && this.swell == 0) {
                // 点燃引信的那一瞬间：播放苦力怕的"嘶"声（音高更低，像从水底传来）
                this.playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F);
                this.gameEvent(net.minecraft.world.level.gameevent.GameEvent.PRIME_FUSE);
            }

            this.swell += dir;
            if (this.swell < 0) {
                this.swell = 0;
            }

            if (this.swell >= this.maxSwell) {
                this.swell = this.maxSwell;
                this.explode();
            }
        }

        if (this.ramCooldown > 0) {
            this.ramCooldown--;
        }

        super.tick();
    }

    /**
     * 落地上岸之后立刻引爆。
     *
     * <p>这是"岸上攻击"的收尾：{@code WaterCreeperBeachAssaultGoal} 负责把它从水里抛出来，
     * 这里负责在它碰到陆地的那一刻结算 —— 玩家没有时间反应，这正是需求里
     * "跳上岸随后立即爆炸"要的效果。</p>
     *
     * <p>判据是 {@code onGround()} 且不在水里。腾空过程中不会误触发。</p>
     */
    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide
                && this.isAlive()
                && this.isBeachAssaulting()
                && this.onGround()
                && !this.isInWater()) {
            // 已经上岸站稳了，直接引爆（不必再等引信走完）
            this.explode();
        }
    }

    /* ------------------------------------------------------------------
     * 引信 / 爆炸状态
     * ------------------------------------------------------------------ */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SWELL_DIR, -1);
        builder.define(DATA_BEACH_ASSAULT, false);
        builder.define(DATA_POWERED, false);
    }

    public int getSwellDir() {
        return this.entityData.get(DATA_SWELL_DIR);
    }

    public void setSwellDir(int dir) {
        this.entityData.set(DATA_SWELL_DIR, dir);
    }

    /** 供渲染器做膨胀动画：0 = 原始大小，1 = 即将爆炸。 */
    public float getSwelling(float partialTick) {
        return Mth.lerp(partialTick, (float) this.oldSwell, (float) this.swell) / (float) (this.maxSwell - 2);
    }

    public boolean isBeachAssaulting() {
        return this.entityData.get(DATA_BEACH_ASSAULT);
    }

    public void setBeachAssaulting(boolean value) {
        this.entityData.set(DATA_BEACH_ASSAULT, value);
    }

    /**
     * 引爆。
     *
     * <p>结构逐字对标 {@code Creeper#explodeCreeper()}：先把自己标记为 dead，
     * 再在自身位置引发一次爆炸，最后 {@code discard()}。数值<b>全部来自配置</b>
     * {@code [waterCreeper.explosion]}：</p>
     * <ul>
     *   <li>{@code explosionPower} —— 半径（默认 3，与苦力怕一致）；</li>
     *   <li>{@code destroyBlocks} —— 决定用 {@code ExplosionInteraction.MOB}
     *       （受 {@code mobGriefing} 约束）还是 {@code NONE}（只伤实体，地形无损）；</li>
     *   <li>{@code setFire} —— 是否引燃；</li>
     *   <li>{@code damageMultiplier} —— 交给
     *       {@link HybridCreeperExplosionCalculator} 缩放伤害；</li>
     *   <li>{@code chargedMultiplier} —— 充能（被闪电劈中）时的半径倍率，默认 2.0。</li>
     * </ul>
     *
     * <p>{@code waterCreeper.explode = false} 时直接返回：既不炸也不自毁，
     * 它退化成"只会撞船与近战撕咬"的生物（此时引信与上岸突袭也会自动停工，
     * 见两个 goal 里的判断）。</p>
     */
    public void explode() {
        if (this.level().isClientSide) {
            return;
        }
        if (!HybridCreeperConfig.WATER_CREEPER_EXPLODE.get()) {
            return;
        }

        boolean destroyBlocks = HybridCreeperConfig.WATER_CREEPER_DESTROY_BLOCKS.get();

        HybridCreeperExplosionCalculator calculator = new HybridCreeperExplosionCalculator(
                destroyBlocks,
                // 引爆即自毁，不需要"免疫自己的爆炸"
                false,
                this,
                // 没有"额外照顾"的目标（苦力怕海豚没有幻翼那种"咬中谁额外罚谁"的机制）
                null,
                HybridCreeperConfig.WATER_CREEPER_DAMAGE_MULTIPLIER.get(),
                1.0D);

        // 破坏方块 = MOB：与苦力怕完全一致，受 mobGriefing 游戏规则约束
        // 不破坏方块 = NONE：保留对实体的伤害与击退，但地形无损
        Level.ExplosionInteraction interaction = destroyBlocks
                ? Level.ExplosionInteraction.MOB
                : Level.ExplosionInteraction.NONE;

        this.dead = true;
        this.level().explode(
                /* source           */ this,
                /* damageSource     */ null,        // 交回原版推导（等价于旧的 explode(this, ...) 写法）
                /* damageCalculator */ calculator,
                /* x, y, z          */ this.getX(), this.getY(), this.getZ(),
                // 充能时半径翻倍。等价于 Creeper#explodeCreeper 里的
                //   float f = this.isPowered() ? 2.0F : 1.0F;
                //   explode(..., (float)this.explosionRadius * f, ...);
                /* radius           */ (float) this.explosionRadius * this.explosionRadiusMultiplier(),
                /* fire             */ HybridCreeperConfig.WATER_CREEPER_SET_FIRE.get(),
                /* interaction      */ interaction);
        this.triggerOnDeathMobEffects(Entity.RemovalReason.KILLED);
        this.discard();
    }

    /**
     * 撞击行为 —— 给船上的玩家造成伤害并把船撞偏。
     *
     * <p>"打断玩家的行动"在这里的落点是：<b>船只被撞得偏离航向</b>。
     * 原版船的操控依赖持续的划桨输入，任何横向冲量都会让它打转，
     * 想直线逃离就得重新调整朝向 —— 这在水里、在它 1.5 的速度面前基本没戏。</p>
     *
     * @return 是否真的撞到了东西（撞到了才会进入冷却）
     */
    /**
     * 当前是否允许引爆 —— 供两个 AI goal 判断要不要起用。
     *
     * <p>配置里关掉爆炸后，引信与"跳上岸自爆"都失去意义，所以一起停工。</p>
     */
    public boolean isExplosionEnabled() {
        return HybridCreeperConfig.WATER_CREEPER_EXPLODE.get();
    }

    public boolean ram(Entity target) {
        if (this.ramCooldown > 0) {
            return false;
        }
        this.ramCooldown = HybridCreeperConfig.WATER_CREEPER_RAM_COOLDOWN_TICKS.get();

        // 对目标本体造成伤害（玩家在船上时，伤害算在玩家头上）
        this.doHurtTarget(target);

        // 如果目标是船，额外给一个横向冲量，把它撞歪
        Entity vehicle = target.getVehicle();
        if (vehicle instanceof Boat boat && target instanceof Player player) {
            // 玩家在船上：伤害算玩家，冲量给船
            this.doHurtTarget(player);

            double dx = boat.getX() - this.getX();
            double dz = boat.getZ() - this.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0E-4) {
                // 朝"船被推离生物"的方向施力，强度与生物自己的速度挂钩
                double power = Math.min(0.55, 0.25 + this.getDeltaMovement().length() * 0.35);
                boat.push(dx / len * power, 0.12, dz / len * power);
                boat.hurtMarked = true;
            }
        }

        return true;
    }

    /* ------------------------------------------------------------------
     * 存档
     * ------------------------------------------------------------------ */

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        // 充能状态。键名 "powered" 与原版 Creeper 完全一致，
        // 这样用 /data get entity 查看实体 NBT 时和原版苦力怕长得一样。
        // 只在已充能时才写 —— 原版也是这么省字节的（读的时候缺键 getBoolean 返回 false）。
        if (this.isPowered()) {
            tag.putBoolean("powered", true);
        }

        tag.putShort("Fuse", (short) this.maxSwell);
        tag.putByte("ExplosionRadius", (byte) this.explosionRadius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setPowered(tag.getBoolean("powered"));
        if (tag.contains("Fuse", 99)) {
            this.maxSwell = tag.getShort("Fuse");
        }
        if (tag.contains("ExplosionRadius", 99)) {
            this.explosionRadius = tag.getByte("ExplosionRadius");
        }
    }

    /* ------------------------------------------------------------------
     * 音效 / 杂项
     * ------------------------------------------------------------------ */

    // 注意：这里刻意【不】覆写 getAmbientSound()。
    // 原版苦力怕没有任何环境音（它只会"嘶"），SoundEvents 里也确实没有
    // CREEPER_AMBIENT 这个常量 —— 父类默认返回 null 才是正确的行为。

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CREEPER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CREEPER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    /** 和平难度下消失 —— 与其它敌对生物一致。 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    /**
     * 它不会用普通近战打人，伤害全靠撞击和爆炸。
     *
     * <p>覆写 {@code doHurtTarget} 返回 false 是刻意的：原版苦力怕也这么做
     * （见 {@code Creeper#doHurtTarget}）。真正造成伤害的是我们在
     * {@link #ram(Entity)} 里显式调用的那两次。</p>
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        // 这里走真正的 Mob 实现（父类），因为我们确实要靠撞击造成伤害。
        return super.doHurtTarget(target);
    }

    /** 让船上的玩家也会被锁定为攻击目标（默认只锁定"站在地上"的玩家）。 */
    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type == EntityType.PLAYER;
    }
}
