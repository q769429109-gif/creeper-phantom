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
     * <h2>尺寸</h2>
     * <p>命中箱沿用原版海豚的 {@code sized(0.9F, 0.6F).eyeHeight(0.3F)} ——
     * 自定义模型（海豚身 + 苦力怕的躯干/头/四条腿）整体轮廓与海豚相近，沿用它手感最自然。</p>
     *
     * <h2>为什么是 {@code WATER_CREATURE}（和墨鱼同一个刷新池）</h2>
     * <p>这是主人 2026-09-24 指定的：<b>生成概率和墨鱼一样</b>。
     * 刷新分类决定的是"和谁抢刷新名额"，要和墨鱼同档就必须进同一个池子：</p>
     * <ul>
     *   <li>上限 <b>5</b>（与墨鱼、海豚、鱿鱼共用）—— 所以它是"常见但不泛滥"的量级；</li>
     *   <li>{@code isFriendly() == true}，于是刷新只在"刷动物"那一 tick 发生
     *       （{@code isSpawningAnimals()}，每 400 tick 一次），节奏与墨鱼完全一致；</li>
     *   <li>墨鱼的位置判定是 {@code IN_WATER}，我们同样是
     *       {@code IN_WATER}（见 {@code HybridCreeper#onRegisterSpawnPlacements}）。</li>
     * </ul>
     * <p>Java 类本身仍继承 {@code Monster}（它确实是个敌对生物）——
     * <b>刷新分类与 Java 基类是两件独立的事</b>，分类只管"进哪个池子"。</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<WaterCreeperEntity>> WATER_CREEPER =
            ENTITY_TYPES.register(WATER_CREEPER_NAME, () -> EntityType.Builder
                    .<WaterCreeperEntity>of(WaterCreeperEntity::new, MobCategory.WATER_CREATURE)
                    .sized(0.9F, 0.6F)
                    .eyeHeight(0.3F)
                    .clientTrackingRange(10)
                    .build(ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, WATER_CREEPER_NAME)
                            .toString()));

    private ModEntities() {
    }
}
