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
 * <h2>生成默认值（v1.11.0 起）</h2>
 * <p>{@code [spawn]} 段现在只剩一个总开关 —— 苦力怕幻翼改为<b>独立怪物生成</b>：
 * 生成位置由 {@code SpawnPlacements} 注册（与僵尸同款：夜晚/阴暗 + 地面），
 * 密度由数据包 {@code biome_modifier/creeper_phantom_spawns.json} 的 {@code weight} 决定。
 * 它不再伴随原版幻翼、也不再看玩家的失眠统计。</p>
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

    /* ---------------- 调试 ---------------- */

    /** 是否在日志里打印每次引爆的坐标与生物信息。 */
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;

    /* ---------------- 苦力怕海豚 ---------------- */

    /** 苦力怕海豚是否自爆。false = 只撞船与近战，不再引爆自己。 */
    public static final ModConfigSpec.BooleanValue WATER_CREEPER_EXPLODE;

    /** 苦力怕海豚是否参与自然生成。 */
    public static final ModConfigSpec.BooleanValue WATER_CREEPER_NATURAL_SPAWN;

    /** 最大生命。原版苦力怕 = 20。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_MAX_HEALTH;

    /** 撞击伤害（爆炸伤害另算）。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_ATTACK_DAMAGE;

    /** 陆地上（含上岸突袭）的移动速度。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_MOVEMENT_SPEED;

    /** 水中的速度倍率（NeoForge 标准扩展属性 SWIM_SPEED）。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_SWIM_SPEED;

    /** 主动索敌距离。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_FOLLOW_RANGE;

    /** 抗击退。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_KNOCKBACK_RESISTANCE;

    /** 爆炸威力（= 爆炸半径）。原版苦力怕 = 3.0，TNT = 4.0。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_EXPLOSION_POWER;

    /** 是否破坏方块。false = 只炸实体，地形完好。 */
    public static final ModConfigSpec.BooleanValue WATER_CREEPER_DESTROY_BLOCKS;

    /** 爆炸是否引燃周围方块。 */
    public static final ModConfigSpec.BooleanValue WATER_CREEPER_SET_FIRE;

    /** 爆炸对所有实体的伤害倍率。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_DAMAGE_MULTIPLIER;

    /** 引信长度（tick，20 tick = 1 秒）。原版苦力怕 = 30。 */
    public static final ModConfigSpec.IntValue WATER_CREEPER_FUSE_TICKS;

    /** 被闪电充能后的爆炸半径倍率。原版苦力怕 = 2.0（半径翻倍）。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_CHARGED_MULTIPLIER;

    /** 贴到多少格以内开始点燃引信。原版苦力怕 = 3.0。 */
    public static final ModConfigSpec.DoubleValue WATER_CREEPER_SWELL_RANGE;

    /** 是否允许它跳出水面扑上岸。false = 只在水中活动。 */
    public static final ModConfigSpec.BooleanValue WATER_CREEPER_BEACH_ASSAULT;

    /** 两次撞船之间的最小间隔（tick）。 */
    public static final ModConfigSpec.IntValue WATER_CREEPER_RAM_COOLDOWN_TICKS;

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
                        "v1.11.0 起苦力怕幻翼是「独立怪物」：走标准怪物刷新循环",
                        "（与僵尸同池同判定），不再伴随原版幻翼、也不看玩家失眠。")
                .push("spawn");
        SPAWN_ENABLED = b.comment("是否参与自然生成。",
                        "false = 世界里不会自己刷出来，只能用指令 /summon 或刷怪蛋。",
                        "生成条件：夜晚或阴暗处（与僵尸同款判定），主世界。",
                        "密度由生物群系修饰符里的 weight 控制（默认 20，",
                        "参照：末影人 10、苦力怕 100），见",
                        "data/hybridcreeper/neoforge/biome_modifier/creeper_phantom_spawns.json。")
                .define("naturalSpawn", true);
        b.pop();

        b.comment("调试 Debug").push("debug");
        DEBUG_LOG = b.comment("在日志中打印每次引爆的坐标与被咬中的玩家。")
                .define("debugLog", false);
        b.pop();

        // ================================================================
        // 苦力怕海豚
        // ================================================================
        b.comment("苦力怕海豚 Creeper Dolphin",
                        "水域专属的高速自爆猎手。以下默认值全部对齐原版手感",
                        "（属性抄苦力怕/海豚，爆炸数值抄苦力怕）。",
                        "",
                        "⚠️ 这些开关只作用于苦力怕海豚，与上面苦力怕幻翼的那些互不影响。",
                        "⚠️ 「默认值」在生物<b>生成时</b>写入。已经在存档里的旧生物会保留它们",
                        "   自己的数值（与苦力怕一样存在实体 NBT 里），等它们消失或重新刷出后生效；",
                        "   想单独改某一只可以用 /data merge entity <目标> {ExplosionRadius:10,Fuse:60}。")
                .push("waterCreeper");

        WATER_CREEPER_EXPLODE = b.comment("是否自爆。",
                        "false = 只撞船与近战撕咬，不再引爆自己（它仍然会自然生成）。")
                .define("explode", true);
        WATER_CREEPER_NATURAL_SPAWN = b.comment("是否参与自然生成。",
                        "false = 世界里不会自己刷出来，只能用指令 /summon 或刷怪蛋。")
                .define("naturalSpawn", true);

        b.comment("属性 Attributes",
                        "参照值：海豚 MOVEMENT_SPEED 1.2、僵尸 0.23、玩家疾跑 0.13。",
                        "水里的速度看 swimSpeed，陆地上（含上岸突袭）看 movementSpeed。")
                .push("attributes");
        WATER_CREEPER_MAX_HEALTH = b.comment("最大生命。原版苦力怕 = 20。")
                .defineInRange("maxHealth", 20.0D, 1.0D, 1024.0D);
        WATER_CREEPER_ATTACK_DAMAGE = b.comment("撞击伤害（爆炸伤害另算）。")
                .defineInRange("attackDamage", 4.0D, 0.0D, 2048.0D);
        WATER_CREEPER_MOVEMENT_SPEED = b.comment("陆地上（含上岸突袭）的移动速度。原为 1.5。")
                .defineInRange("movementSpeed", 1.5D, 0.0D, 10.0D);
        WATER_CREEPER_SWIM_SPEED = b.comment("水中的速度倍率（NeoForge 扩展属性 SWIM_SPEED）。",
                        "关键：水里速度 = 0.02 × 本值，与 movementSpeed 无关。",
                        "默认 7.5 ≈ 0.15 格/tick ≈ 3 格/秒；原为 15（≈6 格/秒）时体感过快。",
                        "参照：1.0（默认值）≈ 0.4 格/秒，几乎不动。")
                .defineInRange("swimSpeed", 7.5D, 0.0D, 100.0D);
        WATER_CREEPER_FOLLOW_RANGE = b.comment("主动索敌距离。")
                .defineInRange("followRange", 32.0D, 0.0D, 128.0D);
        WATER_CREEPER_KNOCKBACK_RESISTANCE = b.comment("抗击退。免得被自己的爆炸掀得满海乱飞。")
                .defineInRange("knockbackResistance", 0.3D, 0.0D, 1.0D);
        b.pop();

        b.comment("爆炸 Explosion").push("explosion");
        WATER_CREEPER_EXPLOSION_POWER = b.comment("爆炸威力（= 半径）。原版苦力怕 = 3.0，TNT = 4.0。")
                .defineInRange("explosionPower", 3.0D, 0.0D, 64.0D);
        WATER_CREEPER_DESTROY_BLOCKS = b.comment("是否破坏方块。",
                        "true = 苦力怕级（同时受 mobGriefing 游戏规则约束）；",
                        "false = 只炸实体、地形完好。")
                .define("destroyBlocks", true);
        WATER_CREEPER_SET_FIRE = b.comment("爆炸是否引燃周围方块。原版苦力怕不引燃，故默认 false。")
                .define("setFire", false);
        WATER_CREEPER_DAMAGE_MULTIPLIER = b.comment("爆炸对所有实体的伤害倍率。")
                .defineInRange("damageMultiplier", 1.0D, 0.0D, 10.0D);
        WATER_CREEPER_FUSE_TICKS = b.comment("引信长度（tick，20 tick = 1 秒）。原版苦力怕 = 30（1.5 秒）。",
                        "调大 = 给玩家更多逃跑时间；调小 = 更猝不及防。",
                        "下限 3 是为了渲染动画（膨胀比例要除以 引信-2）。")
                .defineInRange("fuseTicks", 30, 3, 1200);
        WATER_CREEPER_CHARGED_MULTIPLIER = b.comment("被闪电充能后的爆炸半径倍率。",
                        "原版苦力怕 = 2.0（半径 3 → 6，这就是「充能苦力怕炸得死、普通炸不死」的由来）。")
                .defineInRange("chargedMultiplier", 2.0D, 0.0D, 10.0D);
        b.pop();

        b.comment("行为 AI").push("ai");
        WATER_CREEPER_SWELL_RANGE = b.comment("贴到多少格以内开始点燃引信。原版苦力怕 = 3.0。",
                        "放弃膨胀的距离会自动取「本值 + 4」，与原版 3 → 7 的比例一致。")
                .defineInRange("swellRange", 3.0D, 0.5D, 32.0D);
        WATER_CREEPER_BEACH_ASSAULT = b.comment("是否允许它跳出水面扑上岸。",
                        "true = 玩家缩在岸上会被它跳上来炸（原设计）；",
                        "false = 它只在水中活动，站在岸上就安全了。")
                .define("beachAssault", true);
        b.pop();

        b.comment("撞船 Ram").push("ram");
        WATER_CREEPER_RAM_COOLDOWN_TICKS = b.comment("两次撞船之间的最小间隔（tick）。16 tick ≈ 0.8 秒。",
                        "防止一帧内把船和玩家连撞十几次。")
                .defineInRange("cooldownTicks", 16, 0, 200);
        b.pop();

        b.pop();

        SPEC = b.build();
    }

    private HybridCreeperConfig() {
    }
}
