package com.hybridcreeper.client.model;

import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.entity.HybridCreeperEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 苦力怕幻翼的自定义模型：「幻翼的翅膀 + 苦力怕的身子」。
 *
 * <p><b>本文件由工具自动生成</b>（{@code vanilla-reference/tools/bbmodel_to_java.py}），
 * 源工程是 Blockbench 文件 {@code phantomcreeper.bbmodel}。
 * 要改模型请改 .bbmodel 后重跑生成器，不要手改这里。</p>
 *
 * <h2>坐标换算是怎么来的</h2>
 * <p>Blockbench 的 Modded Entity 格式与 Minecraft Java 实体模型之间是确定的线性映射：</p>
 * <pre>
 *   java.x = -bb.x        java.y = 24 - bb.y        java.z = bb.z
 *   旋转：java.xRot = -rad(bb.rx)   java.yRot = -rad(bb.ry)   java.zRot = +rad(bb.rz)
 * </pre>
 * <p>生成器对 10 个立方体逐个做了世界 AABB 交叉验证，最大偏差 1.8e-15（机器精度）。</p>
 *
 * <h2>为什么有 {@code body_pivot} / {@code xxx_leg_pivot} 这类部件</h2>
 * <p>原版模型系统里 <b>立方体不能自带旋转</b>——{@code CubeListBuilder.addBox()} 没有任何旋转参数，
 * 只有 {@code PartPose}（部件级）才有。而主人的模型里有 5 个立方体带 90°/180° 旋转
 * （躯干横躺、四条腿）。所以生成器把这些立方体各自提成了一个独立的「合成子部件」，
 * 把旋转挪到 {@code PartPose} 上。位置和外观完全等价。</p>
 *
 * <p>顺带一提：Blockbench 自己的 "Modded Entity → Java" 导出会<b>直接丢掉</b>立方体级旋转，
 * 所以如果直接用它导出，躯干会立起来、腿会乱掉。</p>
 */
public class HybridCreeperModel extends HierarchicalModel<HybridCreeperEntity> {

    /**
     * 模型图层：客户端启动期注册，渲染器用 {@code ctx.bakeLayer(LAYER)} 取。
     *
     * <p>路径是本模组自己的命名空间（{@code hybridcreeper:creeperphantom}），
     * <b>不会</b>和原版的 {@code minecraft:phantom} 图层撞车 ——
     * 原版幻翼继续用它自己那份模型。</p>
     */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, "creeperphantom"), "main");

    /** 无膨胀。苦力怕充能层那种外扩 2px 的效果用的是 {@code new CubeDeformation(2.0F)}。 */
    private static final CubeDeformation DEFORM = CubeDeformation.NONE;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tailBase;
    private final ModelPart tailTip;
    private final ModelPart leftWingBase;
    private final ModelPart leftWingTip;
    private final ModelPart rightWingBase;
    private final ModelPart rightWingTip;

    public HybridCreeperModel(ModelPart root) {
        this.root = root;
        ModelPart body = root.getChild("body");
        this.body = body;
        this.head = body.getChild("head");
        this.tailBase = body.getChild("tail_base");
        this.leftWingBase = body.getChild("left_wing_base");
        this.rightWingBase = body.getChild("right_wing_base");
        this.tailTip = this.tailBase.getChild("tail_tip");
        this.leftWingTip = this.leftWingBase.getChild("left_wing_tip");
        this.rightWingTip = this.rightWingBase.getChild("right_wing_tip");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ---------------- 躯干 ----------------
        // 部件 pivot (0,0,0)，静态俯仰 -0.1 rad（与原版幻翼一致）
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.1F, 0.0F, 0.0F));

        // 苦力怕躯干：8x12x4 的竖立方体，绕 X 轴 +90° 放平（原 bb 旋转 -90°）
        body.addOrReplaceChild("body_pivot",
                CubeListBuilder.create().texOffs(0, 48).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(0.0F, -1.0F, -8.0F, 1.570796F, 0.0F, 0.0F));

        // ---------------- 头 ----------------
        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(32, 38).addBox(-4.0F, -7.0F, -8.0F, 8.0F, 8.0F, 8.0F, DEFORM),
                PartPose.offsetAndRotation(0.0F, 1.0F, -7.0F, 0.2F, 0.0F, 0.0F));

        // ---------------- 前腿（挂在 tail_base 上，跟着尾巴摆） ----------------
        PartDefinition tailBase = body.addOrReplaceChild("tail_base",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, -2.0F, 1.0F));

        tailBase.addOrReplaceChild("right_front_leg_pivot",
                CubeListBuilder.create().texOffs(32, 54).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(-2.0F, 3.0F, 3.0F, 1.570796F, 0.0F, 0.0F));

        tailBase.addOrReplaceChild("left_front_leg_pivot",
                CubeListBuilder.create().texOffs(32, 54).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(2.0F, 3.0F, 3.0F, 1.570796F, 0.0F, 0.0F));

        // ---------------- 后腿（挂在 tail_tip 上，摆动幅度是前腿的两倍） ----------------
        PartDefinition tailTip = tailBase.addOrReplaceChild("tail_tip",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, -0.5F, 6.0F));

        tailTip.addOrReplaceChild("right_hind_leg_pivot",
                CubeListBuilder.create().texOffs(32, 54).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(-2.0F, -0.5F, -3.0F, 1.570796F, 0.0F, -3.141593F));

        tailTip.addOrReplaceChild("left_hind_leg_pivot",
                CubeListBuilder.create().texOffs(32, 54).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(2.0F, -0.5F, -3.0F, 1.570796F, 0.0F, -3.141593F));

        // ---------------- 左翼 ----------------
        PartDefinition leftWingBase = body.addOrReplaceChild("left_wing_base",
                CubeListBuilder.create()
                        .texOffs(23, 12).addBox(0.0F, 0.0F, 0.0F, 6.0F, 2.0F, 9.0F, DEFORM),
                PartPose.offsetAndRotation(2.0F, -2.0F, -8.0F, 0.0F, 0.0F, 0.1F));

        leftWingBase.addOrReplaceChild("left_wing_tip",
                CubeListBuilder.create()
                        .texOffs(16, 24).addBox(0.0F, 0.0F, 0.0F, 13.0F, 1.0F, 9.0F, DEFORM),
                PartPose.offsetAndRotation(6.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1F));

        // ---------------- 右翼（镜像 UV） ----------------
        PartDefinition rightWingBase = body.addOrReplaceChild("right_wing_base",
                CubeListBuilder.create()
                        .texOffs(23, 12).mirror().addBox(-6.0F, 0.0F, 0.0F, 6.0F, 2.0F, 9.0F, DEFORM),
                PartPose.offsetAndRotation(-3.0F, -2.0F, -8.0F, 0.0F, 0.0F, -0.1F));

        rightWingBase.addOrReplaceChild("right_wing_tip",
                CubeListBuilder.create()
                        .texOffs(16, 24).mirror().addBox(-13.0F, 0.0F, 0.0F, 13.0F, 1.0F, 9.0F, DEFORM),
                PartPose.offsetAndRotation(-6.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * 动画与原版幻翼<b>完全一致</b>：扇翅 + 摆尾。
     *
     * <p>关键点：苦力怕的四条腿挂在 {@code tail_base} / {@code tail_tip} 上，
     * 所以它们会跟着尾巴一起前后摆动，看起来像悬空划水——这是刻意的设计，
     * 没有额外写腿部动画。</p>
     */
    @Override
    public void setupAnim(HybridCreeperEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float f = ((float) entity.getUniqueFlapTickOffset() + ageInTicks) * 7.448451F * (float) (Math.PI / 180.0);
        float wing = Mth.cos(f) * 16.0F * (float) (Math.PI / 180.0);
        float tail = -(5.0F + Mth.cos(f * 2.0F) * 5.0F) * (float) (Math.PI / 180.0);

        this.leftWingBase.zRot = wing;
        this.leftWingTip.zRot = wing;
        this.rightWingBase.zRot = -wing;
        this.rightWingTip.zRot = -wing;
        this.tailBase.xRot = tail;
        this.tailTip.xRot = tail;
    }
}
