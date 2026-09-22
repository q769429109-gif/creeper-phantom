package com.phantomblast.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 幻翼爆破专用爆炸参数计算器。
 *
 * <p>原版 {@link ExplosionDamageCalculator} 是所有爆炸的"参数中枢"，
 * {@code Explosion#explode()} 在遍历周围实体时依次调用：</p>
 * <pre>
 *   if (damageCalculator.shouldDamageEntity(explosion, entity)) {
 *       entity.hurt(damageSource, damageCalculator.getEntityDamageAmount(explosion, entity));
 *   }
 *   double knock = ... * damageCalculator.getKnockbackMultiplier(entity);   // 与上面是并列的，不受 shouldDamageEntity 影响
 * </pre>
 *
 * <p>注意最后一行：<b>击退永远会执行</b>，所以"让苦力怕幻翼免疫自己的爆炸"必须同时重写
 * {@link #shouldDamageEntity}（免伤）和 {@link #getKnockbackMultiplier}（免击退），
 * 只重写前者的话苦力怕幻翼还是会被自己的冲击波掀飞。</p>
 */
public class PhantomBlastExplosionCalculator extends ExplosionDamageCalculator {

    /** 是否破坏方块 */
    private final boolean destroyBlocks;
    /** 苦力怕幻翼是否免疫自己引发的爆炸 */
    private final boolean phantomImmune;
    /** 本次爆炸的元凶（苦力怕幻翼）；理论上不为 null */
    private final Entity phantom;
    /** 本次爆炸中被咬中的那名玩家，用于施加额外伤害倍率 */
    private final Entity hitTarget;
    /** 全局伤害倍率 */
    private final double damageMultiplier;
    /** 额外作用于被咬中玩家的伤害倍率 */
    private final double hitTargetDamageMultiplier;

    public PhantomBlastExplosionCalculator(boolean destroyBlocks,
                                           boolean phantomImmune,
                                           Entity phantom,
                                           Entity hitTarget,
                                           double damageMultiplier,
                                           double hitTargetDamageMultiplier) {
        this.destroyBlocks = destroyBlocks;
        this.phantomImmune = phantomImmune;
        this.phantom = phantom;
        this.hitTarget = hitTarget;
        this.damageMultiplier = damageMultiplier;
        this.hitTargetDamageMultiplier = hitTargetDamageMultiplier;
    }

    /** 是否可以破坏这个方块。等价于 {@code Level.ExplosionInteraction.BLOCK/MOB} 与 {@code NONE} 的区别。 */
    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
        return this.destroyBlocks;
    }

    /** 该实体是否吃这次爆炸的伤害。 */
    @Override
    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        // 苦力怕幻翼豁免自己的爆炸：否则威力 3.0 的近身爆炸会把 20 血、无爆炸抗性的苦力怕幻翼直接炸死。
        return !(this.phantomImmune && entity == this.phantom);
    }

    /** 该实体实际承受的爆炸伤害。原版公式：{@code ((d²+d)/2 * 7 * (radius*2) + 1)}，d 为归一化距离。 */
    @Override
    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
        float base = super.getEntityDamageAmount(explosion, entity);
        double multiplier = this.damageMultiplier;
        if (entity == this.hitTarget) {
            multiplier *= this.hitTargetDamageMultiplier;
        }
        return (float) (base * multiplier);
    }

    /** 击退倍率。返回 0 即完全不被这次爆炸推动。 */
    @Override
    public float getKnockbackMultiplier(Entity entity) {
        if (this.phantomImmune && entity == this.phantom) {
            return 0.0F;
        }
        return super.getKnockbackMultiplier(entity);
    }
}
