package com.hybridcreeper.client;

import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.entity.PoweredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 充能生物的蓝色能量外衣。
 *
 * <p>被闪电劈中的苦力怕那身跳动的蓝色光膜，就是这个东西。
 * 这里的实现让我们的两只生物也能长出来。</p>
 *
 * <h2>为什么不直接用原版的 {@code EnergySwirlLayer}</h2>
 * <p>原版那个类本身没问题，泛型约束我们也满足（{@code <T extends Entity & PowerableMob>}，
 * 见 {@link PoweredMob}）。真正卡住的是它的 {@code model()} 抽象方法 ——
 * 它要求你提供一个<b>单独烘焙的、比原模型略大的「装甲模型」</b>：</p>
 * <pre>
 *   // CreeperPowerLayer 的构造
 *   this.model = new CreeperModel<>(modelSet.bakeLayer(ModelLayers.CREEPER_ARMOR));
 * </pre>
 * <p>而 {@code ModelLayers.CREEPER_ARMOR} 是在 {@code LayerDefinitions} 里用
 * {@code new CubeDeformation(2.0F)} 注册的 —— 也就是"把每个立方体向外撑开 2 像素"，
 * 免得这层光膜和生物本体<b>共面打架</b>。</p>
 *
 * <p>问题在于这套做法要求「模型构造函数能收一个变形参数」：</p>
 * <ul>
 *   <li>{@code CreeperModel} 有 —— {@code createBodyLayer(CubeDeformation)}；</li>
 *   <li>{@code DolphinModel} <b>没有</b> —— 它的签名是 {@code createBodyLayer()}，不收参数。
 *       水下苦力怕正好用的是海豚模型，这条路直接堵死。</li>
 * </ul>
 *
 * <p>所以这里改成<b>重绘父模型本身</b>，用 {@code PoseStack.scale} 把它整体撑大一点点
 * 来代替逐立方体的变形。效果等价（都是"往外挪一点避免共面"），
 * 而且对任何模型都通用 —— 将来水下苦力怕换成自定义模型也不用改这里。</p>
 *
 * <h2>关于那点缩放偏移</h2>
 * <p>整体缩放是以实体脚下为原点放大的，所以模型顶部会比本体高出约
 * {@code 身高 × (EXPANSION - 1)} ≈ 2 格 × 0.02 = 0.04 格。
 * 这层是<b>加法混合</b>的发光层，本体就在它正下方，那 4 厘米的偏差在视觉上
 * 只是让光晕略微溢出轮廓一点点 —— 其实更好看。真要较真的话可以改成
 * 先平移到模型中心再缩放，但那样代码复杂度不值得。</p>
 *
 * <h2>贴图为什么是"通用"的</h2>
 * <p>能量着色器是这样的：</p>
 * <pre>
 *   vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
 *   if (color.a &lt; 0.1) discard;
 * </pre>
 * <p>配合 {@code blendFunc(ONE, ONE)} —— <b>纯加法混合</b>，贴图 RGB 直接决定发光亮度，
 * alpha 只负责"够不够不透明、要不要丢弃"。</p>
 *
 * <p>原版 {@code creeper_armor.png} 是按苦力怕模型的 UV 排布画的异形图
 * （只有 31% 的像素非透明，散布在模型实际用到的那几块矩形里）。
 * 我们<b>不沿用这种画法</b>，而是用一张 64×64 全幅无缝的能量纹理 —— 理由是：</p>
 * <ol>
 *   <li>模型只会采样到自己 UV 覆盖到的像素，全幅图的效果和异形图<b>完全一样</b>
 *       （没被用到的地方永远不会被采样）；</li>
 *   <li>于是同一张贴图能同时服务两只 UV 排布完全不同的生物，不用各画一张；</li>
 *   <li>而且它是<b>纯算法生成</b>的，不含任何 Mojang 素材的衍生，
 *       没有版权负担。</li>
 * </ol>
 *
 * @param <T> 实体类型。必须是"能被充能"的（{@link PoweredMob}）
 * @param <M> 该实体的模型类型
 */
@OnlyIn(Dist.CLIENT)
public class PoweredOverlayLayer<T extends Entity & PoweredMob, M extends EntityModel<T>>
        extends RenderLayer<T, M> {

    /** 与 {@code assets/hybridcreeper/textures/entity/charged_energy.png} 对应。 */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, "textures/entity/charged_energy.png");

    /** 外扩比例。1.02 = 向外撑 2%，足够躲开共面，又不至于看起来"胖了一圈"。 */
    private static final float EXPANSION = 1.02F;

    /**
     * 顶点色。{@code 0xFF808080} 即 ARGB 的「不透明 + 50% 灰」。
     *
     * <p>和原版 {@code EnergySwirlLayer} 里那个 {@code -8355712} 是同一个值
     * （{@code 0xFF808080} 按有符号 int 解读就是 {@code -8355712}）。
     * 着色器会把它乘进贴图颜色，所以贴图要画得比目标亮度<b>亮一倍</b>，
     * 由这里折半回来。</p>
     */
    private static final int OVERLAY_COLOR = 0xFF808080;

    /** 滚动速度。0.01 与原版 {@code CreeperPowerLayer#xOffset} 一致。 */
    private static final float SCROLL_SPEED = 0.01F;

    /**
     * @param renderer 父渲染器。直接传 {@code this}（渲染器构造里 `addLayer(new ...(this))`）
     */
    public PoweredOverlayLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // 没充能就什么都不画。原版对隐形实体还有额外判断，
        // 但那在 MobRenderer 的外层已经拦掉了，这里不用重复。
        if (!entity.isPowered()) {
            return;
        }

        // 滚动量：用 tickCount + 部分 tick 保证插值平滑（掉帧时也不会一跳一跳）。
        // 取模 1.0 是因为着色器按纹理坐标采样，超过 1 会绕回 —— 配合无缝贴图就是无限滚动。
        float scroll = ((float) entity.tickCount + partialTick) * SCROLL_SPEED;

        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.energySwirl(TEXTURE, scroll % 1.0F, scroll % 1.0F));

        poseStack.pushPose();
        // 外扩：见类注释里"关于那点缩放偏移"
        poseStack.scale(EXPANSION, EXPANSION, EXPANSION);
        // 直接重绘父模型 —— 它的各个部件姿态在 setupAnim 里已经摆好了，
        // 我们只是换一个 VertexConsumer 和一层略大的变换再画一遍。
        this.getParentModel().renderToBuffer(
                poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, OVERLAY_COLOR);
        poseStack.popPose();
    }
}
