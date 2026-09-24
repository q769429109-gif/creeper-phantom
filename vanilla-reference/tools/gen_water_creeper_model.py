#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
creeperdolphin.bbmodel  ->  WaterCreeperModel.java

水下苦力怕的自定义模型生成器（海豚身体/鳍 + 苦力怕躯干/头/四条腿）。

为什么单独写一个、不复用 bbmodel_to_java.py 的 emit_java：
  bbmodel_to_java.py 的 emit 是给「幻翼+苦力怕」硬编码的（翅膀字段、Phantom 泛型、
  扇翅动画）。本模型的部件集合、动画（水中摆尾）完全不同，所以这里只复用它的
  坐标换算与 AABB 交叉验证思路，emit 部分重写。

与 bbmodel_to_java.py 的两处适配：
  1. 本模型的 group 全是「无名字、无 origin、无旋转」的纯文件夹分组，
     所以把所有立方体拍平到同一个 body 容器下（不需要合成子部件层级）；
  2. 动画用的 body 容器轴心取原版海豚的 (0, 22, -5) —— 这样 body 立方体的
     addBox 与 DolphinModel 逐字一致，水中摆尾动画也能照搬海豚。

Blockbench 的 Modded Entity 格式（flip_y = true）到 Java 的换算：
    世界坐标   java = ( -bb.x , 24 - bb.y , bb.z )
    部件轴心   PartPose.offset   = 轴心世界坐标 - 父级轴心世界坐标
    立方体     addBox(x,y,z,dx,dy,dz) : (x,y,z) = 立方体世界最小角 - 部件轴心世界坐标
    旋转       java = ( -rad(rx) , -rad(ry) , +rad(rz) )
"""

import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REF = os.path.dirname(HERE)
BB_PATH = os.path.join(REF, "bbmodel", "creeperdolphin.bbmodel")
OUT_JAVA = os.path.join(REF, "bbmodel", "WaterCreeperModel.java.txt")

# 动画容器（body）的绝对轴心 —— 与 DolphinModel 的 body 一致
BODY_PIVOT = (0.0, 22.0, -5.0)

# 元素名 -> Java 部件名（去重 / 去掉无意义的重名）
RENAME = {
    # 带旋转的那个 8x12x4 "body" 其实是苦力怕躯干，跟海豚身体重名了
    "creeper_torso": "creeper_torso",
}


def rad(v):
    return math.radians(v)


def rot_mat(rx, ry, rz):
    """Rz·Ry·Rx 复合前的 Rx·Ry·Rz（与 MC rotationZYX 一致）。输入弧度。"""
    cX, sX, cY, sY, cZ, sZ = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = [[1, 0, 0], [0, cX, -sX], [0, sX, cX]]
    Ry = [[cY, 0, sY], [0, 1, 0], [-sY, 0, cY]]
    Rz = [[cZ, -sZ, 0], [sZ, cZ, 0], [0, 0, 1]]

    def mul(A, B):
        return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mul(Rx, mul(Ry, Rz))


def apply(R, p):
    return [sum(R[i][k] * p[k] for k in range(3)) for i in range(3)]


def corners(x, y, z, dx, dy, dz):
    return [(x + a * dx, y + b * dy, z + c * dz) for a in (0, 1) for b in (0, 1) for c in (0, 1)]


def aabb(pts):
    return ([min(p[i] for p in pts) for i in range(3)],
            [max(p[i] for p in pts) for i in range(3)])


def load():
    with open(BB_PATH, encoding="utf-8") as f:
        return json.load(f)


def convert(bb):
    """返回 (body_cube, plain, rotated)。全部坐标已相对 BODY_PIVOT。"""
    body_cube = None
    plain, rotated = [], []
    for el in bb["elements"]:
        frm, to = el["from"], el["to"]
        dx, dy, dz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
        tex = tuple(el.get("uv_offset") or [0, 0])
        mir = bool(el.get("mirror_uv"))
        rot = el.get("rotation") or [0, 0, 0]
        co = el.get("origin") or [0, 0, 0]
        name = el["name"]
        # 立方体世界最小角（java）
        abs_min = (-to[0], 24.0 - to[1], frm[2])
        if any(abs(v) > 1e-6 for v in rot):
            abs_piv = (-co[0], 24.0 - co[1], co[2])
            local_min = (co[0] - to[0], co[1] - to[1], frm[2] - co[2])
            jrot = (-rad(rot[0]), -rad(rot[1]), rad(rot[2]))
            rel_piv = tuple(abs_piv[i] - BODY_PIVOT[i] for i in range(3))
            if name == "body":
                name = "creeper_torso"
            rotated.append(dict(name=name, min=local_min, size=(dx, dy, dz),
                                tex=tex, mir=mir, rot=jrot, piv=rel_piv))
        else:
            rel_min = tuple(abs_min[i] - BODY_PIVOT[i] for i in range(3))
            if name == "body":
                body_cube = dict(min=rel_min, size=(dx, dy, dz), tex=tex, mir=mir)
            else:
                plain.append(dict(name=name, min=rel_min, size=(dx, dy, dz), tex=tex, mir=mir))
    return body_cube, plain, rotated


def verify(bb, body_cube, plain, rotated):
    """逐立方体把 Java 世界 AABB 与 Blockbench 世界 AABB 对照。"""
    print("=== 逐立方体世界 AABB 交叉验证 ===")
    # java 世界 AABB（plain 的 min 是相对 body 轴心的，要加回轴心才是世界坐标）
    def world_min(m):
        return [m[i] + BODY_PIVOT[i] for i in range(3)]

    java = {}
    if body_cube:
        java["body"] = aabb(corners(*(world_min(body_cube["min"]) + list(body_cube["size"]))))
    for p in plain:
        java[p["name"]] = aabb(corners(*(world_min(p["min"]) + list(p["size"]))))
    for r in rotated:
        R = rot_mat(*r["rot"])
        base = [r["piv"][i] + BODY_PIVOT[i] for i in range(3)]  # 轴心的世界坐标
        pts = []
        for c in corners(*(list(r["min"]) + list(r["size"]))):
            q = apply(R, c)
            pts.append([q[i] + base[i] for i in range(3)])
        java[r["name"]] = aabb(pts)

    # bb 世界 AABB
    bb_map = {}
    for el in bb["elements"]:
        frm, to = el["from"], el["to"]
        nm = el["name"]
        if nm == "body" and any(abs(v) > 1e-6 for v in (el.get("rotation") or [0, 0, 0])):
            nm = "creeper_torso"
        co = el.get("origin") or [0, 0, 0]
        rot = el.get("rotation") or [0, 0, 0]
        pts = corners(frm[0], frm[1], frm[2], to[0] - frm[0], to[1] - frm[1], to[2] - frm[2])
        if any(abs(v) > 1e-6 for v in rot):
            R = rot_mat(*[rad(v) for v in rot])
            pts = [[apply(R, [p[i] - co[i] for i in range(3)])[i] + co[i] for i in range(3)] for p in pts]
        bb_map[nm] = aabb(pts)

    worst = 0.0
    for nm, (blo, bhi) in bb_map.items():
        if nm not in java:
            print("  !! Java 缺失", nm)
            continue
        jlo, jhi = java[nm]
        exp_lo = [-bhi[0], 24.0 - bhi[1], blo[2]]
        exp_hi = [-blo[0], 24.0 - blo[1], bhi[2]]
        d = max(abs(a - b) for a, b in zip(jlo + jhi, exp_lo + exp_hi))
        worst = max(worst, d)
        print("  %-16s 偏差 %.2e %s" % (nm, d, "OK" if d < 1e-4 else "!! 不一致"))
    print("  最大偏差 %.2e => %s" % (worst, "全部通过" if worst < 1e-4 else "**失败**"))
    return worst < 1e-4


def fl(v):
    if abs(v) < 1e-7:
        return "0.0F"
    s = ("%.4f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    return s + "F"


def emit(body_cube, plain, rotated):
    L = []
    a = L.append
    a("package com.hybridcreeper.client.model;")
    a("")
    a("import com.hybridcreeper.HybridCreeper;")
    a("import com.hybridcreeper.entity.WaterCreeperEntity;")
    a("import net.minecraft.client.model.HierarchicalModel;")
    a("import net.minecraft.client.model.geom.ModelLayerLocation;")
    a("import net.minecraft.client.model.geom.ModelPart;")
    a("import net.minecraft.client.model.geom.PartPose;")
    a("import net.minecraft.client.model.geom.builders.CubeDeformation;")
    a("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    a("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    a("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    a("import net.minecraft.client.model.geom.builders.PartDefinition;")
    a("import net.minecraft.resources.ResourceLocation;")
    a("import net.minecraft.util.Mth;")
    a("")
    a("/**")
    a(" * 水下苦力怕的自定义模型：「海豚的身体 + 苦力怕的躯干 / 头 / 四条腿」。")
    a(" *")
    a(" * <p><b>本文件由工具自动生成</b>（{@code vanilla-reference/tools/gen_water_creeper_model.py}），")
    a(" * 源工程是 Blockbench 文件 {@code creeperdolphin.bbmodel}（64×64 贴图）。")
    a(" * 要改模型请改 .bbmodel 后重跑生成器，不要手改这里。</p>")
    a(" *")
    a(" * <h2>坐标换算</h2>")
    a(" * <pre>")
    a(" *   java = ( -bb.x , 24 - bb.y , bb.z )")
    a(" *   PartPose.offset = 轴心世界坐标 - 父级轴心世界坐标")
    a(" *   addBox(x,y,z,dx,dy,dz) : (x,y,z) = 立方体世界最小角 - 部件轴心世界坐标")
    a(" *   旋转 java = ( -rad(rx) , -rad(ry) , +rad(rz) )")
    a(" * </pre>")
    a(" * 生成器对每个立方体做了世界 AABB 交叉验证（最大偏差 ~1e-15）。")
    a(" *")
    a(" * <h2>为什么 body 轴心是 (0, 22, -5)</h2>")
    a(" * <p>与 {@code DolphinModel} 的 body 完全一致 —— 这样海豚身体立方体")
    a(" * 的 {@code addBox} 调用逐字相同，水中摆尾动画也能直接照搬。</p>")
    a(" *")
    a(" * <h2>旋转的立方体被提成了独立部件</h2>")
    a(" * <p>原版模型系统不支持立方体级旋转（{@code addBox} 没有旋转参数），")
    a(" * 所以苦力怕躯干 + 四条腿这几个带旋转的立方体，各自被提成了一个")
    a(" * 独立子部件，把旋转搬到 {@code PartPose} 上。</p>")
    a(" */")
    a("public class WaterCreeperModel extends HierarchicalModel<WaterCreeperEntity> {")
    a("")
    a("    /**")
    a("     * 模型图层。路径是本模组自己的命名空间（{@code hybridcreeper:water_creeper}），")
    a("     * <b>不会</b>和原版的 {@code minecraft:dolphin} 图层撞车 ——")
    a("     * 原版海豚继续用它自己那份模型。")
    a("     */")
    a("    public static final ModelLayerLocation LAYER = new ModelLayerLocation(")
    a("            ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, \"water_creeper\"), \"main\");")
    a("")
    a("    private static final CubeDeformation DEFORM = CubeDeformation.NONE;")
    a("")
    a("    private final ModelPart root;")
    a("    private final ModelPart body;")
    names = [r["name"] for r in rotated] + [p["name"] for p in plain]
    field = {"creeper_torso": "creeperTorso", "back_fin": "backFin", "left_fin": "leftFin",
             "right_fin": "rightFin", "head": "head",
             "left_hind_leg": "leftHindLeg", "right_hind_leg": "rightHindLeg",
             "right_front_leg": "rightFrontLeg", "left_front_leg": "leftFrontLeg"}
    # 腿的静态 xRot（来自 PartPose）—— 动画必须基于它做「绝对赋值」
    legx = {r["name"]: r["rot"][0] for r in rotated}
    hind_x = legx.get("left_hind_leg", 0.0)
    front_x = legx.get("left_front_leg", 0.0)
    for n in ["head"] + [r["name"] for r in rotated] + [p["name"] for p in plain if p["name"] != "head"]:
        a("    private final ModelPart %s;" % field[n])
    a("")

    def cube_line(minv, size, tex, mir):
        return ("                CubeListBuilder.create().texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, DEFORM)"
                % (tex[0], tex[1], ".mirror()" if mir else "",
                   fl(minv[0]), fl(minv[1]), fl(minv[2]),
                   fl(size[0]), fl(size[1]), fl(size[2])))

    # 构造函数
    a("    public %s(ModelPart root) {" % "WaterCreeperModel")
    a("        this.root = root;")
    a("        this.body = root.getChild(\"body\");")
    a("        this.head = this.body.getChild(\"head\");")
    for r in rotated:
        a("        this.%s = this.body.getChild(\"%s\");" % (field[r["name"]], r["name"]))
    for p in plain:
        if p["name"] != "head":
            a("        this.%s = this.body.getChild(\"%s\");" % (field[p["name"]], p["name"]))
    a("    }")
    a("")
    # createBodyLayer
    a("    public static LayerDefinition createBodyLayer() {")
    a("        MeshDefinition mesh = new MeshDefinition();")
    a("        PartDefinition root = mesh.getRoot();")
    a("")
    a("        // 海豚身体（与 DolphinModel 逐字一致），也是动画容器")
    a("        PartDefinition body = root.addOrReplaceChild(\"body\",")
    a(cube_line(body_cube["min"], body_cube["size"], body_cube["tex"], body_cube["mir"]) + ",")
    a("                PartPose.offset(%s, %s, %s));" % (fl(BODY_PIVOT[0]), fl(BODY_PIVOT[1]), fl(BODY_PIVOT[2])))
    a("")
    # plain cubes
    order = {"back_fin": 0, "left_fin": 1, "right_fin": 2, "head": 3}
    for p in sorted(plain, key=lambda x: order.get(x["name"], 9)):
        a("        body.addOrReplaceChild(\"%s\"," % p["name"])
        a(cube_line(p["min"], p["size"], p["tex"], p["mir"]) + ",")
        a("                PartPose.offset(%s, %s, %s));" % tuple(fl(v) for v in p["min"]))
        a("")
    # rotated subparts
    for r in rotated:
        a("        // ↓ 原立方体带旋转，已提成独立子部件")
        a("        body.addOrReplaceChild(\"%s\"," % r["name"])
        a(cube_line(r["min"], r["size"], r["tex"], r["mir"]) + ",")
        a("                PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));"
          % (fl(r["piv"][0]), fl(r["piv"][1]), fl(r["piv"][2]),
             fl(r["rot"][0]), fl(r["rot"][1]), fl(r["rot"][2])))
        a("")
    a("        return LayerDefinition.create(mesh, 64, 64);")
    a("    }")
    a("")
    a("    @Override")
    a("    public ModelPart root() {")
    a("        return this.root;")
    a("    }")
    a("")
    a("    /**")
    a("     * 水中游动动画 —— 逐字沿用原版 {@code DolphinModel#setupAnim}：")
    a("     * 身体的俯仰/偏航跟随头部朝向，游动时（有水平位移）叠加一段")
    a("     * 摆尾级联振荡。苦力怕的躯干与四条腿挂在 body 上，会跟着一起晃。")
    a("     */")
    a("    @Override")
    a("    public void setupAnim(WaterCreeperEntity entity, float limbSwing, float limbSwingAmount,")
    a("                          float ageInTicks, float netHeadYaw, float headPitch) {")
    a("        this.body.xRot = headPitch * (float) (Math.PI / 180.0);")
    a("        this.body.yRot = netHeadYaw * (float) (Math.PI / 180.0);")
    a("        // 腿的静态角度来自 PartPose，每帧必须「绝对赋值」——")
    a("        // ModelPart 的旋转不会自动复位，用 += 会逐帧累加转飞。")
    a("        float hind = %s;" % fl(hind_x))
    a("        float front = %s;" % fl(front_x))
    a("        if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7) {")
    a("            this.body.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);")
    a("            // 四条腿随游动轻轻划水（幅度很小，别抢戏）")
    a("            float swing = Mth.cos(ageInTicks * 0.3F) * 0.15F;")
    a("            hind += swing;")
    a("            front -= swing;")
    a("        }")
    a("        this.leftHindLeg.xRot = hind;")
    a("        this.rightHindLeg.xRot = hind;")
    a("        this.leftFrontLeg.xRot = front;")
    a("        this.rightFrontLeg.xRot = front;")
    a("    }")
    a("}")
    return "\n".join(L)


def main():
    bb = load()
    tw = bb["resolution"]["width"]
    th = bb["resolution"]["height"]
    print("模型 %s  %d 个立方体  纹理 %dx%d" % (bb.get("name"), len(bb["elements"]), tw, th))
    body_cube, plain, rotated = convert(bb)
    ok = verify(bb, body_cube, plain, rotated)
    if not ok:
        print("!! 校验未通过，已停止生成")
        sys.exit(1)
    java = emit(body_cube, plain, rotated)
    with open(OUT_JAVA, "w", encoding="utf-8") as f:
        f.write(java)
    print("\nJava 源码草稿:", OUT_JAVA)


if __name__ == "__main__":
    main()
