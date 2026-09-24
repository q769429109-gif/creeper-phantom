package com.hybridcreeper.client.model;

import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.entity.WaterCreeperEntity;
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
 * 水下苦力怕的自定义模型：「海豚的身体 + 苦力怕的躯干 / 头 / 四条腿」。
 *
 * <p><b>本文件由工具自动生成</b>（{@code vanilla-reference/tools/gen_water_creeper_model.py}），
 * 源工程是 Blockbench 文件 {@code creeperdolphin.bbmodel}（64×64 贴图）。
 * 要改模型请改 .bbmodel 后重跑生成器，不要手改这里。</p>
 *
 * <h2>坐标换算</h2>
 * <pre>
 *   java = ( -bb.x , 24 - bb.y , bb.z )
 *   PartPose.offset = 轴心世界坐标 - 父级轴心世界坐标
 *   addBox(x,y,z,dx,dy,dz) : (x,y,z) = 立方体世界最小角 - 部件轴心世界坐标
 *   旋转 java = ( -rad(rx) , -rad(ry) , +rad(rz) )
 * </pre>
 * 生成器对每个立方体做了世界 AABB 交叉验证（最大偏差 ~1e-15）。
 *
 * <h2>为什么 body 轴心是 (0, 22, -5)</h2>
 * <p>与 {@code DolphinModel} 的 body 完全一致 —— 这样海豚身体立方体
 * 的 {@code addBox} 调用逐字相同，水中摆尾动画也能直接照搬。</p>
 *
 * <h2>旋转的立方体被提成了独立部件</h2>
 * <p>原版模型系统不支持立方体级旋转（{@code addBox} 没有旋转参数），
 * 所以苦力怕躯干 + 四条腿这几个带旋转的立方体，各自被提成了一个
 * 独立子部件，把旋转搬到 {@code PartPose} 上。</p>
 */
public class WaterCreeperModel extends HierarchicalModel<WaterCreeperEntity> {

    /**
     * 模型图层。路径是本模组自己的命名空间（{@code hybridcreeper:water_creeper}），
     * <b>不会</b>和原版的 {@code minecraft:dolphin} 图层撞车 ——
     * 原版海豚继续用它自己那份模型。
     */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, "water_creeper"), "main");

    private static final CubeDeformation DEFORM = CubeDeformation.NONE;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart creeperTorso;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;
    private final ModelPart backFin;
    private final ModelPart leftFin;
    private final ModelPart rightFin;

    public WaterCreeperModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.creeperTorso = this.body.getChild("creeper_torso");
        this.rightHindLeg = this.body.getChild("right_hind_leg");
        this.leftHindLeg = this.body.getChild("left_hind_leg");
        this.rightFrontLeg = this.body.getChild("right_front_leg");
        this.leftFrontLeg = this.body.getChild("left_front_leg");
        this.backFin = this.body.getChild("back_fin");
        this.leftFin = this.body.getChild("left_fin");
        this.rightFin = this.body.getChild("right_fin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 海豚身体（与 DolphinModel 逐字一致），也是动画容器
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(22, 0).addBox(-4.0F, -7.0F, 0.0F, 8.0F, 7.0F, 13.0F, DEFORM),
                PartPose.offset(0.0F, 22.0F, -5.0F));

        body.addOrReplaceChild("back_fin",
                CubeListBuilder.create().texOffs(51, 0).addBox(-0.5F, 3.0F, 6.0F, 1.0F, 4.0F, 5.0F, DEFORM),
                PartPose.offset(-0.5F, 3.0F, 6.0F));

        body.addOrReplaceChild("left_fin",
                CubeListBuilder.create().texOffs(48, 20).mirror().addBox(1.5F, -6.0F, 4.0F, 1.0F, 4.0F, 7.0F, DEFORM),
                PartPose.offset(1.5F, -6.0F, 4.0F));

        body.addOrReplaceChild("right_fin",
                CubeListBuilder.create().texOffs(48, 20).addBox(-2.5F, -6.0F, 4.0F, 1.0F, 4.0F, 7.0F, DEFORM),
                PartPose.offset(-2.5F, -6.0F, 4.0F));

        body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(24, 32).addBox(-4.0F, -7.0F, -8.0F, 8.0F, 8.0F, 8.0F, DEFORM),
                PartPose.offset(-4.0F, -7.0F, -8.0F));

        // ↓ 原立方体带旋转，已提成独立子部件
        body.addOrReplaceChild("creeper_torso",
                CubeListBuilder.create().texOffs(40, 48).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 1.5708F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，已提成独立子部件
        body.addOrReplaceChild("right_hind_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(-2.0F, -2.0F, 12.0F, 1.6144F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，已提成独立子部件
        body.addOrReplaceChild("left_hind_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(2.0F, -2.0F, 12.0F, 1.6144F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，已提成独立子部件
        body.addOrReplaceChild("right_front_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(-4.0F, -2.0F, 12.0F, 0.9163F, 0.0F, 1.5708F));

        // ↓ 原立方体带旋转，已提成独立子部件
        body.addOrReplaceChild("left_front_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(4.0F, -2.0F, 12.0F, 0.9163F, 0.0F, -1.5708F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * 水中游动动画 —— 逐字沿用原版 {@code DolphinModel#setupAnim}：
     * 身体的俯仰/偏航跟随头部朝向，游动时（有水平位移）叠加一段
     * 摆尾级联振荡。苦力怕的躯干与四条腿挂在 body 上，会跟着一起晃。
     */
    @Override
    public void setupAnim(WaterCreeperEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.body.xRot = headPitch * (float) (Math.PI / 180.0);
        this.body.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        // 腿的静态角度来自 PartPose，每帧必须「绝对赋值」——
        // ModelPart 的旋转不会自动复位，用 += 会逐帧累加转飞。
        float hind = 1.6144F;
        float front = 0.9163F;
        if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7) {
            this.body.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);
            // 四条腿随游动轻轻划水（幅度很小，别抢戏）
            float swing = Mth.cos(ageInTicks * 0.3F) * 0.15F;
            hind += swing;
            front -= swing;
        }
        this.leftHindLeg.xRot = hind;
        this.rightHindLeg.xRot = hind;
        this.leftFrontLeg.xRot = front;
        this.rightFrontLeg.xRot = front;
    }
}