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
 * 苦力怕海豚的自定义模型：「海豚的身体 + 苦力怕的躯干 / 头 / 四条腿」。
 *
 * <p><b>本文件由工具自动生成</b>（{@code vanilla-reference/tools/gen_water_creeper_model.py}），
 * 源工程是 Blockbench 文件 {@code creeperdolphin.bbmodel}（64×64 贴图）。
 * 要改模型/动画请改 .bbmodel 后重跑生成器，不要手改这里。</p>
 *
 * <h2>部件层级</h2>
 * <p>本文件<b>按 .bbmodel 的骨头层级 1:1 生成</b>（每个 group 一个部件），
 * 这样 .bbmodel 里的动画（animator 按骨头 uuid 索引）能直接落到对应部件上。</p>
 * <p><b>注意</b>：部件 {@code tail} 的 uuid 继承自原版海豚的尾巴骨头，
 * 但在这个模型里它装的是<b>四条腿</b> —— 名字保持与 Blockbench 工程一致，别被名字骗了。</p>
 *
 * <h2>动画</h2>
 * <p>{@link #setupAnim} 直接回放 .bbmodel 里 {@code swim} 动画的关键帧曲线
 * （线性插值），角度按 {@code java = -rad(bb)} 换算。</p>
 */
public class WaterCreeperModel extends HierarchicalModel<WaterCreeperEntity> {

    /** 模型图层：本模组私有命名空间，不会和原版 {@code minecraft:dolphin} 撞车。 */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, "water_creeper"), "main");

    private static final CubeDeformation DEFORM = CubeDeformation.NONE;
    private static final float DEG2RAD = 0.017453292F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart tail;

    public WaterCreeperModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.tail = this.body.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition _body = root.addOrReplaceChild("body",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // ↓ 原立方体带旋转，提成独立子部件
        _body.addOrReplaceChild("creeper_torso",
                CubeListBuilder.create().texOffs(40, 48).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(0.0F, -4.0F, -5.0F, 1.5708F, 0.0F, 0.0F));

        PartDefinition _back_fin = _body.addOrReplaceChild("back_fin",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，提成独立子部件
        _back_fin.addOrReplaceChild("back_fin",
                CubeListBuilder.create().texOffs(51, 0).addBox(-0.5F, 0.0F, 8.0F, 1.0F, 4.0F, 5.0F, DEFORM),
                PartPose.offsetAndRotation(0.0F, 0.1388F, -6.9199F, 0.8727F, 0.0F, 0.0F));

        PartDefinition _left_fin = _body.addOrReplaceChild("left_fin",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，提成独立子部件
        _left_fin.addOrReplaceChild("left_fin",
                CubeListBuilder.create().texOffs(48, 20).mirror().addBox(-0.5F, -4.0F, 0.0F, 1.0F, 4.0F, 7.0F, DEFORM),
                PartPose.offsetAndRotation(2.0F, -4.0F, -1.0F, 1.0472F, 0.0F, 2.0944F));

        PartDefinition _right_fin = _body.addOrReplaceChild("right_fin",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，提成独立子部件
        _right_fin.addOrReplaceChild("right_fin",
                CubeListBuilder.create().texOffs(48, 20).addBox(-0.5F, -4.0F, 0.0F, 1.0F, 4.0F, 7.0F, DEFORM),
                PartPose.offsetAndRotation(-2.0F, -4.0F, -1.0F, 1.0472F, 0.0F, -2.0944F));

        PartDefinition _tail = _body.addOrReplaceChild("tail",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，提成独立子部件
        _tail.addOrReplaceChild("left_hind_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(2.0F, -4.0F, 7.0F, 1.6144F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，提成独立子部件
        _tail.addOrReplaceChild("right_hind_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(-2.0F, -4.0F, 7.0F, 1.6144F, 0.0F, 0.0F));

        // ↓ 原立方体带旋转，提成独立子部件
        _tail.addOrReplaceChild("right_front_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(-4.0F, -4.0F, 7.0F, 0.9163F, 0.0F, 1.5708F));

        // ↓ 原立方体带旋转，提成独立子部件
        _tail.addOrReplaceChild("left_front_leg",
                CubeListBuilder.create().texOffs(24, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, DEFORM),
                PartPose.offsetAndRotation(4.0F, -4.0F, 7.0F, 0.9163F, 0.0F, -1.5708F));

        PartDefinition _head = _body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(24, 32).addBox(-4.0F, -9.0F, -13.0F, 8.0F, 8.0F, 8.0F, DEFORM)
                , PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /* ---------------- swim 动画（由 .bbmodel 关键帧导出） ---------------- */

    /** 一个循环的时长（tick）= length(秒) × 20。 */
    private static final float SWIM_LOOP_TICKS = 20.944F;

    private static final float[] BODY_T = {0.0F, 0.0524F, 0.1047F, 0.1571F, 0.2094F, 0.2618F, 0.3142F, 0.3665F, 0.4189F, 0.4712F, 0.5236F, 0.576F, 0.6283F, 0.6807F, 0.733F, 0.7854F, 0.8378F, 0.8901F, 0.9425F, 0.9948F, 1.0472F};
    private static final float[] BODY_X = {-5.7296F, -5.5894F, -5.1825F, -4.5487F, -3.7501F, -2.8648F, -1.9795F, -1.1809F, -0.5471F, -0.1402F, 0.0F, -0.1402F, -0.5471F, -1.1809F, -1.9795F, -2.8648F, -3.7501F, -4.5487F, -5.1825F, -5.5894F, -5.7296F};

    private static final float[] TAIL_T = {0.0F, 0.0524F, 0.1047F, 0.1571F, 0.2094F, 0.2618F, 0.3142F, 0.3665F, 0.4189F, 0.4712F, 0.5236F, 0.576F, 0.6283F, 0.6807F, 0.733F, 0.7854F, 0.8378F, 0.8901F, 0.9425F, 0.9948F, 1.0472F};
    private static final float[] TAIL_X = {-5.7296F, -5.4492F, -4.6353F, -3.3678F, -1.7705F, 0.0F, 1.7705F, 3.3678F, 4.6353F, 5.4492F, 5.7296F, 5.4492F, 4.6353F, 3.3678F, 1.7705F, 0.0F, -1.7705F, -3.3678F, -4.6353F, -5.4492F, -5.7296F};

    /**
     * 水中游动：直接回放 .bbmodel 的 swim 曲线（线性插值）。
     * 角度是 Blockbench 约定（度），Java 侧按 {@code xRot = -rad(bb)} 换算。
     */
    @Override
    public void setupAnim(WaterCreeperEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        float t = (ageInTicks % SWIM_LOOP_TICKS) / 20.0F;
        this.body.xRot = -sample(BODY_T, BODY_X, t) * DEG2RAD;
        this.tail.xRot = -sample(TAIL_T, TAIL_X, t) * DEG2RAD;
    }

    /** 在关键帧时间轴上做线性插值。 */
    private static float sample(float[] times, float[] values, float t) {
        int last = times.length - 1;
        if (t <= times[0]) return values[0];
        if (t >= times[last]) return values[last];
        for (int i = 1; i <= last; i++) {
            if (t <= times[i]) {
                float f = (t - times[i - 1]) / (times[i] - times[i - 1]);
                return Mth.lerp(f, values[i - 1], values[i]);
            }
        }
        return values[last];
    }

}