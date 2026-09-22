package com.hybridcreeper.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.hybridcreeper.entity.WaterCreeperEntity;
import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 水下苦力怕的渲染器 —— <b>当前阶段直接复用原版海豚模型与贴图</b>。
 *
 * <h2>为什么能直接套海豚模型</h2>
 * <p>{@code DolphinModel} 的泛型是 {@code DolphinModel<T extends Entity>} ——
 * 它只要一个"有速度"的实体，不关心具体类型。所以换成我们的实体不需要改一行模型代码：</p>
 * <ul>
 *   <li>几何直接取原版图层 {@code ModelLayers.DOLPHIN}（启动时已经烘焙好了，
 *       我们<b>不需要</b>再注册一遍图层）；</li>
 *   <li>贴图直接用 {@code minecraft:textures/entity/dolphin.png}；</li>
 *   <li>动画（摆尾 + 身体俯仰）由 {@code DolphinModel#setupAnim} 自己根据
 *       {@code getDeltaMovement()} 算，正好适配它 1.5 的高速游动。</li>
 * </ul>
 * <p>换自定义模型时，把这两行换成自己的图层和贴图即可，膨胀逻辑不用动。</p>
 *
 * <h2>膨胀动画</h2>
 * <p>海豚模型没有引信这个概念，所以膨胀得我们自己画。缩放曲线<b>逐字抄自
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
 */
public class WaterCreeperRenderer extends MobRenderer<WaterCreeperEntity, DolphinModel<WaterCreeperEntity>> {

    /** 临时借用原版海豚贴图。换自定义模型时连同这一行一起改。 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/dolphin.png");

    public WaterCreeperRenderer(EntityRendererProvider.Context context) {
        // 阴影半径 0.6 —— 与海豚一致（命中箱尺寸也跟海豚走，见 ModEntities）
        super(context, new DolphinModel<>(context.bakeLayer(ModelLayers.DOLPHIN)), 0.6F);
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
