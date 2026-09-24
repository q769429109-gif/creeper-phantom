#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
creeperdolphin.bbmodel  ->  WaterCreeperModel.java

水下苦力怕的自定义模型生成器（海豚身体/鳍 + 苦力怕躯干/头/四条腿），
**并**把 .bbmodel 里的 swim 动画一起转成 setupAnim。

与第一版的区别（第一版把分组拍平了）：
  现在**按 .bbmodel 的骨头层级原样生成**（每个 group = 一个部件），
  这样动画里的 animator（按骨头 uuid 索引）能 1:1 落到 Java 部件上；
  以后在主人的 Blockbench 里改动画，重跑本脚本即可。

坐标换算（Blockbench Modded Entity, flip_y=true）：
    java = ( -bb.x , 24 - bb.y , bb.z )
    部件轴心 PartPose.offset = 轴心世界坐标 - 父级轴心世界坐标
    立方体 addBox(x,y,z,dx,dy,dz): (x,y,z) = 立方体世界最小角 - 部件轴心世界坐标
    旋转 java = ( -rad(rx) , -rad(ry) , +rad(rz) )      （动画关键帧同理）
"""

import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
REF = os.path.dirname(HERE)
BB_PATH = os.path.join(REF, "bbmodel", "creeperdolphin.bbmodel")
OUT_JAVA = os.path.join(REF, "bbmodel", "WaterCreeperModel.java.txt")
FPS = 20.0

import bbmodel_to_java as G  # noqa: E402


# --------------------------------------------------------------------------
# 1. 预处理：给分组注入名字（优先用动画里的骨头名），并给重名元素改名
# --------------------------------------------------------------------------

def prep(bb, anim):
    names = {}
    if anim:
        for uid, a in anim.get("animators", {}).items():
            if a.get("name"):
                names[uid] = a["name"]
    counter = [0]

    def walk(nodes):
        for it in nodes:
            if isinstance(it, dict):
                if not it.get("name"):
                    it["name"] = names.get(it.get("uuid")) or ("group_%d" % counter[0])
                    counter[0] += 1
                it.setdefault("origin", [0, 0, 0])
                it.setdefault("rotation", [0, 0, 0])
                walk(it.get("children", []))
    walk(bb["outliner"])
    # 带旋转的那个 8x12x4 "body" 其实是苦力怕躯干，与海豚身体重名 → 改名
    for el in bb["elements"]:
        rot = el.get("rotation") or [0, 0, 0]
        if el["name"] == "body" and any(abs(v) > 1e-6 for v in rot):
            el["name"] = "creeper_torso"


# --------------------------------------------------------------------------
# 2. 逐立方体「带标记角点」交叉验证（能识别 ±90° 的旋转方向错误）
# --------------------------------------------------------------------------
# 约定：bb 角点索引 (i,j,k) ↔ java 角点索引 (1-i, 1-j, k)
#   java_x = -bb_x  → bb x_min 对应 java x_max
#   java_y = 24-bb_y → bb y_min 对应 java y_max
#   java_z = +bb_z  → 索引不变

def _rotm(rx, ry, rz):
    return G._rotm(rx, ry, rz)


def _apply(R, p):
    return G._apply(R, p)


def verify(bb, parts):
    by_uuid = {e["uuid"]: e for e in bb["elements"]}
    groups = {}
    for name, g in [(p["name"], p) for p in parts]:
        pass
    # group 原点（预处理后都存在）
    gorigin = {}

    def walk(nodes):
        for it in nodes:
            if isinstance(it, dict):
                gorigin[it["name"]] = it.get("origin") or [0, 0, 0]
                walk(it.get("children", []))
    walk(bb["outliner"])

    f_of = G.java_transforms(parts)
    by_name = {p["name"]: p for p in parts}

    worst = 0.0
    print("=== 逐立方体「角点」交叉验证 ===")
    for p in parts:
        f_par = f_of(p["name"])
        for sub, box, tex, mir, rot, rel in p["cubes"]:
            use = G._cube_transform(f_par, sub, rel, rot)
            dx, dy, dz = box[3], box[4], box[5]
            # 找到这个立方体在 bb 里的原始元素
            el = None
            for e in bb["elements"]:
                if e["name"] == (sub[:-6] if (sub and sub.endswith("_pivot")) else p["name"]):
                    el = e
                    break
            if el is None:
                continue
            co = el.get("origin") or [0, 0, 0]
            crot = el.get("rotation") or [0, 0, 0]
            Rc = _rotm(*[math.radians(v) for v in crot])
            frm, to = el["from"], el["to"]
            cdx, cdy, cdz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
            for i in (0, 1):
                for j in (0, 1):
                    for k in (0, 1):
                        vbb = [frm[0] + i * cdx, frm[1] + j * cdy, frm[2] + k * cdz]
                        q = _apply(Rc, [vbb[0] - co[0], vbb[1] - co[1], vbb[2] - co[2]])
                        wbb = [q[0] + co[0], q[1] + co[1], q[2] + co[2]]
                        exp = [-wbb[0], 24.0 - wbb[1], wbb[2]]
                        a, b, c = 1 - i, 1 - j, k
                        vj = [box[0] + a * dx, box[1] + b * dy, box[2] + c * dz]
                        wj = use(vj)
                        d = max(abs(wj[n] - exp[n]) for n in range(3))
                        worst = max(worst, d)
    ok = worst < 1e-4
    print("  所有立方体×8 角点，最大偏差 %.2e => %s" % (worst, "全部通过" if ok else "**失败**"))
    return ok


# --------------------------------------------------------------------------
# 3. 生成 Java
# --------------------------------------------------------------------------

def fl(v):
    if abs(v) < 1e-7:
        return "0.0F"
    s = ("%.4f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    return s + "F"


def camel(n):
    p = n.split("_")
    return p[0] + "".join(w.capitalize() for w in p[1:])


def emit(parts, anim):
    fields = []          # [(fieldName, parentField or None, partName)]
    by_name = {p["name"]: p for p in parts}
    children = {}
    for p in parts:
        children.setdefault(p["parent"], []).append(p)

    out = []
    a = out.append

    # 只给「需要单独引用的部件」建字段：动画驱动到的 + 它们的父链 + root/body
    needed = {"root"}
    for p in parts:
        if p["parent"] is None:
            needed.add(p["name"])
    anim_bones = set()
    if anim:
        for uid, an in anim.get("animators", {}).items():
            if any(k.get("channel") == "rotation" and k.get("data_points") for k in an.get("keyframes", [])):
                anim_bones.add(an.get("name"))
    for n in anim_bones:
        cur = n
        while cur and cur in by_name:
            needed.add(cur)
            cur = by_name[cur]["parent"]
    # 稳住顺序：按层级
    ordered = sorted([p["name"] for p in parts if p["name"] in needed],
                     key=lambda n: _depth(n, by_name))

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
    a(" * 要改模型/动画请改 .bbmodel 后重跑生成器，不要手改这里。</p>")
    a(" *")
    a(" * <h2>部件层级</h2>")
    a(" * <p>本文件<b>按 .bbmodel 的骨头层级 1:1 生成</b>（每个 group 一个部件），")
    a(" * 这样 .bbmodel 里的动画（animator 按骨头 uuid 索引）能直接落到对应部件上。</p>")
    a(" * <p><b>注意</b>：部件 {@code tail} 的 uuid 继承自原版海豚的尾巴骨头，")
    a(" * 但在这个模型里它装的是<b>四条腿</b> —— 名字保持与 Blockbench 工程一致，别被名字骗了。</p>")
    a(" *")
    a(" * <h2>动画</h2>")
    a(" * <p>{@link #setupAnim} 直接回放 .bbmodel 里 {@code swim} 动画的关键帧曲线")
    a(" * （线性插值），角度按 {@code java = -rad(bb)} 换算。</p>")
    a(" */")
    a("public class WaterCreeperModel extends HierarchicalModel<WaterCreeperEntity> {")
    a("")
    a("    /** 模型图层：本模组私有命名空间，不会和原版 {@code minecraft:dolphin} 撞车。 */")
    a("    public static final ModelLayerLocation LAYER = new ModelLayerLocation(")
    a("            ResourceLocation.fromNamespaceAndPath(HybridCreeper.MODID, \"water_creeper\"), \"main\");")
    a("")
    a("    private static final CubeDeformation DEFORM = CubeDeformation.NONE;")
    a("    private static final float DEG2RAD = 0.017453292F;")
    a("")
    a("    private final ModelPart root;")
    for n in ordered:
        if n == "root":
            continue
        a("    private final ModelPart %s;" % camel(n))
    a("")
    a("    public WaterCreeperModel(ModelPart root) {")
    a("        this.root = root;")
    for n in ordered:
        if n == "root":
            continue
        p = by_name[n]
        par = p["parent"]
        src = "root" if par is None else ("this." + camel(par))
        a("        this.%s = %s.getChild(\"%s\");" % (camel(n), src, n))
    a("    }")
    a("")

    # ---- createBodyLayer ----
    a("    public static LayerDefinition createBodyLayer() {")
    a("        MeshDefinition mesh = new MeshDefinition();")
    a("        PartDefinition root = mesh.getRoot();")
    a("")

    used_vars = set()

    def varof(n):
        v = "_" + n
        while v in used_vars:
            v += "_"
        used_vars.add(v)
        return v

    def pose(prefix, piv, rot):
        if any(abs(v) > 1e-7 for v in rot):
            return "%s.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % (
                prefix, fl(piv[0]), fl(piv[1]), fl(piv[2]), fl(rot[0]), fl(rot[1]), fl(rot[2]))
        return "%s.offset(%s, %s, %s)" % (prefix, fl(piv[0]), fl(piv[1]), fl(piv[2]))

    def cube_call(box, tex, mir):
        return "CubeListBuilder.create().texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, DEFORM)" % (
            tex[0], tex[1], ".mirror()" if mir else "",
            fl(box[0]), fl(box[1]), fl(box[2]), fl(box[3]), fl(box[4]), fl(box[5]))

    def emit_part(p, parent_expr):
        v = varof(p["name"])
        plain = [c for c in p["cubes"] if c[0] is None]
        a("        PartDefinition %s = %s.addOrReplaceChild(\"%s\"," % (v, parent_expr, p["name"]))
        if plain:
            a("                CubeListBuilder.create()")
            for sub, box, tex, mir, _r, _rel in plain:
                a("                        .texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, DEFORM)"
                  % (tex[0], tex[1], ".mirror()" if mir else "",
                     fl(box[0]), fl(box[1]), fl(box[2]), fl(box[3]), fl(box[4]), fl(box[5])))
            a("                , %s);" % pose("PartPose", p["pivot"], p["rot"]))
        else:
            a("                CubeListBuilder.create(),")
            a("                %s);" % pose("PartPose", p["pivot"], p["rot"]))
        a("")
        for sub, box, tex, mir, rot_c, rel in p["cubes"]:
            if sub is None:
                continue
            nm = sub[:-6] if sub.endswith("_pivot") else sub
            a("        // ↓ 原立方体带旋转，提成独立子部件")
            a("        %s.addOrReplaceChild(\"%s\"," % (v, nm))
            a("                %s," % cube_call(box, tex, mir))
            a("                %s);" % pose("PartPose", rel, rot_c))
            a("")
        for ch in children.get(p["name"], []):
            emit_part(ch, v)

    for p in children.get(None, []):
        emit_part(p, "root")

    a("        return LayerDefinition.create(mesh, 64, 64);")
    a("    }")
    a("")
    a("    @Override")
    a("    public ModelPart root() {")
    a("        return this.root;")
    a("    }")
    a("")

    # ---- 动画 ----
    if anim:
        emit_anim(a, anim, by_name, camel)

    a("}")
    return "\n".join(out)


def _depth(n, by_name):
    d, cur = 0, by_name[n]
    while cur["parent"]:
        d += 1
        cur = by_name[cur["parent"]]
    return d


def emit_anim(a, anim, by_name, camel):
    length = float(anim.get("length") or 0.0)
    loop_ticks = length * FPS
    curves = []   # (boneName, fieldName, times, xs, ys, zs)
    for uid, an in anim.get("animators", {}).items():
        bone = an.get("name")
        kfs = [k for k in an.get("keyframes", []) if k.get("channel") == "rotation" and k.get("data_points")]
        if not kfs:
            continue
        if bone not in by_name:
            print("  (跳过动画骨头 %s：模型里没有)" % bone)
            continue
        kfs.sort(key=lambda k: k["time"])
        times = [float(k["time"]) for k in kfs]

        def comp(i):
            return [float(k["data_points"][0][i]) for k in kfs]
        curves.append((bone, camel(bone), times, comp("x"), comp("y"), comp("z")))

    a("    /* ---------------- swim 动画（由 .bbmodel 关键帧导出） ---------------- */")
    a("")
    a("    /** 一个循环的时长（tick）= length(秒) × 20。 */")
    a("    private static final float SWIM_LOOP_TICKS = %s;" % fl(loop_ticks))
    for bone, fld, times, xs, ys, zs in curves:
        a("")
        a("    private static final float[] %s_T = {%s};" % (fld.upper(), ", ".join(fl(t) for t in times)))
        a("    private static final float[] %s_X = {%s};" % (fld.upper(), ", ".join(fl(v) for v in xs)))
        if any(abs(v) > 1e-7 for v in ys):
            a("    private static final float[] %s_Y = {%s};" % (fld.upper(), ", ".join(fl(v) for v in ys)))
        if any(abs(v) > 1e-7 for v in zs):
            a("    private static final float[] %s_Z = {%s};" % (fld.upper(), ", ".join(fl(v) for v in zs)))
    a("")
    a("    /**")
    a("     * 水中游动：直接回放 .bbmodel 的 swim 曲线（线性插值）。")
    a("     * 角度是 Blockbench 约定（度），Java 侧按 {@code xRot = -rad(bb)} 换算。")
    a("     */")
    a("    @Override")
    a("    public void setupAnim(WaterCreeperEntity entity, float limbSwing, float limbSwingAmount,")
    a("                          float ageInTicks, float netHeadYaw, float headPitch) {")
    a("        float t = (ageInTicks % SWIM_LOOP_TICKS) / 20.0F;")
    for bone, fld, times, xs, ys, zs in curves:
        a("        this.%s.xRot = -sample(%s_T, %s_X, t) * DEG2RAD;" % (fld, fld.upper(), fld.upper()))
        if any(abs(v) > 1e-7 for v in ys):
            a("        this.%s.yRot = -sample(%s_T, %s_Y, t) * DEG2RAD;" % (fld, fld.upper(), fld.upper()))
        if any(abs(v) > 1e-7 for v in zs):
            a("        this.%s.zRot = sample(%s_T, %s_Z, t) * DEG2RAD;" % (fld, fld.upper(), fld.upper()))
    a("    }")
    a("")
    a("    /** 在关键帧时间轴上做线性插值。 */")
    a("    private static float sample(float[] times, float[] values, float t) {")
    a("        int last = times.length - 1;")
    a("        if (t <= times[0]) return values[0];")
    a("        if (t >= times[last]) return values[last];")
    a("        for (int i = 1; i <= last; i++) {")
    a("            if (t <= times[i]) {")
    a("                float f = (t - times[i - 1]) / (times[i] - times[i - 1]);")
    a("                return Mth.lerp(f, values[i - 1], values[i]);")
    a("            }")
    a("        }")
    a("        return values[last];")
    a("    }")
    a("")


def main():
    with open(BB_PATH, encoding="utf-8") as f:
        bb = json.load(f)
    anim = (bb.get("animations") or [None])[0]
    prep(bb, anim)
    print("模型 %s  %d 个立方体  %d 段动画" % (bb.get("name"), len(bb["elements"]), len(bb.get("animations") or [])))
    # ⚠️ Blockbench 里「隐藏」≠「不导出」：visibility=false 的元素照样会进游戏，
    #    还常常把别的部件整个盖住（胸鳍被隐藏的海豚身体吃掉就是这么来的）。
    hidden = [e["name"] for e in bb["elements"] if e.get("visibility") is False]
    if hidden:
        print("⚠️  有 %d 个元素是隐藏的（visibility=false）但 export=true，会被导出到游戏；" % len(hidden))
        print("    如果不是刻意要的，请先在 .bbmodel 里删掉：%s" % ", ".join(hidden))
    parts = G.to_parts(bb)
    if not verify(bb, parts):
        raise SystemExit("!! 校验未通过，已停止生成")
    java = emit(parts, anim)
    with open(OUT_JAVA, "w", encoding="utf-8") as f:
        f.write(java)
    print("\nJava 源码草稿:", OUT_JAVA)


if __name__ == "__main__":
    main()
