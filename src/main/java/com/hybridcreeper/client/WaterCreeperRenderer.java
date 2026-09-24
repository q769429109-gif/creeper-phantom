package com.hybridcreeper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.client.model.WaterCreeperModel;
import com.hybridcreeper.entity.WaterCreeperEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 水下苦力怕的渲染器 —— 使用自定义模型
 * {@link WaterCreeperModel}（海豚身体 + 苦力怕躯干 / 头 / 四条腿）。
 *
 * <h2>模型与贴图</h2>
 * <ul>
 *   <li>几何来自自绘的 Blockbench 工程 {@code creeperdolphin.bbmodel}，
 *       由 {@code gen_water_creeper_model.py} 反向换算成 Java；</li>
 *   <li>贴图是本模组自己的 {@code hybridcreeper:textures/entity/water_creeper.png}，
 *       <b>不再借用原版海豚</b>；</li>
 *   <li>图层是本模组私有命名空间（{@code hybridcreeper:water_creeper}），
 *       与原版 {@code minecraft:dolphin} 互不影响 —— 原版海豚保持原样。</li>
 * </ul>
 *
 * <h2>膨胀动画</h2>
 * <p>模型自身没有引信这个概念，所以膨胀得我们自己画。缩放曲线<b>逐字抄自
 * {@code CreeperRenderer#scale}</b>：</p>
 * <pre>
 *   f  = swelling                                    // 0 → 1
 *   f1 = 1 + sin(f * 100) * f * 0.01                 // 高频抖动，像在发抖
 *   f  = (f^2)^2                                      // 四次方，前 80% 几乎看不出来，最后猛地胀起来
 *   scale( (1 + f * 0.4) * f1,
 *          (1 + f * 0.1) / f1,
 *          (1 + f * 0.4) * f1 )
 * </pre>
 * <p>所以它会在引信走到最后 20% 时才明显膨胀 —— 这个"最后一刻才看出来"的节奏
 * 是原版苦力怕最吓人的地方，不该改。</p>
 *
 * <p>另外还保留了原版的<b>闪白层</b>（{@code getWhiteOverlayProgress}）：
 * 引信期间每隔几 tick 闪一下白，那是玩家判断"还剩多久"的视觉信号。</p>
 *
 * <h2>充能层</h2>
 * <p>挂了一个 {@link PoweredOverlayLayer}：被闪电劈中后裹上滚动的蓝色能量。
 * 那层不依赖模型变形，对任何模型通用。</p>
 */
public class WaterCreeperRenderer extends MobRenderer<WaterCreeperEntity, WaterCreeperModel> {

    /** 本模组自绘的水下苦力怕贴图（64×64，来自 creeperdolphin.png）。 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, "textures/entity/water_creeper.png");

    public WaterCreeperRenderer(EntityRendererProvider.Context context) {
        // 阴影半径 0.6 —— 与海豚一致（命中箱尺寸也跟海豚走，见 ModEntities）
        super(context, new WaterCreeperModel(context.bakeLayer(WaterCreeperModel.LAYER)), 0.6F);

        // 被闪电劈中后的蓝色能量外衣。这层对模型没有任何要求。
        this.addLayer(new PoweredOverlayLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WaterCreeperEntity entity) {
        return TEXTURE;
    }

    /**
     * 引信膨胀。曲线与 {@code CreeperRenderer#scale} 完全一致，见类注释。
     */
    @Override
    protected void scale(WaterCreeperEntity entity, PoseStack poseStack, float partialTick) {
        float swell = entity.getSwelling(partialTick);
        float jitter = 1.0F + Mth.sin(swell * 100.0F) * swell * 0.01F;
        swell = Mth.clamp(swell, 0.0F, 1.0F);
        swell *= swell;
        swell *= swell;
        float wide = (1.0F + swell * 0.4F) * jitter;
        float tall = (1.0F + swell * 0.1F) / jitter;
        poseStack.scale(wide, tall, wide);
    }

    /**
     * 引信期间的闪白强度。原版 {@code CreeperRenderer#getWhiteOverlayProgress} 同款：
     * 用 {@code (int)(f * 10) % 2} 造出"亮两 tick 灭两 tick"的频闪。
     */
    @Override
    protected float getWhiteOverlayProgress(WaterCreeperEntity entity, float partialTick) {
        float swell = entity.getSwelling(partialTick);
        return (int) (swell * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swell, 0.5F, 1.0F);
    }
}
