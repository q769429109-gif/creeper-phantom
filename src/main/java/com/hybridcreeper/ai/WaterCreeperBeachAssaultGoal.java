package com.hybridcreeper.ai;

import com.hybridcreeper.config.HybridCreeperConfig;
import com.hybridcreeper.entity.WaterCreeperEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 上岸突袭 —— 玩家缩在岸上不出来时，它跳出水面，落地即爆。
 *
 * <h2>它解决的问题</h2>
 * <p>如果没有这个目标，苦力怕海豚虽然在水里无敌，但只要玩家脚一沾地就安全了 ——
 * 那它反而成了"水域保镖"。这个目标把岸边也变成危险区。</p>
 *
 * <h2>触发链路</h2>
 * <ol>
 *   <li><b>起意</b>：玩家不在水里、不在载具上、自己还在水里、水平距离 14 格以内；</li>
 *   <li><b>助游</b>：朝玩家方向游过去（走水中寻路，目标落在岸边时路径终点自然就是岸边水域）；</li>
 *   <li><b>起跳</b>：进入 3.5 格时，朝玩家方向施加一个斜向冲量把自己抛出水面；</li>
 *   <li><b>结算</b>：落地那一刻由 {@code WaterCreeperEntity#aiStep} 判定并引爆
 *       —— 判定条件是"贴地且不在水里"，所以腾空过程不会提前炸。</li>
 * </ol>
 *
 * <h2>为什么 {@code canContinueToUse} 要特殊处理</h2>
 * <p>{@code Goal} 默认的 {@code canContinueToUse} 就是再调一次 {@code canUse()}。
 * 但这里 {@code canUse()} 要求 {@code mob.isInWater()} —— 一旦它腾空离水，
 * {@code canUse()} 立刻变 false，目标会被中止、突击状态被清掉，
 * <b>落地就不会爆炸了</b>。</p>
 * <p>所以这里重写了 {@code canContinueToUse}：<b>只要已经进入突击状态就咬着不放</b>，
 * 直到它落地引爆（实体消失）或目标消失。这个"有去无回"的语义正是需求里
 * "跳上岸随后立即爆炸"要的。</p>
 */
public class WaterCreeperBeachAssaultGoal extends Goal {

    /** 起意距离（平方）。再远就先正常游过去。 */
    private static final double ASSAULT_RANGE_SQR = 14.0 * 14.0;

    /** 起跳距离（平方）。进入 3.5 格就抛出水面。 */
    private static final double LEAP_TRIGGER_SQR = 3.5 * 3.5;

    /** 两次起跳之间的间隔（tick）。落回水里之后要缓一下再跳，否则像抽搐。 */
    private static final int LEAP_COOLDOWN_TICKS = 20;

    /**
     * 起跳的水平速度（格/tick）。0.35 配上下面的垂直速度，大约能跨出 3~4 格、抬起 1.5 格 ——
     * 够上一格高的河岸。岸特别高（悬崖）它上不去，会反复跳，这是可接受的：
     * 玩家躲到悬崖上本来就应该安全。
     */
    private static final double LEAP_SPEED_HORIZONTAL = 0.35;

    /** 起跳的垂直速度（格/tick）。约等于原版跳跃初速 0.42 再加一点，用来抵消水的阻力。 */
    private static final double LEAP_SPEED_VERTICAL = 0.5;

    private final WaterCreeperEntity mob;

    @Nullable
    private LivingEntity target;

    private int leapCooldown;
    private int repathCooldown;

    public WaterCreeperBeachAssaultGoal(WaterCreeperEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // 配置里关掉了自爆 → 跳上岸也没意义；或者主人明确关掉了"上岸突袭"
        if (!this.mob.isExplosionEnabled()
                || !HybridCreeperConfig.WATER_CREEPER_BEACH_ASSAULT.get()) {
            return false;
        }
        LivingEntity living = this.mob.getTarget();
        if (living == null || !living.isAlive()) {
            return false;
        }
        // 玩家在船上 → 那是"撞击"的活儿，别上岸
        if (living.isPassenger()) {
            return false;
        }
        // 玩家自己在水里 → 正常追击就够了
        if (living.isInWater()) {
            return false;
        }
        // 自己得在水里，才能从水里跳出去
        if (!this.mob.isInWater()) {
            return false;
        }
        if (this.mob.distanceToSqr(living) > ASSAULT_RANGE_SQR) {
            return false;
        }
        this.target = living;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity living = this.mob.getTarget();
        if (living == null || !living.isAlive()) {
            return false;
        }
        // 已经起意上岸了就不放手 —— 哪怕正在腾空、哪怕已经站上陆地。
        // 见类注释：这里一放手，落地就不会引爆了。
        if (this.mob.isBeachAssaulting()) {
            return true;
        }
        return this.canUse();
    }

    @Override
    public void start() {
        this.leapCooldown = 0;
        this.repathCooldown = 0;
        this.mob.setBeachAssaulting(true);
    }

    @Override
    public void stop() {
        this.target = null;
        this.mob.setBeachAssaulting(false);
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

        double distSqr = this.mob.distanceToSqr(living);

        if (distSqr > LEAP_TRIGGER_SQR) {
            // 助游阶段：游向玩家。水中寻路走不到陆地上的目标，
            // 但会把路径终点落在最靠近玩家的那块水里 —— 正好是我们要的岸边。
            if (--this.repathCooldown <= 0) {
                this.repathCooldown = 10;
                this.mob.getNavigation().moveTo(living, 1.2);
            }
            return;
        }

        // 进入起跳距离
        this.mob.getNavigation().stop();

        if (this.leapCooldown > 0) {
            this.leapCooldown--;
            return;
        }

        this.leapTowards(living);
        this.leapCooldown = LEAP_COOLDOWN_TICKS;
    }

    /** 朝目标方向抛出水面。 */
    private void leapTowards(LivingEntity living) {
        double dx = living.getX() - this.mob.getX();
        double dz = living.getZ() - this.mob.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            return;
        }
        this.mob.setDeltaMovement(
                dx / len * LEAP_SPEED_HORIZONTAL,
                LEAP_SPEED_VERTICAL,
                dz / len * LEAP_SPEED_HORIZONTAL);
        // 告诉服务端"这个速度是外力给的"，需要同步给客户端，否则客户端会看到它原地抖。
        this.mob.hasImpulse = true;
        this.mob.hurtMarked = true;
    }
}
