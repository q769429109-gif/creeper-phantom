package com.hybridcreeper.entity;

import com.hybridcreeper.config.HybridCreeperConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;

/**
 * 苦力怕幻翼的自然生成器 —— 逐行对标原版 {@code PhantomSpawner}。
 *
 * <h2>原版幻翼是怎么生成的</h2>
 * <p>幻翼<b>不走</b>普通的生物刷怪系统（没有 {@code SpawnPlacements} 注册，也不占刷怪上限）。
 * 它由 {@code net.minecraft.world.level.levelgen.PhantomSpawner} 这个
 * {@link CustomSpawner} 直接生成，挂在 {@code MinecraftServer#createLevels} 里
 * 传给<b>主世界</b> {@code ServerLevel} 的那个固定列表上（下界/末地传的是空列表，
 * 所以那俩维度没有幻翼）。</p>
 *
 * <p>它的判定顺序是这样的（下面每一步都原样保留）：</p>
 * <ol>
 *   <li>本 tick 允许生成敌对生物（{@code spawnEnemies}）；</li>
 *   <li>游戏规则 {@code doInsomnia} 为 true（防失眠开关）；</li>
 *   <li>40~120 秒的尝试间隔计时器到期；</li>
 *   <li>夜晚判定：{@code getSkyDarken() < 5} <b>且</b>该维度有天空光
 *       （注意这个条件对无天空光的维度<b>不生效</b>，是原版写法）；</li>
 *   <li>逐个遍历非旁观玩家，触发 {@code PlayerSpawnPhantomsEvent}
 *       （别的模组可以在这里 ALLOW / DENY / 改数量）；</li>
 *   <li>玩家头顶能看到天（{@code PlayerSpawnPhantomsEvent#shouldSpawnPhantoms}）；</li>
 *   <li>当前难度 {@code isHarderThan(random * 3)}；</li>
 *   <li><b>（默认已跳过）</b>玩家距离上次睡觉的刻数 {@code TIME_SINCE_REST} 满足
 *       {@code random.nextInt(ticks) >= 72000}（= 至少连续 3 个游戏日没睡）——
 *       这条「失眠」限制由配置 {@code spawn.requireInsomnia} 控制，<b>默认关闭</b>；</li>
 *   <li>生成点：玩家上方 20~34 格、水平 ±10 格；</li>
 *   <li>该位置是"合法的空生成方块"；</li>
 *   <li>刷出 1~（难度等级+1）只，各自 {@code finalizeSpawn} 后入场。</li>
 * </ol>
 *
 * <h2>怎么注入的</h2>
 * <p>原版那个 spawner 列表是 {@code MinecraftServer} 里的局部变量，
 * {@code ServerLevel#customSpawners} 又是 private 的 —— 但 NeoForge 提供了
 * {@code ModifyCustomSpawnersEvent}：{@code ServerLevel} 构造时，
 * {@code EventHooks#getCustomSpawners} 会把这个列表交出来让我们改。
 * 所以<b>不需要访问转换器，也不需要 Mixin</b>。注入代码见 {@link HybridCreeperSpawnHook}。</p>
 *
 * <h2>关于 {@code PlayerSpawnPhantomsEvent}</h2>
 * <p>本生成器<b>照常触发</b>这个事件。代价是别的模组每轮会收到两次（原版幻翼一次、
 * 我们一次）；好处是"装了反幻翼模组就不刷"这类行为对我们的生物同样生效 —— 这才是
 * "生成方式与幻翼一致"应有的样子。</p>
 */
public class HybridCreeperSpawner implements CustomSpawner {

    /** 距离下一次尝试生成还有多少 tick。语义与原版完全一致。 */
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
        // 总开关（配置项，默认开启）
        if (!HybridCreeperConfig.SPAWN_ENABLED.get()) {
            return 0;
        }
        // 1. 本 tick 不允许刷敌对生物 —— 与原版一致，直接退出
        if (!spawnEnemies) {
            return 0;
        }
        // 2. doInsomnia 游戏规则 —— 与原版一致
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            return 0;
        }

        RandomSource random = level.random;

        // 3. 尝试间隔计时器。注意这里是 +=（累加）而不是 =，
        //    与原版一字不差 —— 第一次调用时 nextTick 为 -1，累加后就是完整的间隔值。
        this.nextTick--;
        if (this.nextTick > 0) {
            return 0;
        }
        this.nextTick += this.nextIntervalTicks(random);

        // 4. 夜晚判定。原版的写法是"天黑 且 有天空光"，所以无天空光的维度反而不挡。
        if (level.getSkyDarken() < HybridCreeperConfig.SKY_DARKEN_THRESHOLD.get()
                && level.dimensionType().hasSkyLight()) {
            return 0;
        }

        int spawned = 0;

        for (ServerPlayer player : level.players()) {
            // 旁观者不刷 —— 与原版一致
            if (player.isSpectator()) {
                continue;
            }

            BlockPos playerPos = player.blockPosition();

            // 5. 让别的模组有机会插手（ALLOW / DENY / 改数量）—— 与原版一致
            PlayerSpawnPhantomsEvent event =
                    EventHooks.firePlayerSpawnPhantoms(player, level, playerPos);
            boolean forced = event.getResult() == PlayerSpawnPhantomsEvent.Result.ALLOW;

            // 6. 玩家头顶能看到天（或被 ALLOW 强制放行）
            if (!event.shouldSpawnPhantoms(level, playerPos)) {
                continue;
            }

            DifficultyInstance difficulty = level.getCurrentDifficultyAt(playerPos);

            // 7. 难度随机门槛 —— 与原版一致
            if (!forced && !difficulty.isHarderThan(random.nextFloat() * 3.0F)) {
                continue;
            }

            // 8. 失眠时长门槛 —— **默认关闭**（配置 spawn.requireInsomnia = false）。
            //    原版幻翼要求玩家连续 3 个游戏日（72000 tick）没睡觉才有机会刷；
            //    2026-09-24 按主人要求去掉这条限制，所以默认整段跳过。
            //    想恢复原版行为，把 requireInsomnia 改回 true 即可（阈值仍看 minTicksSinceRest）。
            //    clamp 下限 1 是为了避免 nextInt(0) 抛异常。
            if (!forced && HybridCreeperConfig.SPAWN_REQUIRE_INSOMNIA.get()) {
                ServerStatsCounter stats = player.getStats();
                int ticksSinceRest = Mth.clamp(
                        stats.getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)),
                        1, Integer.MAX_VALUE);
                if (random.nextInt(ticksSinceRest) < HybridCreeperConfig.MIN_TICKS_SINCE_REST.get()) {
                    continue;
                }
            }

            // 9. 生成点：玩家上方 20~34 格，水平方向各 ±10 格 —— 与原版一致
            BlockPos spawnPos = playerPos
                    .above(20 + random.nextInt(15))
                    .east(-10 + random.nextInt(21))
                    .south(-10 + random.nextInt(21));

            BlockState blockState = level.getBlockState(spawnPos);
            FluidState fluidState = level.getFluidState(spawnPos);

            // 10. 该位置必须是合法的空生成方块（不卡在方块里、不是液体、不是危险方块）
            if (!NaturalSpawner.isValidEmptySpawnBlock(
                    level, spawnPos, blockState, fluidState, ModEntities.CREEPER_PHANTOM.get())) {
                continue;
            }

            // 11. 一次刷 1~（难度等级+1）只，与原版一致；数量可被事件覆写。
            //     再乘上配置的数量倍率（默认 2 = 普通幻翼的两倍）。
            SpawnGroupData groupData = null;
            int count = event.getPhantomsToSpawn() * HybridCreeperConfig.SPAWN_COUNT_MULTIPLIER.get();

            for (int i = 0; i < count; i++) {
                HybridCreeperEntity blast = ModEntities.CREEPER_PHANTOM.get().create(level);
                if (blast == null) {
                    continue;
                }
                blast.moveTo(spawnPos, 0.0F, 0.0F);
                // finalizeSpawn 会做本生物自己的初始化（幻翼那边是设置锚点、体型归零）
                groupData = blast.finalizeSpawn(level, difficulty, MobSpawnType.NATURAL, groupData);
                level.addFreshEntityWithPassengers(blast);
                spawned++;
            }
        }

        return spawned;
    }

    /**
     * 计算下一次尝试生成的间隔（tick）。
     *
     * <p>原版硬编码 {@code (60 + random.nextInt(60)) * 20}，也就是
     * <b>[60, 120) 秒</b>的均匀分布。这里改成读配置，默认值保持完全一致。</p>
     */
    private int nextIntervalTicks(RandomSource random) {
        int minSeconds = Math.max(1, HybridCreeperConfig.SPAWN_INTERVAL_MIN_SECONDS.get());
        int maxSeconds = Math.max(minSeconds, HybridCreeperConfig.SPAWN_INTERVAL_MAX_SECONDS.get());
        int span = Math.max(1, maxSeconds - minSeconds);
        return (minSeconds + random.nextInt(span)) * 20;
    }
}
