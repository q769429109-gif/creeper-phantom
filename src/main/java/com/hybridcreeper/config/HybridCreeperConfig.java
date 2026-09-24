package com.hybridcreeper.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 杂交苦力怕配置。
 *
 * <p>文件位置：{@code config/hybridcreeper-common.toml}（COMMON 类型，固定路径）。</p>
 *
 * <h2>爆炸默认值</h2>
 * <p>全部对齐"苦力怕等级"：威力 3.0、破坏方块、不引燃，与 {@code Creeper#explodeCreeper()} 中
 * {@code level.explode(this, x, y, z, explosionRadius, ExplosionInteraction.MOB)} 完全一致
 * （苦力怕默认 {@code explosionRadius = 3}）。</p>
 *
 * <h2>生成默认值</h2>
 * <p>{@code [spawn]} 段里的每一项默认值都抄自原版
 * {@code net.minecraft.world.level.levelgen.PhantomSpawner} 的硬编码常量，
 * 所以开箱即用时苦力怕幻翼的生成条件与幻翼<b>完全相同</b>。</p>
 */
public final class HybridCreeperConfig {

    public static final ModConfigSpec SPEC;

    /* ---------------- 总开关 ---------------- */

    /** 总开关。关掉后苦力怕幻翼只俯冲撕咬、不爆炸（但仍会自然生成）。 */
    public static final ModConfigSpec.BooleanValue ENABLED;

    /* ---------------- 爆炸参数 ---------------- */

    /** 爆炸威力。原版苦力怕 = 3.0，TNT = 4.0，凋灵生成时 = 7.0。 */
    public static final ModConfigSpec.DoubleValue EXPLOSION_POWER;

    /** 是否破坏方块。true 时受 mobGriefing 游戏规则约束（与苦力怕一致）。 */
    public static final ModConfigSpec.BooleanValue DESTROY_BLOCKS;

    /** 是否引燃周围方块。原版苦力怕不引燃，故默认 false。 */
    public static final ModConfigSpec.BooleanValue SET_FIRE;

    /** 苦力怕幻翼是否免疫自己引发的爆炸。 */
    public static final ModConfigSpec.BooleanValue PHANTOM_IMMUNE;

    /* ---------------- 伤害与节奏 ---------------- */

    /** 爆炸对所有实体的伤害倍率。 */
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;

    /** 额外作用于"被咬中的那名玩家"的伤害倍率。 */
    public static final ModConfigSpec.DoubleValue HIT_PLAYER_DAMAGE_MULTIPLIER;

    /** 同一只苦力怕幻翼两次引爆的最小间隔（tick）。 */
    public static final ModConfigSpec.IntValue COOLDOWN_TICKS;

    /* ---------------- 自然生成 ---------------- */

    /** 是否参与自然生成。关掉后只能靠指令或刷怪蛋生成。 */
    public static final ModConfigSpec.BooleanValue SPAWN_ENABLED;

    /** 失眠时长门槛（tick）。原版幻翼 = 72000（3 个游戏日）。 */
    public static final ModConfigSpec.IntValue MIN_TICKS_SINCE_REST;

    /** 夜晚亮度阈值。原版幻翼 = 5（getSkyDarken() 小于它才算天黑）。 */
    public static final ModConfigSpec.IntValue SKY_DARKEN_THRESHOLD;

    /** 生成尝试间隔下限（秒）。原版幻翼 = 60。 */
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_MIN_SECONDS;

    /** 生成尝试间隔上限（秒，不含）。原版幻翼 = 120（实际区间 [60, 120)）。 */
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_MAX_SECONDS;

    /** 一次生成尝试刷出的数量倍率。原版幻翼 = 1（按难度刷 1~N 只）；本模组默认 2（两倍）。 */
    public static final ModConfigSpec.IntValue SPAWN_COUNT_MULTIPLIER;

    /* ---------------- 调试 ---------------- */

    /** 是否在日志里打印每次引爆的坐标与生物信息。 */
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("杂交苦力怕 Hybrid Creeper —— 苦力怕幻翼俯冲击中玩家时引爆苦力怕级爆炸").push("general");
        ENABLED = b.comment("总开关。false 时苦力怕幻翼只俯冲撕咬，不爆炸。",
                        "注意：这个开关不影响原版幻翼 —— 它从头到尾都没被改过。")
                .define("enabled", true);
        b.pop();

        b.comment("爆炸参数 Explosion").push("explosion");
        EXPLOSION_POWER = b.comment("爆炸威力。原版苦力怕 = 3.0，TNT = 4.0，凋灵生成时 = 7.0。")
                .defineInRange("explosionPower", 3.0D, 0.0D, 64.0D);
        DESTROY_BLOCKS = b.comment("是否破坏方块。",
                        "true = 苦力怕级（同时受 mobGriefing 游戏规则约束）；",
                        "false = 只炸实体、地形完好（等价于 TNT 的 NONE 交互）。")
                .define("destroyBlocks", true);
        SET_FIRE = b.comment("爆炸是否引燃周围方块。原版苦力怕不引燃，故默认 false。")
                .define("setFire", false);
        PHANTOM_IMMUNE = b.comment("苦力怕幻翼是否免疫自己引发的爆炸。",
                        "true = 炸完继续盘旋、可再次俯冲（推荐，也更像\"会自爆的掠食者\"）；",
                        "false = 同归于尽，苦力怕幻翼几乎必死（威力 3.0 近距离约 49 点伤害）。")
                .define("phantomImmune", true);
        b.pop();

        b.comment("伤害与节奏 Damage & Rhythm").push("damage");
        DAMAGE_MULTIPLIER = b.comment("爆炸对所有实体的伤害倍率。")
                .defineInRange("damageMultiplier", 1.0D, 0.0D, 10.0D);
        HIT_PLAYER_DAMAGE_MULTIPLIER = b.comment("额外作用于「被咬中的那名玩家」的伤害倍率。",
                        "1.0 = 完整承伤：撕咬伤害 + 满额爆炸，穿钻石套也基本必死；",
                        "0.5 = 惩罚减半；0.0 = 只炸周围、不额外伤害被咬的人。")
                .defineInRange("hitPlayerDamageMultiplier", 1.0D, 0.0D, 10.0D);
        COOLDOWN_TICKS = b.comment("同一只苦力怕幻翼两次引爆的最小间隔（tick）。20 tick = 1 秒。",
                        "原版俯冲冷却本身就有 8~12 秒，这里是防止异常情况下的连环引爆。")
                .defineInRange("cooldownTicks", 40, 0, 1200);
        b.pop();

        b.comment("自然生成 Natural Spawning",
                        "以下默认值均与原版幻翼 PhantomSpawner 的硬编码常量一致。",
                        "苦力怕幻翼只在「原版会刷幻翼」的维度里生效（主世界）。")
                .push("spawn");
        SPAWN_ENABLED = b.comment("是否参与自然生成。",
                        "false = 世界里不会自己刷出来，只能用指令 /summon 或刷怪蛋。",
                        "true = 与原版幻翼完全相同的条件（夜晚 + 连续 3 天不睡觉 + 头顶见天）。")
                .define("naturalSpawn", true);
        MIN_TICKS_SINCE_REST = b.comment("玩家距上次睡觉至少多少 tick 才会刷。",
                        "原版幻翼 = 72000（= 3 个游戏日 = 60 分钟）。调低会明显变多。")
                .defineInRange("minTicksSinceRest", 72000, 0, Integer.MAX_VALUE);
        SKY_DARKEN_THRESHOLD = b.comment("天黑程度阈值：getSkyDarken() 小于它才算夜晚。",
                        "原版幻翼 = 5。数值越小白天的容忍度越低。")
                .defineInRange("skyDarkenThreshold", 5, 0, 15);
        SPAWN_INTERVAL_MIN_SECONDS = b.comment("生成尝试间隔下限（秒）。原版幻翼 = 60。")
                .defineInRange("spawnIntervalMinSeconds", 60, 1, 3600);
        SPAWN_INTERVAL_MAX_SECONDS = b.comment("生成尝试间隔上限（秒，不含）。原版幻翼 = 120。",
                        "即实际间隔在 [下限, 上限) 之间随机取。")
                .defineInRange("spawnIntervalMaxSeconds", 120, 1, 3600);
        SPAWN_COUNT_MULTIPLIER = b.comment("一次生成尝试刷出的数量倍率（相对原版幻翼）。",
                        "原版幻翼 = 1（按难度刷 1~N 只）；本模组默认 2 = 两倍。",
                        "想恢复与原版完全一致的数量，改成 1。")
                .defineInRange("spawnCountMultiplier", 2, 1, 20);
        b.pop();

        b.comment("调试 Debug").push("debug");
        DEBUG_LOG = b.comment("在日志中打印每次引爆的坐标与被咬中的玩家。")
                .define("debugLog", false);
        b.pop();

        SPEC = b.build();
    }

    private HybridCreeperConfig() {
    }
}
