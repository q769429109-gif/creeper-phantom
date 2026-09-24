package com.hybridcreeper.explosion;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 杂交苦力怕通用爆炸参数计算器（<b>两只生物共用</b>：苦力怕幻翼的俯冲爆炸、
 * 苦力怕海豚的自爆）。
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
 * <p>注意最后一行：<b>击退永远会执行</b>，所以"让某只生物免疫自己的爆炸"必须同时重写
 * {@link #shouldDamageEntity}（免伤）和 {@link #getKnockbackMultiplier}（免击退），
 * 只重写前者的话它还是会被自己的冲击波掀飞。</p>
 *
 * <h2>两个使用者的差异</h2>
 * <ul>
 *   <li><b>苦力怕幻翼</b>：炸完要活着继续盘旋，所以传 {@code immuneToOwnExplosion = true}、
 *       并且有"额外打了被咬中那名玩家"的 {@code hitTarget}；</li>
 *   <li><b>苦力怕海豚</b>：引爆即自毁（与苦力怕一致），所以
 *       {@code immuneToOwnExplosion = false}、{@code hitTarget = null} ——
 *       它的爆炸只由 {@link #damageMultiplier} 调参。</li>
 * </ul>
 */
public class HybridCreeperExplosionCalculator extends ExplosionDamageCalculator {

    /** 是否破坏方块 */
    private final boolean destroyBlocks;
    /** 爆炸的发起者是否免疫自己引发的爆炸 */
    private final boolean immuneToOwnExplosion;
    /** 本次爆炸的发起者；理论上不为 null */
    private final Entity explosionSource;
    /** 本次爆炸中"被额外照顾"的目标（苦力怕幻翼咬中的那名玩家），可为 null */
    private final Entity hitTarget;
    /** 全局伤害倍率 */
    private final double damageMultiplier;
    /** 额外作用于 {@link #hitTarget} 的伤害倍率 */
    private final double hitTargetDamageMultiplier;

    public HybridCreeperExplosionCalculator(boolean destroyBlocks,
                                           boolean immuneToOwnExplosion,
                                           Entity explosionSource,
                                           @Nullable Entity hitTarget,
                                           double damageMultiplier,
                                           double hitTargetDamageMultiplier) {
        this.destroyBlocks = destroyBlocks;
        this.immuneToOwnExplosion = immuneToOwnExplosion;
        this.explosionSource = explosionSource;
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
        // 发起者豁免自己的爆炸：否则威力 3.0 的近身爆炸会把 20 血、无爆炸抗性的它直接炸死。
        return !(this.immuneToOwnExplosion && entity == this.explosionSource);
    }

    /** 该实体实际承受的爆炸伤害。原版公式：{@code ((d²+d)/2 * 7 * (radius*2) + 1)}，d 为归一化距离。 */
    @Override
    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
        float base = super.getEntityDamageAmount(explosion, entity);
        double multiplier = this.damageMultiplier;
        if (this.hitTarget != null && entity == this.hitTarget) {
            multiplier *= this.hitTargetDamageMultiplier;
        }
        return (float) (base * multiplier);
    }

    /** 击退倍率。返回 0 即完全不被这次爆炸推动。 */
    @Override
    public float getKnockbackMultiplier(Entity entity) {
        if (this.immuneToOwnExplosion && entity == this.explosionSource) {
            return 0.0F;
        }
        return super.getKnockbackMultiplier(entity);
    }
}
