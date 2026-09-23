package com.hybridcreeper.entity;

import net.minecraft.world.entity.PowerableMob;

/**
 * 「能被闪电充能」的生物。
 *
 * <h2>为什么继承 {@link PowerableMob} 而不是自己造一个</h2>
 * <p>{@code PowerableMob} 是原版就有的标记接口，整个接口只有一个方法：</p>
 * <pre>
 *   public interface PowerableMob {
 *       boolean isPowered();
 *   }
 * </pre>
 * <p>原版 {@code Creeper}（苦力怕）和 {@code WitherBoss}（凋灵）都实现了它。
 * 继承它的好处是<b>能直接复用原版的能量层渲染器</b> ——
 * {@code EnergySwirlLayer} 的泛型约束正是
 * {@code <T extends Entity & PowerableMob, M extends EntityModel<T>>}。
 * （我们最终没用那个类，原因见 {@code com.hybridcreeper.client.PoweredOverlayLayer} 的注释，
 * 但接口选它仍然是对的：语义一致，将来想换回原版渲染器也不用改实体。）</p>
 *
 * <h2>为什么充能状态不在这里实现</h2>
 * <p>同步数据访问器（{@code EntityDataAccessor}）必须用
 * {@code SynchedEntityData.defineId(实体类.class, 序列化器)} 逐类定义：
 * 它靠一个静态自增计数器分配 id，同一个类里定义两次就会撞 id。
 * 而我们的两只生物继承自<b>不同的基类</b>（{@code Phantom} 和 {@code Monster}），
 * 没法共用一份字段。所以这里只声明契约，状态分别落在两个实体类里 ——
 * 那几行重复是刻意的，比为了消重去搞泛型基类清楚得多。</p>
 */
public interface PoweredMob extends PowerableMob {

    /**
     * 充能后的爆炸半径倍率。
     *
     * <p><b>数值逐字对齐原版</b> {@code Creeper#explodeCreeper()}：</p>
     * <pre>
     *   float f = this.isPowered() ? 2.0F : 1.0F;
     *   this.level().explode(this, x, y, z, (float)this.explosionRadius * f, ExplosionInteraction.MOB);
     * </pre>
     * <p>苦力怕的基础半径是 3，充能后变成 6 —— 这也是「充能苦力怕能炸死人、普通苦力怕炸不死」的由来。</p>
     */
    float CHARGED_EXPLOSION_MULTIPLIER = 2.0F;

    /**
     * 设置充能状态。这是唯一的写入入口，实现在各自的实体类里。
     *
     * <p>正常情况下只有 {@code thunderHit}（被闪电劈中）会调用它。
     * 想用指令或调试手段手动充能的话，这是那个钩子。</p>
     */
    void setPowered(boolean powered);

    /**
     * 当前爆炸半径应该乘的倍率：充能时 {@link #CHARGED_EXPLOSION_MULTIPLIER}，否则 {@code 1.0F}。
     *
     * <p>把这个算好再交给爆炸逻辑，是为了让两处调用点都只写
     * {@code radius * mob.explosionRadiusMultiplier()}，
     * 而不是各写一遍三元表达式、将来改数值时漏掉一处。</p>
     */
    default float explosionRadiusMultiplier() {
        return this.isPowered() ? CHARGED_EXPLOSION_MULTIPLIER : 1.0F;
    }
}
