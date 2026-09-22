#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Blockbench .bbmodel  ->  Minecraft Java 实体模型（反向换算 + 验证 + 生成 Java 源码）

反向换算公式来自 Blockbench 官方源码 js/formats/java/modded_entity.ts 的 compile() 分支：
    ox = -(bb.origin.x - parent.bb.origin.x)
    oy = -(bb.origin.y - parent.bb.origin.y) + (24 if 根级 else 0)
    oz = +(bb.origin.z - parent.bb.origin.z)
    rot = (-degToRad(bb.rx), -degToRad(bb.ry), +degToRad(bb.rz))
    java_x = bb.origin.x - cube.to.x
    java_y = -cube.from.y - size.y + bb.origin.y        # flip_y = true
    java_z = cube.from.z - bb.origin.z

⚠️ 关键点：**Minecraft 的原版模型系统不支持"单个立方体自带旋转"**。
   CubeListBuilder.addBox() 没有任何旋转参数，只有 PartPose（部件级）才有。
   Blockbench 的 Modded Entity 导出会把立方体级旋转**直接丢掉**。
   所以本脚本会把每个"带旋转的立方体"自动提成一个合成子部件（synthetic part），
   把旋转转移到 PartPose 上——这样导出的 Java 才和 Blockbench 里看到的一致。
"""

import base64
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_bbmodel as B  # noqa: E402  复用它的渲染器 / PNG 读写


# ==========================================================================
# 1. 解析 bbmodel
# ==========================================================================

def load_bb(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def build_tree(bb):
    """把 outliner 展平成 [(group_path, group_dict, cube_element)] 的有序列表。"""
    by_uuid = {e["uuid"]: e for e in bb["elements"]}
    out = []

    def walk(nodes, parent_name):
        for n in nodes:
            if isinstance(n, str):
                el = by_uuid.get(n)
                if el:
                    out.append((parent_name, None, el))
            else:
                out.append((parent_name, n, None))
                walk(n.get("children", []), n["name"])
    walk(bb["outliner"], None)
    return out


def group_index(bb):
    """name -> group dict（含 parent 名）"""
    by_uuid = {e["uuid"]: e for e in bb["elements"]}
    groups = {}

    def walk(nodes, parent_name):
        for n in nodes:
            if isinstance(n, dict):
                n = dict(n)
                n["_parent"] = parent_name
                groups[n["name"]] = n
                walk(n.get("children", []), n["name"])
    walk(bb["outliner"], None)
    return groups


# ==========================================================================
# 2. 反向换算 -> Java 部件表
# ==========================================================================

def to_parts(bb):
    """
    返回 (parts, extras)
      parts : [(name, parent, pivot_xyz_relative, rot_rad, [(box, texOffs, mirror)])]
              —— 正好是 build_bbmodel.resolve() 认识的格式，可直接复用它的渲染器
      extras: {part_name: {"cube_rotated": bool, "src": ...}} 供生成 Java 注释用
    """
    groups = group_index(bb)
    by_uuid = {e["uuid"]: e for e in bb["elements"]}

    # 每个 group 的 bb 全局 origin；根级的父 origin 取 (0,0,0)
    def bb_origin(name):
        return groups[name]["origin"]

    parts = []
    extras = {}

    def java_pivot_rel(name):
        g = groups[name]
        o = g["origin"]
        p = groups[g["_parent"]]["origin"] if g["_parent"] else (0.0, 0.0, 0.0)
        ox = -(o[0] - p[0])
        oy = -(o[1] - p[1]) + (24.0 if not g["_parent"] else 0.0)
        oz = (o[2] - p[2])
        return (ox, oy, oz)

    def java_rot(g):
        r = g.get("rotation") or [0.0, 0.0, 0.0]
        return (-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2]))

    # 先建 group 级部件
    order = []
    for name, g in groups.items():
        if g["_parent"]:
            order.append(name)
    # 保证父级先出现
    def depth(n):
        d, cur = 0, groups[n]
        while cur["_parent"]:
            d += 1
            cur = groups[cur["_parent"]]
        return d
    order = sorted(groups.keys(), key=depth)

    for name in order:
        g = groups[name]
        cubes = []
        synthetic = []
        for child in g.get("children", []):
            if not isinstance(child, str):
                continue
            el = by_uuid[child]
            o = g["origin"]
            frm, to = el["from"], el["to"]
            dx, dy, dz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
            # 立方体在 group 局部 java 坐标下的最小角
            box = (o[0] - to[0], o[1] - to[1], frm[2] - o[2], dx, dy, dz)
            tex = el.get("uv_offset") or [0, 0]
            mir = bool(el.get("mirror_uv"))
            rot = el.get("rotation")
            if rot and any(abs(v) > 1e-6 for v in rot):
                # 带旋转 —— 提成合成子部件
                co = el["origin"]
                # 该立方体的 java 绝对轴心 / 父 group 的 java 绝对轴心
                abs_p = _abs_java_pivot(groups, name, co)
                par_abs = _abs_java_pivot(groups, name)
                rel = (abs_p[0] - par_abs[0], abs_p[1] - par_abs[1], abs_p[2] - par_abs[2])
                # 立方体局部坐标（相对它自己的轴心）
                loc = (box[0] - rel[0], box[1] - rel[1], box[2] - rel[2])
                sub = el["name"] + "_pivot"
                while sub in groups:
                    sub += "_"
                cubes.append((sub, (loc[0], loc[1], loc[2], dx, dy, dz), tex, mir,
                              (-math.radians(rot[0]), -math.radians(rot[1]),
                               math.radians(rot[2])), rel))
                synthetic.append(sub)
                extras[sub] = {"rotated": True, "orig": el["name"],
                               "bb_rot": list(rot)}
            else:
                cubes.append((None, box, tex, mir, None, None))
        parts.append({"name": name, "parent": g["_parent"], "pivot": java_pivot_rel(name),
                      "rot": java_rot(g), "cubes": cubes})
    return parts


def _abs_java_pivot(groups, name, bb_origin=None):
    """部件（或某个立方体）在 Java 坐标系下的绝对轴心。"""
    o = bb_origin if bb_origin is not None else groups[name]["origin"]
    return (-o[0], 24.0 - o[1], o[2])


def to_render_model(parts, res, tex, name="converted"):
    """转成 build_bbmodel.resolve() 认识的 model 结构。

    ⚠️ 只有「无旋转」的立方体留在父级；带旋转的立方体**只能**挂在合成子部件上，
       否则会重复出现（局部坐标直接放进父级是错的）。
    """
    flat = []
    for p in parts:
        cubes = [(box, tuple(texoff), mir)
                 for sub, box, texoff, mir, _rot, _rel in p["cubes"] if sub is None]
        flat.append((p["name"], p["parent"], p["pivot"], p["rot"], cubes))
        for sub, box, texoff, mir, rot_c, rel in p["cubes"]:
            if sub is not None:
                flat.append((sub, p["name"], rel, rot_c, [(box, tuple(texoff), mir)]))
    return {"id": name, "tex": tex, "res": res, "parts": flat}


# ==========================================================================
# 3. 用 Blockbench 语义渲染（立方体自带旋转），做交叉验证
# ==========================================================================

def _raster(tri_list, W, H, scale, off_x, off_y, keep_max):
    color = bytearray(W * H * 4)
    for i in range(0, len(color), 4):
        color[i] = color[i + 1] = color[i + 2] = 24
        color[i + 3] = 255
    zbuf = [-1e18 if keep_max else 1e18] * (W * H)
    for scr, dep, col in tri_list:
        pa = (scr[0][0] * scale + off_x, scr[0][1] * scale + off_y)
        pb = (scr[1][0] * scale + off_x, scr[1][1] * scale + off_y)
        pc = (scr[2][0] * scale + off_x, scr[2][1] * scale + off_y)
        da, db, dc = dep
        minx = max(0, int(min(pa[0], pb[0], pc[0])))
        maxx = min(W - 1, int(max(pa[0], pb[0], pc[0])) + 1)
        miny = max(0, int(min(pa[1], pb[1], pc[1])))
        maxy = min(H - 1, int(max(pa[1], pb[1], pc[1])) + 1)
        den = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
        if abs(den) < 1e-9:
            continue
        for py in range(miny, maxy + 1):
            for pxx in range(minx, maxx + 1):
                X, Y = pxx + 0.5, py + 0.5
                w0 = ((pb[1] - pc[1]) * (X - pc[0]) + (pc[0] - pb[0]) * (Y - pc[1])) / den
                w1 = ((pc[1] - pa[1]) * (X - pc[0]) + (pa[0] - pc[0]) * (Y - pc[1])) / den
                w2 = 1.0 - w0 - w1
                if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                    continue
                d = w0 * da + w1 * db + w2 * dc
                idx = py * W + pxx
                if (keep_max and d <= zbuf[idx]) or ((not keep_max) and d >= zbuf[idx]):
                    continue
                zbuf[idx] = d
                j = idx * 4
                sh = 0.55 + 0.45 * (1.0 if keep_max else 0.0)
                color[j] = int(col[0] * sh)
                color[j + 1] = int(col[1] * sh)
                color[j + 2] = int(col[2] * sh)
                color[j + 3] = 255
    return W, H, color


# ==========================================================================
# 3.5 数值交叉验证：逐立方体比对世界 AABB
# ==========================================================================
# 原版 ModelPart.translateAndRotate 的实现（已从源码逐字确认）：
#     poseStack.translate(x, y, z);          // 平移到轴心
#     poseStack.mulPose(rotationZYX(zRot, yRot, xRot));
# **没有 translate(-x,-y,-z)**。所以部件的世界变换是：
#     world = 父级变换( pivot_rel + R · v )      // v 是立方体相对自身轴心的局部坐标
# 而不是 R·(v - pivot) + pivot —— 后者会把轴心抵消掉，
# 小角度时看不出来，90° / 180° 就直接飞走了（我第一版就是这么错的）。
#
# BB 侧：立方体 from/to 是**全局坐标**，旋转绕自身的全局 origin：
#     world = 父级变换( R · (v - origin) + origin )

def _rotm(rx, ry, rz):
    """输入弧度。返回 Rx·Ry·Rz（与 rotationZYX 的复合顺序一致）。"""
    cX, sX = math.cos(rx), math.sin(rx)
    cY, sY = math.cos(ry), math.sin(ry)
    cZ, sZ = math.cos(rz), math.sin(rz)

    def mul(A, Bm):
        return [[sum(A[i][k] * Bm[k][j] for k in range(3)) for j in range(3)]
                for i in range(3)]
    Rz = [[cZ, -sZ, 0], [sZ, cZ, 0], [0, 0, 1]]
    Ry = [[cY, 0, sY], [0, 1, 0], [-sY, 0, cY]]
    Rx = [[1, 0, 0], [0, cX, -sX], [0, sX, cX]]
    return mul(Rx, mul(Ry, Rz))


def _apply(R, p):
    return [sum(R[i][k] * p[k] for k in range(3)) for i in range(3)]


def box_corners(x0, y0, z0, x1, y1, z1):
    return [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
            (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]


def _aabb(pts):
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    return lo, hi


def java_transforms(parts):
    """
    返回 {部件名: f}，f 把"相对自身轴心的局部坐标"映射到世界坐标。
    递归式：f_child(v) = f_parent( pivot_rel + R_child · v )
    """
    by = {p["name"]: p for p in parts}
    cache = {}

    def f_of(name):
        if name in cache:
            return cache[name]
        p = by[name]
        R = _rotm(*p["rot"])
        piv = p["pivot"]

        def self_tf(v):
            q = _apply(R, v)
            return [q[0] + piv[0], q[1] + piv[1], q[2] + piv[2]]
        par = f_of(p["parent"]) if p["parent"] else None
        f = (lambda v: par(self_tf(v))) if par else self_tf
        cache[name] = f
        return f
    return f_of


def _cube_transform(f_parent, sub, rel, rot):
    """带旋转的立方体走合成子部件：f(v) = f_parent( rel + R · v )。"""
    if sub is None:
        return f_parent
    R = _rotm(*rot)

    def g(v, f=f_parent, R=R, rel=rel):
        q = _apply(R, v)
        return f([q[0] + rel[0], q[1] + rel[1], q[2] + rel[2]])
    return g


def java_cube_aabbs(parts):
    """{原始立方体名: (lo, hi)}，Java 世界坐标。"""
    f_of = java_transforms(parts)
    out = {}
    for p in parts:
        f_par = f_of(p["name"])
        for sub, box, tex, mir, rot, rel in p["cubes"]:
            use = _cube_transform(f_par, sub, rel, rot)
            pts = [use(c) for c in box_corners(box[0], box[1], box[2],
                                               box[0] + box[3], box[1] + box[4], box[2] + box[5])]
            nm = sub[:-6] if (sub and sub.endswith("_pivot")) else (sub or p["name"])
            out[nm] = _aabb(pts)
    return out


def bb_cube_aabbs(bb):
    """{立方体名: (lo, hi)}，Blockbench 世界坐标。"""
    groups = group_index(bb)
    by_uuid = {e["uuid"]: e for e in bb["elements"]}
    cache = {}

    def f_of(name):
        if name in cache:
            return cache[name]
        g = groups[name]
        o = g["origin"]
        r = g.get("rotation") or [0.0, 0.0, 0.0]
        R = _rotm(*[math.radians(v) for v in r])

        def self_tf(v):
            q = _apply(R, [v[0] - o[0], v[1] - o[1], v[2] - o[2]])
            return [q[0] + o[0], q[1] + o[1], q[2] + o[2]]
        par = f_of(g["_parent"]) if g["_parent"] else None
        f = (lambda v: par(self_tf(v))) if par else self_tf
        cache[name] = f
        return f

    out = {}
    for name, g in groups.items():
        f = f_of(name)
        for child in g.get("children", []):
            if not isinstance(child, str):
                continue
            el = by_uuid[child]
            frm, to = el["from"], el["to"]
            co = el.get("origin") or g["origin"]
            cr = el.get("rotation") or [0.0, 0.0, 0.0]
            Rc = _rotm(*[math.radians(v) for v in cr])

            def cube_tf(v, f=f, co=co, Rc=Rc):
                q = _apply(Rc, [v[0] - co[0], v[1] - co[1], v[2] - co[2]])
                return f([q[0] + co[0], q[1] + co[1], q[2] + co[2]])
            pts = [cube_tf(c) for c in box_corners(frm[0], frm[1], frm[2], to[0], to[1], to[2])]
            out[el["name"]] = _aabb(pts)
    return out


def verify_aabb(bb, parts):
    j = java_cube_aabbs(parts)
    b = bb_cube_aabbs(bb)
    print("\n=== 逐立方体世界 AABB 交叉验证 ===")
    print("    期望关系：java = ( -bb.x , 24 - bb.y , bb.z )")
    ok = True
    worst = 0.0
    for name in b:
        if name not in j:
            print("  !! Java 侧缺失:", name)
            ok = False
            continue
        blo, bhi = b[name]
        jlo, jhi = j[name]
        exp_lo = [-bhi[0], 24.0 - bhi[1], blo[2]]
        exp_hi = [-blo[0], 24.0 - blo[1], bhi[2]]
        d = max(abs(a - c) for a, c in zip(jlo + jhi, exp_lo + exp_hi))
        worst = max(worst, d)
        good = d < 1e-4
        ok = ok and good
        print("  %-16s BB Y[%6.1f,%6.1f] Z[%6.1f,%6.1f]  ->  Java Y[%6.1f,%6.1f] Z[%6.1f,%6.1f]"
              "  偏差 %.1e %s"
              % (name, blo[1], bhi[1], blo[2], bhi[2],
                 jlo[1], jhi[1], jlo[2], jhi[2], d, "OK" if good else "!! 不一致"))
    print("  最大偏差 %.2e  => 交叉验证%s" % (worst, "全部通过" if ok else "**失败**"))
    return ok


# ==========================================================================
# 3.6 用正确的 MC 语义渲染（纯展示用）
# ==========================================================================

def render_parts(tex_path, parts, axes, size=(360, 460), colored=False):
    """用 java_transforms 的正确变换渲染。colored=True 时按部件配色（看结构）。"""
    projx, projy, depth_axis, keep_max = axes
    texpx = tw = th = None
    if not colored:
        tw, th, texpx = B.load_png(tex_path)

    f_of = java_transforms(parts)
    tris = []
    sx_min = sy_min = 1e18
    sx_max = sy_max = -1e18
    pi = 0
    for p in parts:
        col = B.PALETTE[pi % len(B.PALETTE)]
        pi += 1
        f_par = f_of(p["name"])
        for sub, box, tex, mir, rot, rel in p["cubes"]:
            f = _cube_transform(f_par, sub, rel, rot)
            # 复用 build_bbmodel.mc_faces：它是从 ModelPart.Cube 逐行移植的顶点+UV 生成
            for _facename, vc in B.mc_faces(box, tex, mir):
                pts = [f(v[0]) for v in vc]
                uvs = [v[1] for v in vc]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    idx = [tri[0], tri[1], tri[2]]
                    scr = [(projx[1] * pts[i][projx[0]], projy[1] * pts[i][projy[0]])
                           for i in idx]
                    dep = [pts[i][depth_axis] for i in idx]
                    uvv = [uvs[i] for i in idx]
                    for q in scr:
                        sx_min = min(sx_min, q[0]); sx_max = max(sx_max, q[0])
                        sy_min = min(sy_min, q[1]); sy_max = max(sy_max, q[1])
                    tris.append((scr, dep, col, uvv))

    W, H = size
    scale = min((W - 24) / max(sx_max - sx_min, 1e-6),
                (H - 24) / max(sy_max - sy_min, 1e-6), 24.0)
    off_x = W / 2.0 - (sx_min + sx_max) / 2.0 * scale
    off_y = H / 2.0 - (sy_min + sy_max) / 2.0 * scale

    color = bytearray(W * H * 4)
    for i in range(0, len(color), 4):
        color[i] = color[i + 1] = color[i + 2] = 24
        color[i + 3] = 255
    zbuf = [-1e18 if keep_max else 1e18] * (W * H)

    def sample(u, v):
        uu = int(math.floor(u)) % tw
        vv = int(math.floor(v)) % th
        i = (vv * tw + uu) * 4
        return texpx[i], texpx[i + 1], texpx[i + 2], texpx[i + 3]

    for scr, dep, col, uvv in tris:
        pa = (scr[0][0] * scale + off_x, scr[0][1] * scale + off_y)
        pb = (scr[1][0] * scale + off_x, scr[1][1] * scale + off_y)
        pc = (scr[2][0] * scale + off_x, scr[2][1] * scale + off_y)
        da, db, dc = dep
        ua, ub, uc = uvv
        minx = max(0, int(min(pa[0], pb[0], pc[0])))
        maxx = min(W - 1, int(max(pa[0], pb[0], pc[0])) + 1)
        miny = max(0, int(min(pa[1], pb[1], pc[1])))
        maxy = min(H - 1, int(max(pa[1], pb[1], pc[1])) + 1)
        den = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
        if abs(den) < 1e-9:
            continue
        for py in range(miny, maxy + 1):
            for pxx in range(minx, maxx + 1):
                X, Y = pxx + 0.5, py + 0.5
                w0 = ((pb[1] - pc[1]) * (X - pc[0]) + (pc[0] - pb[0]) * (Y - pc[1])) / den
                w1 = ((pc[1] - pa[1]) * (X - pc[0]) + (pa[0] - pc[0]) * (Y - pc[1])) / den
                w2 = 1.0 - w0 - w1
                if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                    continue
                d = w0 * da + w1 * db + w2 * dc
                idx = py * W + pxx
                if (keep_max and d <= zbuf[idx]) or ((not keep_max) and d >= zbuf[idx]):
                    continue
                zbuf[idx] = d
                j = idx * 4
                if colored:
                    color[j] = col[0]; color[j + 1] = col[1]; color[j + 2] = col[2]
                    color[j + 3] = 255
                else:
                    u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
                    v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
                    r, g, b_, al = sample(u, v)
                    if al == 0:
                        continue
                    color[j] = r; color[j + 1] = g; color[j + 2] = b_; color[j + 3] = 255
    return W, H, color


# ==========================================================================
# 4. 生成 Java 源码
# ==========================================================================

def f(v):
    """float 字面量（始终带小数点，避免出现 4F 这种写法）"""
    if abs(v) < 1e-7:
        return "0.0F"
    s = ("%.6f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    return s + "F"


def emit_java(parts, bb, class_name, model_layer, entity_class, tex_w, tex_h):
    L = []
    a = L.append
    a("package com.hybridcreeper.client.model;")
    a("")
    a("import net.minecraft.client.model.HierarchicalModel;")
    a("import net.minecraft.client.model.geom.ModelPart;")
    a("import net.minecraft.client.model.geom.PartPose;")
    a("import net.minecraft.client.model.geom.builders.CubeDeformation;")
    a("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    a("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    a("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    a("import net.minecraft.client.model.geom.builders.PartDefinition;")
    a("import net.minecraft.util.Mth;")
    a("import net.minecraft.world.entity.monster.Phantom;")
    a("")
    a("/**")
    a(" * 杂交苦力怕自定义模型（自动生成，请勿手改 —— 改模型请改 .bbmodel 后重跑生成器）。")
    a(" *")
    a(" * <p>由 Blockbench 工程 {@code phantomcreeper.bbmodel} 反向换算而来，")
    a(" * 纹理尺寸 %d × %d。</p>" % (tex_w, tex_h))
    a(" *")
    a(" * <p>注意：原版模型系统不支持立方体级旋转，带旋转的立方体已被提成独立的")
    a(" * 「合成子部件」，旋转转移到 {@code PartPose} 上。</p>")
    a(" */")
    a("public class %s extends HierarchicalModel<Phantom> {" % class_name)
    a("")

    # 字段
    field_parts = [p for p in parts if p["name"] in
                   ("body", "head", "tail_base", "tail_tip",
                    "left_wing_base", "left_wing_tip",
                    "right_wing_base", "right_wing_tip")]
    for p in field_parts:
        a("    private final ModelPart %s;" % _camel(p["name"]))
    a("    private final ModelPart root;")
    a("")
    a("    public %s(ModelPart root) {" % class_name)
    a("        this.root = root;")
    a("        ModelPart body = root.getChild(\"body\");")
    for p in field_parts:
        if p["name"] == "body":
            a("        this.body = body;")
        elif p["parent"] == "body":
            a("        this.%s = body.getChild(\"%s\");" % (_camel(p["name"]), p["name"]))
    for p in field_parts:
        if p["parent"] in ("tail_base", "tail_tip", "left_wing_base", "right_wing_base"):
            a("        this.%s = this.%s.getChild(\"%s\");"
              % (_camel(p["name"]), _camel(p["parent"]), p["name"]))
    a("    }")
    a("")

    # createBodyLayer
    a("    public static LayerDefinition createBodyLayer() {")
    a("        MeshDefinition mesh = new MeshDefinition();")
    a("        PartDefinition root = mesh.getRoot();")
    a("")
    by_name = {p["name"]: p for p in parts}

    def var(n):
        return "_" + n.replace("-", "_")

    emitted = set()

    def emit_part(p, parent_var):
        name = p["name"]
        cubes = p["cubes"]
        # 该部件的立方体（不含合成子部件）
        plain = [(sub, box, tex, mir, rot, rel) for sub, box, tex, mir, rot, rel in cubes]
        a("        PartDefinition %s = %s.addOrReplaceChild(\"%s\"," % (var(name), parent_var, name))
        a("                CubeListBuilder.create()")
        if not plain:
            a("                ,")
        for sub, box, tex, mir, rot, rel in plain:
            if sub is not None:
                continue          # 带旋转的立方体只挂到合成子部件上，别在这里重复加
            a("                        .texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, DEFORM)"
              % (tex[0], tex[1], ".mirror()" if mir else "",
                 f(box[0]), f(box[1]), f(box[2]), f(box[3]), f(box[4]), f(box[5])))
        px, py, pz = p["pivot"]
        rx, ry, rz = p["rot"]
        if any(abs(v) > 1e-7 for v in (rx, ry, rz)):
            a("                , PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));"
              % (f(px), f(py), f(pz), f(rx), f(ry), f(rz)))
        else:
            a("                , PartPose.offset(%s, %s, %s));" % (f(px), f(py), f(pz)))
        a("")
        emitted.add(name)
        # 先输出合成子部件
        for sub, box, tex, mir, rot, rel in plain:
            if sub is None:
                continue
            a("        // ↓ 原立方体带旋转，已提成子部件")
            a("        %s.addOrReplaceChild(\"%s\"," % (var(name), sub))
            a("                CubeListBuilder.create().texOffs(%d, %d)%s.addBox(%s, %s, %s, %s, %s, %s, DEFORM),"
              % (tex[0], tex[1], ".mirror()" if mir else "",
                 f(box[0]), f(box[1]), f(box[2]), f(box[3]), f(box[4]), f(box[5])))
            a("                PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));"
              % (f(rel[0]), f(rel[1]), f(rel[2]), f(rot[0]), f(rot[1]), f(rot[2])))
            a("")
        # 子部件
        for child in parts:
            if child["parent"] == name:
                emit_part(child, var(name))

    for p in parts:
        if p["parent"] is None:
            emit_part(p, "root")

    a("        return LayerDefinition.create(mesh, %d, %d);" % (tex_w, tex_h))
    a("    }")
    a("")
    a("    @Override")
    a("    public ModelPart root() {")
    a("        return this.root;")
    a("    }")
    a("")
    a("    /**")
    a("     * 沿用原版幻翼的扇翅 / 摆尾节奏：")
    a("     * 翅膀 Z 轴 ±16° 摆动，尾巴 X 轴 0°~−10° 摆动。")
    a("     * 苦力怕的四条腿挂在 tail_base / tail_tip 上，会跟着尾巴一起晃。")
    a("     */")
    a("    @Override")
    a("    public void setupAnim(Phantom entity, float limbSwing, float limbSwingAmount,")
    a("                          float ageInTicks, float netHeadYaw, float headPitch) {")
    a("        float f = ((float) entity.getUniqueFlapTickOffset() + ageInTicks) * 7.448451F * (float) (Math.PI / 180.0);")
    a("        float wing = Mth.cos(f) * 16.0F * (float) (Math.PI / 180.0);")
    a("        float tail = -(5.0F + Mth.cos(f * 2.0F) * 5.0F) * (float) (Math.PI / 180.0);")
    a("")
    a("        this.leftWingBase.zRot = wing;")
    a("        this.leftWingTip.zRot = wing;")
    a("        this.rightWingBase.zRot = -wing;")
    a("        this.rightWingTip.zRot = -wing;")
    a("        this.tailBase.xRot = tail;")
    a("        this.tailTip.xRot = tail;")
    a("")
    a("        // 头部跟随目标（原版幻翼头是固定的，这里让苦力怕脑袋能转，更像活的）")
    a("        this.head.yRot = netHeadYaw * (float) (Math.PI / 180.0);")
    a("        this.head.xRot = 0.2F + headPitch * (float) (Math.PI / 180.0);")
    a("    }")
    a("")
    a("    private static final CubeDeformation DEFORM = CubeDeformation.NONE;")
    a("}")
    return "\n".join(L)


def _camel(n):
    parts = n.split("_")
    return parts[0] + "".join(w.capitalize() for w in parts[1:])


# ==========================================================================
# 5. main
# ==========================================================================

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ref = os.path.dirname(here)
    IN = os.path.join(ref, "bbmodel", "phantomcreeper.bbmodel")
    TEX = os.path.join(ref, "bbmodel", "creeperphantom.png")
    OUT = os.path.join(ref, "bbmodel")

    bb = load_bb(IN)
    tw, th = bb["resolution"]["width"], bb["resolution"]["height"]
    print("模型 %s  %d 个立方体  纹理 %dx%d" % (bb.get("name"), len(bb["elements"]), tw, th))

    parts = to_parts(bb)
    print("\n反算结果（Java 坐标，弧度）：")
    for p in parts:
        print("  %-18s parent=%-14s pivot=(%6.2f,%6.2f,%6.2f) rot=(%.4f,%.4f,%.4f)"
              % (p["name"], p["parent"] or "-", p["pivot"][0], p["pivot"][1], p["pivot"][2],
                 p["rot"][0], p["rot"][1], p["rot"][2]))
        for sub, box, tex, mir, rot, rel in p["cubes"]:
            tag = ("  ↳ 合成子部件 %s rot=(%.4f,%.4f,%.4f)" % (sub, rot[0], rot[1], rot[2])) if sub else ""
            print("       box(%-22s) tex%-9s %s%s"
                  % (",".join("%.1f" % v for v in box), str(tuple(tex)),
                     "MIRROR " if mir else "", tag))

    # 数值交叉验证
    verify_aabb(bb, parts)

    VIEWS = {
        "front": ((0, 1.0), (1, 1.0), 2, False),
        "side":  ((2, 1.0), (1, 1.0), 0, True),
        "top":   ((0, 1.0), (2, 1.0), 1, False),
    }
    texpath = os.path.join(OUT, "creeperphantom.png")
    vs = [render_parts(texpath, parts, ax, (360, 460), colored=True) for ax in VIEWS.values()]
    w, h, c = B.compose(vs, "struct")
    B.save_png(os.path.join(OUT, "check_phantomcreeper_struct.png"), w, h, c)
    vs = [render_parts(texpath, parts, ax, (360, 460), colored=False) for ax in VIEWS.values()]
    w, h, c = B.compose(vs, "tex")
    B.save_png(os.path.join(OUT, "check_phantomcreeper_tex.png"), w, h, c)
    print("已输出结构图 / 贴图渲染图")

    java = emit_java(parts, bb, "HybridCreeperModel", "HYBRID_CREEPER", "Phantom", tw, th)
    out_java = os.path.join(OUT, "HybridCreeperModel.java.txt")
    with open(out_java, "w", encoding="utf-8") as fp:
        fp.write(java)
    print("Java 源码草稿:", out_java)


if __name__ == "__main__":
    main()
