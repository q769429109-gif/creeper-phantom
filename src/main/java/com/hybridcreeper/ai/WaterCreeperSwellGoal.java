package com.hybridcreeper.ai;

import com.hybridcreeper.entity.WaterCreeperEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 引信目标 —— 逐行对标原版 {@code SwellGoal}，只换了个宿主类型。
 *
 * <p>原版 {@code SwellGoal} 的字段是写死的 {@code Creeper}，没法直接复用，
 * 所以这里把它整个搬过来，逻辑一个字没改：</p>
 * <pre>
 *   目标为空            → 收缩（swellDir = -1）
 *   距离平方 &gt; 49      → 收缩（7 格外放手）
 *   没有视线            → 收缩
 *   否则                → 膨胀（swellDir = 1）
 * </pre>
 *
 * <p>它占用 {@code MOVE} 标志位，所以膨胀时生物会<b>停下来</b> —— 和苦力怕一样，
 * 那是"点燃引信后原地不动"的经典观感。</p>
 *
 * <h2>为什么船上的玩家不触发膨胀</h2>
 * <p>这是刻意的设计取舍。需求里对船的描述是<b>「不断撞击」</b>而不是"炸船"，
 * 所以当目标坐在船（或任何载具）上时，这里会主动放弃膨胀，
 * 把行为让给 {@link WaterCreeperChaseGoal} 去反复撞 —— 否则它会一贴上来就自爆，
 * 玩家只会听到一声响，感受不到"被持续追击"的压力。</p>
 */
public class WaterCreeperSwellGoal extends Goal {

    /** 开始膨胀的距离（平方）。原版苦力怕也是 9.0 → 3 格。 */
    private static final double SWELL_RANGE_SQR = 9.0;

    /** 放弃膨胀的距离（平方）。原版苦力怕也是 49.0 → 7 格。 */
    private static final double ABORT_RANGE_SQR = 49.0;

    private final WaterCreeperEntity creeper;

    @Nullable
    private LivingEntity target;

    public WaterCreeperSwellGoal(WaterCreeperEntity creeper) {
        this.creeper = creeper;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity living = this.creeper.getTarget();
        if (living == null || !living.isAlive()) {
            // 已经在膨胀中的话允许继续（走完引信），否则不起用
            return this.creeper.getSwellDir() > 0;
        }
        // 目标坐在船上 → 交给撞击目标处理，不在这里自爆
        if (living.isPassenger()) {
            return this.creeper.getSwellDir() > 0 && this.creeper.distanceToSqr(living) < SWELL_RANGE_SQR;
        }
        return this.creeper.getSwellDir() > 0 || this.creeper.distanceToSqr(living) < SWELL_RANGE_SQR;
    }

    @Override
    public void start() {
        this.creeper.getNavigation().stop();
        this.target = this.creeper.getTarget();
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.target == null) {
            this.creeper.setSwellDir(-1);
        } else if (this.creeper.distanceToSqr(this.target) > ABORT_RANGE_SQR) {
            this.creeper.setSwellDir(-1);
        } else if (!this.creeper.getSensing().hasLineOfSight(this.target)) {
            // 水下视线判定比陆地严格（会被水方块挡），所以它偶尔会"闪断" —
            // 这里如果目标已经在 3 格内就不放手，免得贴脸了却突然收帆。
            this.creeper.setSwellDir(
                    this.creeper.distanceToSqr(this.target) < SWELL_RANGE_SQR ? 1 : -1);
        } else {
            this.creeper.setSwellDir(1);
        }
    }
}
