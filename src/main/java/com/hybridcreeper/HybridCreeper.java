package com.hybridcreeper;

import com.hybridcreeper.config.HybridCreeperConfig;
import com.hybridcreeper.entity.ModEntities;
import com.hybridcreeper.entity.WaterCreeperEntity;
import com.hybridcreeper.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * 杂交苦力怕 Hybrid Creeper —— 模组主入口。
 *
 * <h2>这个模组做什么</h2>
 * <p>新增一个独立生物 <b>苦力怕幻翼（Creeper Phantom，{@code hybridcreeper:creeperphantom}）</b>：
 * 行为、属性、掉落、音效、生成方式全部与原版幻翼一致，唯一区别是
 * <b>它俯冲撕咬命中玩家时会在命中点引爆一个苦力怕等级的爆炸</b>。
 * 模型与贴图也是自定义的（幻翼的翅膀 + 苦力怕的身子）。</p>
 *
 * <p><b>原版幻翼完全不受影响</b> —— 它依然是原版的行为、原版的模型、原版的生成。
 * 两者可以同时存在于同一个世界里。</p>
 *
 * <h2>注册流程</h2>
 * <ul>
 *   <li>{@link ModEntities#ENTITY_TYPES} —— 实体类型（mod 事件总线）；</li>
 *   <li>{@link ModItems#ITEMS} —— 物品，目前是刷怪蛋（mod 事件总线）；</li>
 *   <li>{@link #onEntityAttributeCreation} —— 实体属性（mod 事件总线，
 *       在注册之后、common setup 之前触发）；</li>
 *   <li>{@link #onBuildCreativeTabContents} —— 把刷怪蛋塞进创造模式物品栏（mod 事件总线）；</li>
 *   <li>{@code HybridCreeperSpawnHook} —— 自然生成器注入（游戏事件总线）；</li>
 *   <li>{@code SwoopExplosionHandler} —— 俯冲引爆逻辑（游戏事件总线）；</li>
 *   <li>{@code HybridCreeperClient} —— 模型图层与渲染器（mod 事件总线，仅客户端）。</li>
 * </ul>
 */
@Mod(HybridCreeper.MODID)
public class HybridCreeper {

    public static final String MODID = "hybridcreeper";

    public HybridCreeper(IEventBus modEventBus, ModContainer modContainer) {
        // 实体类型注册。DeferredRegister 必须挂到 mod 事件总线上才会真正执行。
        ModEntities.ENTITY_TYPES.register(modEventBus);
        // 物品注册（刷怪蛋）。
        ModItems.ITEMS.register(modEventBus);

        // 实体属性。NeoForge 不允许用 @EventBusSubscriber 之外的静态注册方式给自定义实体配属性，
        // 必须在这个事件里 put，否则实体生成时会因为查不到 AttributeSupplier 而崩。
        modEventBus.addListener(HybridCreeper::onEntityAttributeCreation);

        // 把刷怪蛋放进创造模式的「刷怪蛋」标签页。
        modEventBus.addListener(HybridCreeper::onBuildCreativeTabContents);

        // 苦力怕海豚的生成位置规则（水中 + 阴暗环境）。
        modEventBus.addListener(HybridCreeper::onRegisterSpawnPlacements);

        // NeoForge 1.21.1 中 ModLoadingContext 已不再提供 registerConfig，
        // 统一走 ConfigTracker.INSTANCE.registerConfig(Type, IConfigSpec, ModContainer)。
        // 用 COMMON 而非 SERVER：配置文件落在 config/hybridcreeper-common.toml，
        // 单机与专用服务器都是同一个固定路径，方便用户直接改。
        ConfigTracker.INSTANCE.registerConfig(
                ModConfig.Type.COMMON,
                HybridCreeperConfig.SPEC,
                modContainer);
    }

    /**
     * 注册苦力怕幻翼的属性。
     *
     * <p>用 {@code Monster.createMonsterAttributes()} —— 这正是原版幻翼用的那一份：
     * {@code Phantom} 自身<b>没有</b>覆写 {@code createAttributes}，所以幻翼的属性
     * 就是 {@code Monster} 的默认值（生命 20、跟随范围 16、移动速度 0.2、
     * 飞行速度 0.4、攻击伤害由体型动态设成 {@code 6 + 体型}）。
     * 属性与幻翼完全一致。</p>
     *
     * <p>顺带一提：幻翼的生命 20 + 无爆炸抗性，被威力 3.0 的近身爆炸正面命中大约吃 49 点伤害，
     * <b>必死</b>。所以配置里的 {@code phantomImmune} 默认是 true，让它炸完还能继续盘旋。</p>
     */
    private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CREEPER_PHANTOM.get(), Monster.createMonsterAttributes().build());
        event.put(ModEntities.WATER_CREEPER.get(), WaterCreeperEntity.createAttributes().build());
    }

    /**
     * 把刷怪蛋加进创造模式的「刷怪蛋」标签页。
     *
     * <p>{@link BuildCreativeModeTabContentsEvent} 是 <b>mod 事件总线</b>事件
     * （它 implements {@code IModBusEvent}），所以这里用 {@code modEventBus.addListener}。</p>
     *
     * <p>{@code event.accept(ItemLike)} 是 {@code CreativeModeTab.Output} 接口上的
     * default 方法，等价于以 {@code PARENT_AND_SEARCH_TABS} 可见性加入 ——
     * 也就是在标签页里和搜索里都能找到。</p>
     *
     * <p>物品会展现在标签页<b>末尾</b>（原版那批刷怪蛋之后）。想在指定位置插入可以用
     * {@code insertAfter} / {@code insertBefore}。</p>
     */
    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.CREEPERPHANTOM_SPAWN_EGG.get());
            event.accept(ModItems.WATER_CREEPER_SPAWN_EGG.get());
        }
    }

    /**
     * 注册苦力怕海豚的生成位置规则。
     *
     * <p>三个要素，缺一不可：</p>
     * <ol>
     *   <li><b>位置类型</b> {@code SpawnPlacementTypes.IN_WATER} —— 生成点必须是水方块
     *       （海豚、溺尸、守卫者用的都是它）；</li>
     *   <li><b>高度图</b> {@code MOTION_BLOCKING_NO_LEAVES} —— 与其它水生生物一致；</li>
     *   <li><b>附加谓词</b> —— 见下面 {@link #checkWaterCreeperSpawnRules}。</li>
     * </ol>
     *
     * <h2>为什么不用 {@code Monster::checkMonsterSpawnRules}</h2>
     * <p>它的第二个参数是 {@code ServerLevelAccessor}，而
     * {@code SpawnPlacements.SpawnPredicate} 只给 {@code LevelAccessor} ——
     * 方法引用的参数类型对不上，编译不过。所以这里自己写一份，把 {@code Monster}
     * 里那两段判定（难度 + 亮度）原样搬过来，再补一条自己的条件。</p>
     */
    private static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.WATER_CREEPER.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HybridCreeper::checkWaterCreeperSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /**
     * 苦力怕海豚能否在这个位置生成。
     *
     * <p>前两条逐字对标 {@code Monster#checkMonsterSpawnRules}，实现需求里
     * <b>「与普通苦力怕相似，在夜晚或阴暗的环境中生成」</b>：</p>
     * <ul>
     *   <li>难度不是和平；</li>
     *   <li>亮度够暗（{@code Monster#isDarkEnoughToSpawn}，阈值随维度难度浮动）。</li>
     * </ul>
     *
     * <p>第三条是我加的：<b>头顶那一格也必须是水</b>。它同时解决两件事 ——</p>
     * <ul>
     *   <li>不会在"水面正下方一格"生成然后半个身子探出水面，那看着像搁浅；</li>
     *   <li>浅水（1 格深）刷不出来。而深水区本来就暗，正好落在"阴暗水域"的设定里。</li>
     * </ul>
     *
     * <p>注意这里<b>没有</b>调用 {@code Mob#checkMobSpawnRules}（原版怪物规则里的第三条）。
     * 它检查"下方方块是否 {@code isValidSpawn}"，那是给陆地生物准备的地面判定 ——
     * 水下方块（水、沙、海草）默认都不通过，照搬的话它永远刷不出来。</p>
     */
    private static boolean checkWaterCreeperSpawnRules(EntityType<WaterCreeperEntity> type,
                                                      LevelAccessor level,
                                                      MobSpawnType spawnType,
                                                      BlockPos pos,
                                                      RandomSource random) {
        if (!(level instanceof ServerLevelAccessor serverLevel)) {
            return false;
        }
        if (serverLevel.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (!MobSpawnType.ignoresLightRequirements(spawnType)
                && !Monster.isDarkEnoughToSpawn(serverLevel, pos, random)) {
            return false;
        }
        // 全身没在水里
        return level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.above()).is(FluidTags.WATER);
    }
}
