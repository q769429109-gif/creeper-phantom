package com.phantomblast.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.phantomblast.PhantomBlast;
import com.phantomblast.client.model.PhantomBlastModel;
import com.phantomblast.entity.PhantomBlastEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * 苦力怕幻翼的渲染器。
 *
 * <p>与原版 {@code PhantomRenderer} 的结构一一对应，只换了两样东西：
 * 模型的几何数据（{@link PhantomBlastModel}）和贴图。</p>
 *
 * <h2>为什么不用 {@code PhantomRenderer} 的泛型放宽</h2>
 * <p>虽然 {@code PhantomBlastEntity} 是 {@code Phantom} 的子类，
 * 但 {@code EntityRenderers} 的注册表是按 {@code EntityType} 精确查的
 * （{@code PROVIDERS.get(entity.getType())}），所以必须有一个自己的渲染器实例，
 * 而不能指望复用原版那份。</p>
 *
 * <h2>关于眼睛发光层</h2>
 * <p>原版 {@code PhantomRenderer} 挂了一个 {@code PhantomEyesLayer}，用
 * {@code phantom_eyes.png} 以 {@code RenderType.eyes} 画自发光眼睛。
 * 那层 UV 是按<b>原版幻翼的头</b>排布的，而我们的头换成了苦力怕头（UV 偏移 32,38），
 * 直接照搬会在莫名其妙的位置冒出 4 个绿点，所以这里<b>不挂</b>眼睛层。
 * 新贴图里苦力怕脸本身就有眼睛。</p>
 */
public class PhantomBlastRenderer extends MobRenderer<PhantomBlastEntity, PhantomBlastModel> {

    /** 与 {@code src/main/resources/assets/phantomblast/textures/entity/creeperphantom.png} 对应。 */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PhantomBlast.MODID, "textures/entity/creeperphantom.png");

    public PhantomBlastRenderer(EntityRendererProvider.Context context) {
        // 阴影半径 0.75 —— 与原版幻翼一致
        super(context, new PhantomBlastModel(context.bakeLayer(PhantomBlastModel.LAYER)), 0.75F);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomBlastEntity entity) {
        return TEXTURE;
    }

    /**
     * 原版幻翼的体型缩放。{@code getPhantomSize()} 是 0~64，每级放大 15%，最大能到 10.6 倍。
     *
     * <p>下面那句 {@code translate(0, 1.3125, 0.1875)} 是原版为了让模型坐落在命中箱
     * 正确位置的补偿 —— <b>不要删</b>，删了整只会往下沉。</p>
     */
    @Override
    protected void scale(PhantomBlastEntity entity, PoseStack poseStack, float partialTick) {
        int size = entity.getPhantomSize();
        float f = 1.0F + 0.15F * (float) size;
        poseStack.scale(f, f, f);
        poseStack.translate(0.0F, 1.3125F, 0.1875F);
    }

    /**
     * 俯冲时整只随俯仰角翻转。
     *
     * <p>这层变换是"会飞的怪物"手感的关键：俯冲下去时模型会头朝下扎。</p>
     */
    @Override
    protected void setupRotations(PhantomBlastEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
    }
}
