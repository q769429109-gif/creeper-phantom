package com.hybridcreeper.explosion;

import com.mojang.logging.LogUtils;
import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.config.HybridCreeperConfig;
import com.hybridcreeper.entity.HybridCreeperEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.slf4j.Logger;

/**
 * 苦力怕幻翼俯冲击中 → 引爆，全流程只在这一个类里。
 *
 * <h2>只对苦力怕幻翼生效</h2>
 * <p>判定条件是 {@code source.getEntity() instanceof HybridCreeperEntity} ——
 * 注意是 {@link HybridCreeperEntity} 而不是 {@code Phantom}。
 * 后者会把原版幻翼也算进来，而原版幻翼<b>必须保持原样</b>。</p>
 *
 * <h2>为什么挂在 {@link LivingDamageEvent.Post} 上</h2>
 * <p>俯冲撕咬发生在 {@code Phantom.PhantomSweepAttackGoal#tick()}：</p>
 * <pre>
 *   if (phantom.getBoundingBox().inflate(0.2F).intersects(target.getBoundingBox())) {
 *       phantom.doHurtTarget(target);            // ← 只有这一处会让它造成近战伤害
 *       phantom.attackPhase = AttackPhase.CIRCLE; // 打完立刻脱离，重新盘旋
 *       phantom.level().levelEvent(1039, ...);    // 播放"嘶吼"音效
 *   }
 * </pre>
 * <p>而 {@code Phantom} 自身<b>没有</b>重写 {@code doHurtTarget}，它走的是
 * {@code Mob} 的通用实现，最终落到 {@code LivingEntity#actuallyHurt}。
 * 因此"它用近战伤害打中了玩家"这件事，在 NeoForge 事件层面完全等价于
 * "一次伤害序列的伤害源实体是苦力怕幻翼、受击者是玩家"。</p>
 *
 * <p>选 {@code Post} 而不是 {@code Pre}/{@code LivingIncomingDamageEvent} 的原因：
 * {@code Post} 只有在血量真的被扣掉之后才带着最终值触发，
 * 被无敌帧、护盾格挡、抗性提升完全吸收的伤害不会误触发爆炸——这符合"击中"的语义。</p>
 *
 * <h2>为什么爆炸的源实体传 null</h2>
 * <p>{@code DamageSource#getEntity()} 返回的是 <b>causingEntity（间接来源）</b>。
 * 如果这里把苦力怕幻翼当成爆炸源实体传进去，<code>Level#explode(blast, ...)</code> 生成的
 * 伤害源就会让本方法再次命中"source.getEntity() instanceof HybridCreeperEntity"，
 * 造成爆炸→伤害→爆炸的<b>无限递归</b>（一秒内上千次爆炸，直接崩服）。</p>
 * <p>所以这里显式构造一个<b>没有任何实体归属</b>的爆炸伤害源。</p>
 *
 * <p>副作用是可接受的甚至更好的：死亡信息走 {@code DamageSource#getKillCredit()} 回退分支，
 * 会取玩家"最后攻击者"——而最后攻击者正是那只苦力怕幻翼，所以消息依然是"被幻翼炸死了"。</p>
 */
@EventBusSubscriber(modid = HybridCreeper.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SwoopExplosionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 上一次引爆的游戏刻，写在实体自身的持久化数据里，随实体一起存盘/销毁，不用额外维护 Map。 */
    private static final String NBT_LAST_BLAST = "hybridcreeper:last_blast";

    /**
     * 重入保护。
     *
     * <p>爆炸会伤害周围所有实体并再次触发 {@code LivingDamageEvent.Post}。
     * 虽然"源实体为 null"已经挡掉了绝大多数情况，但保险起见再加一道闸门，
     * 保证一次引爆过程中不会再叠加第二次引爆。</p>
     */
    private static boolean detonating = false;

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        // 1. 总开关
        if (!HybridCreeperConfig.ENABLED.get()) {
            return;
        }
        // 2. 重入保护
        if (detonating) {
            return;
        }
        // 3. 受击者必须是玩家（原版幻翼的俯冲目标也只有玩家）
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // 4. 伤害必须真的落地：被完全格挡 / 处在无敌帧里（newDamage 会被压成 0）的不算"咬中"
        if (event.getNewDamage() <= 0.0F) {
            return;
        }
        // 5. 只处理服务端，避免客户端预测逻辑重复炸一次
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }
        // 6. 伤害源必须来自苦力怕幻翼（mobAttack 的直接实体与间接实体都是它）
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof HybridCreeperEntity blast)) {
            return;
        }

        // 7. 同一只生物的冷却
        long now = level.getGameTime();
        int cooldown = HybridCreeperConfig.COOLDOWN_TICKS.get();
        CompoundTag data = blast.getPersistentData();
        if (cooldown > 0) {
            long last = data.getLong(NBT_LAST_BLAST);
            if (now - last < cooldown) {
                return;
            }
        }
        data.putLong(NBT_LAST_BLAST, now);

        detonate(level, blast, player);
    }

    /**
     * 在被咬中的玩家身上引爆。
     *
     * <p>爆炸中心取玩家躯干中点（脚底 + 半个身高），而不是脚底：
     * 俯冲姿态是撞上玩家的上半身，取躯干让爆炸看起来"炸在撞击点上"，
     * 同时避开脚下方块，减少出现"地上一坑、人在坑边"的违和感。</p>
     */
    private static void detonate(Level level, HybridCreeperEntity blast, Player victim) {
        double x = victim.getX();
        double y = victim.getY() + victim.getBbHeight() * 0.5D;
        double z = victim.getZ();

        HybridCreeperExplosionCalculator calculator = new HybridCreeperExplosionCalculator(
                HybridCreeperConfig.DESTROY_BLOCKS.get(),
                HybridCreeperConfig.PHANTOM_IMMUNE.get(),
                blast,
                victim,
                HybridCreeperConfig.DAMAGE_MULTIPLIER.get(),
                HybridCreeperConfig.HIT_PLAYER_DAMAGE_MULTIPLIER.get());

        // 破坏方块 = MOB：与苦力怕完全一致，受 mobGriefing 游戏规则约束
        // 不破坏方块 = NONE：保留对实体的伤害与击退，但地形无损
        Level.ExplosionInteraction interaction = HybridCreeperConfig.DESTROY_BLOCKS.get()
                ? Level.ExplosionInteraction.MOB
                : Level.ExplosionInteraction.NONE;

        detonating = true;
        try {
            level.explode(
                    /* source           */ null,                              // 见类注释：必须为 null
                    /* damageSource     */ level.damageSources().explosion(null, null), // 无实体归属
                    /* damageCalculator */ calculator,
                    /* x, y, z          */ x, y, z,
                    /* radius           */ HybridCreeperConfig.EXPLOSION_POWER.get().floatValue(),
                    /* fire             */ HybridCreeperConfig.SET_FIRE.get(),
                    /* interaction      */ interaction);
        } finally {
            detonating = false;
        }

        if (HybridCreeperConfig.DEBUG_LOG.get()) {
            LOGGER.info("[HybridCreeper] 苦力怕幻翼({}) 在 ({}, {}, {}) 引爆，命中玩家 {}，威力 {}",
                    blast.getUUID(),
                    String.format("%.1f", x), String.format("%.1f", y), String.format("%.1f", z),
                    victim.getName().getString(),
                    HybridCreeperConfig.EXPLOSION_POWER.get());
        }
    }

    private SwoopExplosionHandler() {
    }
}
