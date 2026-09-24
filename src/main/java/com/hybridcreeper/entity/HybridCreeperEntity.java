package com.hybridcreeper.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;

/**
 * 苦力怕幻翼 —— 独立于原版幻翼的自定义生物。
 *
 * <h2>为什么是 {@code extends Phantom} 而不是从 {@code Monster} 重写</h2>
 * <p>原版幻翼的全部行为都写在 {@code Phantom} 的<b>包私有内部类</b>里：
 * {@code PhantomAttackPlayerTargetGoal}（锁定玩家，64 格）、
 * {@code PhantomAttackStrategyGoal}（盘旋 → 嘶吼 → 俯冲，8~12 秒一轮）、
 * {@code PhantomSweepAttackGoal}（俯冲撕咬）、
 * {@code PhantomCircleAroundAnchorGoal}（绕锚点盘旋）、
 * {@code PhantomMoveControl} / {@code PhantomLookControl} / {@code PhantomBodyRotationControl}
 * （飞行操控手感），以及 {@code PhantomSweepAttackGoal} 里的"怕猫"逻辑。</p>
 *
 * <p>这些内部类全部引用 {@code Phantom.this}，而它们是在 {@code Phantom#registerGoals}
 * 里实例化的。{@code registerGoals} 是 {@code protected}，所以<b>只要继承并且不覆写它，
 * 子类就自动拿到一整套与原版逐字节相同的行为</b>——不需要把上千行 AI 逻辑抄一遍，
 * 也不需要 Mixin 或访问转换器。</p>
 *
 * <p>子类唯一"看不见"的东西是 {@code attackPhase} / {@code anchorPoint} / {@code moveTargetPoint}
 * 这几个包私有字段。但那些字段的读写全部发生在幻翼自己的内部类里，
 * 我们不需要碰——这正是继承方案干净的地方。</p>
 *
 * <h2>与原版幻翼的差异（有且仅有这些）</h2>
 * <ol>
 *   <li>是个<E>b</b>不同的 {@code EntityType}（注册名 {@code hybridcreeper:creeperphantom}），
 *       所以它和原版幻翼可以同时存在于同一个世界、互不干扰。</li>
 *   <li>俯冲撕咬命中玩家时会引爆苦力怕级爆炸（逻辑在
 *       {@code com.hybridcreeper.explosion.SwoopExplosionHandler}）。</li>
 *   <li>用了自己的模型与贴图（客户端 {@code com.hybridcreeper.client} 包）。</li>
 * </ol>
 *
 * <p>除此之外——属性、生命、伤害、掉落、音效、粒子、被阳光点燃、和平模式消失、
 * 体型随机缩放、怕猫——<b>全部与幻翼一致</b>，因为它们都是继承来的。</p>
 */
public class HybridCreeperEntity extends Phantom implements PoweredMob {

    /**
     * 充能状态（被闪电劈中）。
     *
     * <p>用同步数据而不是普通字段 —— 充能要<b>在客户端可见</b>：
     * 那个蓝色能量层全靠这个布尔值决定画不画。
     * 原版 {@code Creeper} 的 {@code DATA_IS_POWERED} 就是同样的做法。</p>
     *
     * <p>{@code defineId} 靠一个静态自增计数器分配 id，所以同一个类里只能定义一次，
     * 也不能和父类 {@code Phantom} 的 {@code ID_SIZE} 混用 —— 那一个是在
     * {@code Phantom} 类里用 {@code Phantom.class} 注册的，id 已经占掉了。</p>
     */
    private static final EntityDataAccessor<Boolean> DATA_POWERED =
            SynchedEntityData.defineId(HybridCreeperEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * 构造函数签名必须与 {@code Phantom} 对齐，才能被 {@code EntityType.Builder.of(...)}
     * 当作工厂方法引用。</p>
     *
     * <p>泛型参数是 {@code EntityType<? extends Phantom>} 而不是
     * {@code EntityType<? extends HybridCreeperEntity>} —— 因为 {@code super(...)}
     * 只接受前者，而 {@code EntityType<HybridCreeperEntity>} 同时满足两者，
     * 方法引用依然可以被 {@code EntityFactory<HybridCreeperEntity>} 接受。</p>
     */
    public HybridCreeperEntity(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
    }

    /* ==================================================================
     * 闪电充能（对齐原版 Creeper）
     * ================================================================== */

    /**
     * 注册同步数据。必须调 {@code super} —— {@code Phantom} 在这里定义了它的体型
     * （{@code ID_SIZE}），漏掉会让幻翼的随机体型功能直接失灵。
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_POWERED, false);
    }

    /** 是否处于充能状态。来自 {@code PowerableMob}，也是渲染层判断画不画能量层的依据。 */
    @Override
    public boolean isPowered() {
        return this.entityData.get(DATA_POWERED);
    }

    @Override
    public void setPowered(boolean powered) {
        this.entityData.set(DATA_POWERED, powered);
    }

    /**
     * 存档时记下充能状态。
     *
     * <p>原版 {@code Creeper} 只在<b>已充能</b>时才写这个键
     * （{@code if (powered) tag.putBoolean("powered", true);}）——
     * 这是刻意的省字节：没充能就不写，读的时候 {@code getBoolean} 缺键返回 false。
     * 这里照抄，连键名 {@code "powered"} 都保持一致，
     * 这样用 {@code /data} 查看实体 NBT 时和原版苦力怕长得一样。</p>
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.isPowered()) {
            tag.putBoolean("powered", true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setPowered(tag.getBoolean("powered"));
    }

    /**
     * 被闪电劈中时充能。
     *
     * <p>触发链路：{@code LightningBolt} 每 tick 找出范围内的实体，
     * 对每一个调用 {@code entity.thunderHit(level, bolt)}。
     * 原版苦力怕就是在这一步把自己设成充能的，这里完全照抄。</p>
     *
     * <p><b>{@code super.thunderHit} 不能省</b> —— 它负责原版雷电该做的事：
     * 点燃（{@code igniteForSeconds(8.0F)}）和造成雷电伤害。
     * 省掉的话闪电劈中它连伤害都没有，那就不是"和原版一样"了。</p>
     *
     * <p>顺带一提，充能是<b>永久且不可逆</b>的 —— 原版苦力怕被劈过就一直是充能状态，
     * 淋雨、泡水、睡觉都不会掉。这里保持一致。</p>
     */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        super.thunderHit(level, bolt);
        this.setPowered(true);
    }

    /*
     * 掉落表：从 v1.10.0 起改用本模组自己的表
     *   data/hybridcreeper/loot_table/entities/creeperphantom.json
     * 内容是「幻翼膜 + 火药」。
     *
     * 这里原本覆写 getDefaultLootTable() 直接借用原版幻翼的掉落表（只掉幻翼膜）；
     * 要加火药就必须有自己的表，所以那个覆写已经删掉，改为走 Minecraft 的默认约定 ——
     * EntityType#getDefaultLootTable() 会把【注册名】加上前缀 "entities/"：
     *     hybridcreeper:creeperphantom  →  hybridcreeper:entities/creeperphantom
     * 与上面的文件名一一对应。
     *
     * ⚠️ 注册名是 creeperphantom（**没有下划线**），改表名时务必同步文件名 ——
     *    数据包里的 loot_table 一旦缺失不会报错，只是掉落静默变空，很难查。
     *
     * 顺带记：能覆写的是 getDefaultLootTable()，getLootTable() 在 Mob 里是 final 的。
     */
}
