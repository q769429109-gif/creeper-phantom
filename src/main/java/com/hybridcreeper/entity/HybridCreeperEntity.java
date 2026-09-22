package com.hybridcreeper.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;

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
public class HybridCreeperEntity extends Phantom {

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

    /**
     * 掉落表复用原版幻翼的（幻翼膜）。
     *
     * <p>默认情况下每个 {@code EntityType} 会去找
     * {@code data/<命名空间>/loot_table/entities/<id>.json}，也就是
     * {@code hybridcreeper:entities/blast_phantom} —— 那个文件不存在，掉落就会是空的。
     * 与其复制一份掉落表 JSON 出来单独维护（原版一改我们就过期），
     * 不如直接把 {@code PHANTOM} 的掉落表 key 拿过来用，<b>永远跟随原版</b>。</p>
     *
     * <p><b>注意这里覆写的是 {@code getDefaultLootTable()} 而不是 {@code getLootTable()}：</b>
     * 后者在 {@code Mob} 里是 {@code final} 的，编译不过。
     * {@code Mob#getLootTable()} 的实现是
     * {@code return this.lootTable == null ? this.getDefaultLootTable() : this.lootTable;}
     * —— 「默认掉落表」这个钩子才是留给子类用的，而 {@code lootTable} 字段那条分支
     * 是给 {@code /data merge} 或结构文件直接指定掉落表用的。</p>
     *
     * <p>反过来说：哪天你想让苦力怕幻翼掉别的东西，覆写这个方法返回自己的
     * {@code ResourceKey} 即可；或者干脆在数据包里放一份
     * {@code data/hybridcreeper/loot_table/entities/blast_phantom.json} 并把这里删掉。</p>
     */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return EntityType.PHANTOM.getDefaultLootTable();
    }
}
