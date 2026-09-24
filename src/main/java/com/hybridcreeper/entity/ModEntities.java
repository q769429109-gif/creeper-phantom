package com.hybridcreeper.entity;

import com.hybridcreeper.HybridCreeper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型注册表。
 *
 * <h2>EntityType.Builder 的每一项都逐条抄自原版幻翼</h2>
 * <p>原版 {@code EntityType.PHANTOM} 的定义是：</p>
 * <pre>
 *   EntityType.Builder.of(Phantom::new, MobCategory.MONSTER)
 *       .sized(0.9F, 0.5F)          // 命中箱 0.9 × 0.5 格（很扁，因为它是"飞行的"）
 *       .eyeHeight(0.175F)
 *       .passengerAttachments(0.3375F)
 *       .ridingOffset(-0.125F)
 *       .clientTrackingRange(8)     // 客户端 8 个区块内同步
 * </pre>
 * <p>下面每一项都对齐了，唯一区别是工厂方法指向 {@code HybridCreeperEntity::new}。</p>
 *
 * <p><b>注意这里没有调 {@code .noSummon()}</b>——原版幻翼也没有。
 * 所以 {@code /summon hybridcreeper:creeperphantom ~ ~10 ~} 可以直接用，
 * 这也是调试时最省事的生成方式（自然生成要连续 3 天不睡觉）。</p>
 *
 * <h2>关于 {@code build(String)} 的参数</h2>
 * <p>那个字符串<b>不是</b>实体的注册名 —— 注册名由
 * {@code DeferredRegister#register("creeperphantom", ...)} 决定。
 * {@code build()} 的参数只用于数据修复器 {@code References.ENTITY_TREE} 的引用登记。
 * 传完整的 {@code 命名空间:路径} 是惯例写法。</p>
 */
public final class ModEntities {

    /** 实体注册名（不含命名空间）。 */
    public static final String CREEPER_PHANTOM_NAME = "creeperphantom";

    /** 本模组的实体类型注册器，由主类挂到 mod 事件总线上。 */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HybridCreeper.MODID);

    /**
     * 苦力怕幻翼。
     *
     * <p>{@code EntityType.Builder.<HybridCreeperEntity>of(...)} 这里显式写了类型见证：
     * 方法引用 {@code HybridCreeperEntity::new} 的形参是
     * {@code EntityType<? extends Phantom>}，与目标函数式接口
     * {@code EntityFactory<HybridCreeperEntity>} 要求的 {@code EntityType<HybridCreeperEntity>}
     * 并不完全相同（泛型不协变），靠逆变规则是可以推断的，
     * 但显式写出更省得编译器抱怨。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<HybridCreeperEntity>> CREEPER_PHANTOM =
            ENTITY_TYPES.register(CREEPER_PHANTOM_NAME, () -> EntityType.Builder
                    .<HybridCreeperEntity>of(HybridCreeperEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.5F)
                    .eyeHeight(0.175F)
                    .passengerAttachments(0.3375F)
                    .ridingOffset(-0.125F)
                    .clientTrackingRange(8)
                    .build(ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, CREEPER_PHANTOM_NAME)
                            .toString()));

    /* ==================================================================
     * 苦力怕海豚
     * ================================================================== */

    /** 实体注册名（不含命名空间）。 */
    public static final String WATER_CREEPER_NAME = "water_creeper";

    /**
     * 苦力怕海豚。
     *
     * <h2>尺寸为什么抄海豚</h2>
     * <p>当前阶段用的是原版海豚模型（{@code DolphinModel}），所以命中箱先跟着海豚走
     * （{@code sized(0.9F, 0.6F).eyeHeight(0.3F)}），不然会出现
     * "模型的嘴在箱子里、尾巴穿出箱子外"的错位感。等换自定义模型时再一起改。</p>
     *
     * <h2>为什么是 {@code MONSTER} 而不是 {@code WATER_CREATURE}</h2>
     * <p>{@code WATER_CREATURE} 的刷新上限只有 <b>5</b>，而且会被海豚、鱿鱼先占满 ——
     * 那样它在海里几乎刷不出来。{@code MONSTER} 上限 70，空间大得多。</p>
     * <p>这正是原版 {@code Drowned}（溺尸）的选择：{@code MONSTER} 分类 +
     * {@code IN_WATER} 生成位置限制，两头都占。所以"只在水中生成"由
     * {@link net.minecraft.world.entity.SpawnPlacementTypes#IN_WATER} 保证，
     * 和刷新池分类是两件独立的事。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<WaterCreeperEntity>> WATER_CREEPER =
            ENTITY_TYPES.register(WATER_CREEPER_NAME, () -> EntityType.Builder
                    .<WaterCreeperEntity>of(WaterCreeperEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.6F)
                    .eyeHeight(0.3F)
                    .clientTrackingRange(10)
                    .build(ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, WATER_CREEPER_NAME)
                            .toString()));

    private ModEntities() {
    }
}
