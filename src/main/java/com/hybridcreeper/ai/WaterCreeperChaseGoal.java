package com.hybridcreeper.ai;

import com.hybridcreeper.entity.WaterCreeperEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 追击 + 撞击目标。
 *
 * <h2>为什么不用原版的 {@code MeleeAttackGoal}</h2>
 * <p>{@code MeleeAttackGoal} 依赖 {@code PathNavigation} 走到"攻击距离"然后原地挥击，
 * 它默认的攻击距离是按陆地步幅算的（约 1.0~1.5 格）。对一个速度 1.5 的水生生物来说，
 * 那个距离它一个 tick 就冲过去了，然后会因为"到达了"而停下挥爪子 —— 完全不像"撞"。</p>
 *
 * <p>所以这里自己做两件事：<b>持续重新寻路</b>（10 tick 一次，高速生物必须比陆地生物
 * 更频繁地修正航向）+ <b>碰撞箱相交即撞击</b>（不是距离判定，是真正的物理接触）。</p>
 *
 * <h2>撞击是怎么作用于船的</h2>
 * <p>真正施加伤害和冲量的代码在 {@link WaterCreeperEntity#ram(net.minecraft.world.entity.Entity)}，
 * 这里只负责发现"撞上了"。</p>
 *
 * <h2>关于"打断玩家的行动"</h2>
 * <p>原版船没有耐久，也不吃常规伤害（{@code Boat#hurt} 只在被攻击时掉落自己）。
 * 所以"打断"是通过<b>冲量</b>实现的：把船推向远离生物的方向，船就会偏离航向。
 * 船的操控依赖持续划桨，被打断一次就得重新对准 —— 而它下一次撞击已经在路上了。</p>
 */
public class WaterCreeperChaseGoal extends Goal {

    /** 重新寻路的间隔（tick）。陆地生物普遍用 20~40，这里用 10 以匹配它的速度。 */
    private static final int REPATH_INTERVAL = 10;

    /** 撞击判定的膨胀量。稍微放大一点，补偿高速移动导致的采样跳变。 */
    private static final float RAM_INFLATE = 0.4F;

    private final WaterCreeperEntity mob;

    @Nullable
    private LivingEntity target;

    private int repathCooldown;

    public WaterCreeperChaseGoal(WaterCreeperEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity living = this.mob.getTarget();
        if (living == null || !living.isAlive()) {
            return false;
        }
        this.target = living;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity living = this.mob.getTarget();
        return living != null && living.isAlive() && living == this.target;
    }

    @Override
    public void start() {
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.target = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity living = this.target;
        if (living == null) {
            return;
        }

        this.mob.getLookControl().setLookAt(living, 30.0F, 30.0F);

        // 定期重新寻路。用 1.0 的速度倍率，真实速度由 MOVEMENT_SPEED 属性决定（1.5）。
        if (--this.repathCooldown <= 0) {
            this.repathCooldown = REPATH_INTERVAL;
            this.mob.getNavigation().moveTo(living, 1.0);
        }

        // 物理接触 → 撞击
        if (this.mob.getBoundingBox().inflate(RAM_INFLATE).intersects(living.getBoundingBox())) {
            this.mob.ram(living);
        }
    }
}
