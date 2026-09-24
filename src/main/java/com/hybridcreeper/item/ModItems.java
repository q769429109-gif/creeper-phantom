package com.hybridcreeper.item;

import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.entity.ModEntities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册表。目前是两颗刷怪蛋。
 *
 * <h2>刷怪蛋几乎是零成本的</h2>
 * <p>原版刷怪蛋的外观不是每个生物一张贴图 —— 它只有两张公用贴图
 * （{@code minecraft:item/spawn_egg} 蛋壳 + {@code minecraft:item/spawn_egg_overlay} 斑点），
 * 靠 <b>两层 tint 染色</b>染出各自颜色。物品模型只要写：</p>
 * <pre>
 *   { "parent": "minecraft:item/template_spawn_egg" }
 * </pre>
 * <p>所以<b>不需要出任何贴图</b>，也不需要自己写 {@code ItemColor} ——
 * {@link DeferredSpawnEggItem} 内部已经挂了
 * {@code RegisterColorHandlersEvent.Item} 把颜色注册好了。</p>
 *
 * <h2>为什么不用原版 {@link SpawnEggItem}</h2>
 * <p>原版那个构造函数接收的是<b>实体类型实例</b>：</p>
 * <pre>
 *   &#64;Deprecated  // NeoForge 标注：请改用 DeferredSpawnEggItem
 *   public SpawnEggItem(EntityType&lt;? extends Mob&gt; type, int bg, int hl, Item.Properties props)
 * </pre>
 * <p>而物品注册发生在实体类型注册之后，用 {@code Supplier} 延迟取值才安全。
 * {@link DeferredSpawnEggItem} 收的是 {@code Supplier<? extends EntityType<? extends Mob>>}，
 * 并且顺带把另外三件事也办了：</p>
 * <ul>
 *   <li><b>发射器行为</b>：{@code FMLCommonSetupEvent} 里自动
 *       {@code DispenserBlock.registerBehavior(egg, ...)}；</li>
 *   <li><b>刷怪笼兼容</b>：把「实体类型 → 刷怪蛋」登记进 {@code TYPE_MAP}
 *       （{@code SpawnEggItem#byId} 会优先查这张表）；</li>
 *   <li><b>客户端 tint 染色</b>：{@code RegisterColorHandlersEvent.Item} 自动注册。</li>
 * </ul>
 */
public final class ModItems {

    /** 物品注册器，由模组主类挂到 mod 事件总线上。 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HybridCreeper.MODID);

    /* ==================================================================
     * 苦力怕幻翼刷怪蛋
     * ================================================================== */

    /**
     * 蛋壳底色：取自 {@code creeperphantom.png} 里<b>幻翼翅膀</b>的主色 {@code #405080}。
     *
     * <p>两个颜色都是从那张贴图的 1734 个非透明像素里按 16 级量化统计出来的主色，
     * 不是随手挑的 —— 一个代表翅膀（幻翼血统），一个代表身体（苦力怕血统）。</p>
     *
     * <p>参考值：原版苦力怕刷怪蛋 = {@code 0x0DA70B} / {@code 0x000000}，
     * 原版幻翼刷怪蛋 = {@code 0x43518A} / {@code 0x88FF00}。</p>
     */
    public static final int CREEPERPHANTOM_EGG_BACKGROUND = 0x405080;

    /** 斑纹色：取自同一张贴图里<b>苦力怕皮肤</b>的主色 {@code #80D070}。 */
    public static final int CREEPERPHANTOM_EGG_HIGHLIGHT = 0x80D070;

    /**
     * 苦力怕幻翼刷怪蛋。
     *
     * <p>第一个参数传的是 {@code ModEntities.CREEPER_PHANTOM} 这个
     * {@code DeferredHolder} <b>本身</b>（它实现了 {@code Supplier}），不是 {@code .get()} 的结果 ——
     * 这样取值会推迟到实际需要时才发生，避开注册顺序问题。</p>
     */
    public static final DeferredItem<SpawnEggItem> CREEPERPHANTOM_SPAWN_EGG =
            ITEMS.registerItem("creeperphantom_spawn_egg",
                    props -> new DeferredSpawnEggItem(
                            ModEntities.CREEPER_PHANTOM,
                            CREEPERPHANTOM_EGG_BACKGROUND,
                            CREEPERPHANTOM_EGG_HIGHLIGHT,
                            props),
                    new Item.Properties());

    /* ==================================================================
     * 苦力怕海豚刷怪蛋
     * ================================================================== */

    /**
     * 蛋壳底色：<b>苦力怕的绿</b> {@code #0DA70B} —— 就是原版 {@code CREEPER_SPAWN_EGG}
     * 的背景色。它终究是只苦力怕。
     */
    public static final int WATER_CREEPER_EGG_BACKGROUND = 0x0DA70B;

    /**
     * 斑纹色：<b>水的蓝</b> {@code #3F76E4} —— 原版水的颜色。
     *
     * <p>和苦力怕那颗蛋（绿底 + 黑点）放在一起，一眼就能看出这是
     * "苦力怕的水下变种"：底子一样，只是斑点从黑变成了水的蓝。</p>
     */
    public static final int WATER_CREEPER_EGG_HIGHLIGHT = 0x3F76E4;

    /** 苦力怕海豚刷怪蛋。 */
    public static final DeferredItem<SpawnEggItem> WATER_CREEPER_SPAWN_EGG =
            ITEMS.registerItem("water_creeper_spawn_egg",
                    props -> new DeferredSpawnEggItem(
                            ModEntities.WATER_CREEPER,
                            WATER_CREEPER_EGG_BACKGROUND,
                            WATER_CREEPER_EGG_HIGHLIGHT,
                            props),
                    new Item.Properties());

    private ModItems() {
    }
}
