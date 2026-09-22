#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 MC 1.21.1 的原版模型定义生成 Blockbench .bbmodel 文件。

数据来源（全部从反编译源码逐字核实，无一处靠记忆）：
  - 几何/贴图偏移 : net/minecraft/client/model/PhantomModel.java, CreeperModel.java
  - 立方体坐标语义 : ModelPart.Cube 构造（(x,y,z) 是最小角，maxX = x + w）
  - 六面 UV 布局   : ModelPart.Cube 里 6 个 Polygon 的 UV 矩形
  - 坐标系变换     : Blockbench 官方源码 js/formats/java/modded_entity.ts
                     parse() 分支：
                       bone.origin = [-x, -y, z]
                       cube.from   = [origin.x - x - dx, origin.y - y - dy, origin.z + z]
                       根级元素 origin.y += 24
                       旋转 bb.rx = -radToDeg(jx), bb.ry = -radToDeg(jy), bb.rz = +radToDeg(jz)
  - Box UV 展开    : Blockbench 官方源码 js/outliner/types/cube.js -> updateUV()
                     的 box_uv 分支（下面 box_uv_faces() 是逐行移植）

输出：
  bbmodel/phantom.bbmodel, bbmodel/creeper.bbmodel
  bbmodel/preview_phantom.png, bbmodel/preview_creeper.png  （自检用渲染图）
"""

import base64
import json
import math
import os
import struct
import uuid as uuidlib
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
REF = os.path.dirname(HERE)
TEXDIR = os.path.join(REF, "textures")
OUTDIR = os.path.join(REF, "bbmodel")
os.makedirs(OUTDIR, exist_ok=True)


# ==========================================================================
# 1. 极简 PNG 读写（只用标准库，不依赖 Pillow）
# ==========================================================================

def load_png(path):
    """返回 (width, height, pixels)，pixels 是 bytearray，每像素 4 字节 RGBA。"""
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "不是 PNG"
    pos, idat, palette, trns = 8, b"", None, None
    w = h = depth = ctype = None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, depth, ctype, _comp, _filt, inter = struct.unpack(">IIBBBBB", body)
            assert inter == 0, "不支持隔行 PNG"
        elif typ == b"IDAT":
            idat += body
        elif typ == b"PLTE":
            palette = body
        elif typ == b"tRNS":
            trns = body
        elif typ == b"IEND":
            break
        pos += 12 + ln

    assert depth in (1, 2, 4, 8), "不支持位深 %s" % depth
    nch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    bpp = max(1, (nch * depth) // 8)          # 每像素字节数（用于滤波）
    stride = (w * nch * depth + 7) // 8
    out = bytearray()
    prev = bytearray(stride)
    p = 0
    for _y in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                c = prev[i - bpp] if i >= bpp else 0
                b = prev[i]
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        elif ft != 0:
            raise ValueError("未知滤波 %s" % ft)
        out += line
        prev = line

    # 解包到 RGBA
    px = bytearray(w * h * 4)
    for y in range(h):
        for x in range(w):
            if ctype == 6:
                i = (y * w + x) * 4
                px[i:i + 4] = out[i:i + 4]
            elif ctype == 2:
                i = (y * w + x) * 3
                j = (y * w + x) * 4
                px[j:j + 3] = out[i:i + 3]; px[j + 3] = 255
            elif ctype == 4:
                i = (y * w + x) * 2
                j = (y * w + x) * 4
                px[j] = px[j + 1] = px[j + 2] = out[i]; px[j + 3] = out[i + 1]
            elif ctype == 0:
                i = y * w + x
                j = (y * w + x) * 4
                px[j] = px[j + 1] = px[j + 2] = out[i]; px[j + 3] = 255
            else:  # 调色板
                row_bits = w * depth
                row_start = y * ((row_bits + 7) // 8)
                bit = x * depth
                byte = out[row_start + bit // 8]
                shift = 8 - depth - (bit % 8)
                idx = (byte >> shift) & ((1 << depth) - 1)
                j = (y * w + x) * 4
                px[j:j + 3] = palette[idx * 3:idx * 3 + 3]
                px[j + 3] = trns[idx] if (trns and idx < len(trns)) else 255
    return w, h, px


def save_png(path, w, h, px):
    """把 RGBA 像素写成 PNG。"""
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += px[y * w * 4:(y + 1) * w * 4]

    def chunk(typ, body):
        c = struct.pack(">I", len(body)) + typ + body
        return c + struct.pack(">I", zlib.crc32(typ + body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    open(path, "wb").write(png)


# ==========================================================================
# 2. Blockbench 坐标变换（逐行对应 modded_entity.ts 的 parse 分支）
# ==========================================================================

def bb_origin(abs_pivot):
    """MC 绝对轴心 -> Blockbench 全局原点。"""
    x, y, z = abs_pivot
    return [-x, 24.0 - y, z]


def bb_rotation(java_rot):
    """MC 弧度 -> Blockbench 度。rx/ry 取反，rz 不变。"""
    rx, ry, rz = java_rot
    return [-math.degrees(rx), -math.degrees(ry), math.degrees(rz)]


def bb_cube_from(origin, box):
    """MC addBox(x,y,z,w,h,d) -> Blockbench cube.from。"""
    x, y, z, dx, dy, dz = box
    return [origin[0] - x - dx, origin[1] - y - dy, origin[2] + z]


# ==========================================================================
# 3. Box UV 六面展开（逐行移植 cube.js -> updateUV() 的 box_uv 分支）
# ==========================================================================

def box_uv_faces(dx, dy, dz, u, v, mirror_uv=False):
    """
    返回 {face: [x1,y1,x2,y2]}。
    注意：up / down 的矩形在这个体系下本来就是"负尺寸"（翻转），这是
    Blockbench 自身的行为，不是笔误 —— 所以 x2<x1 / y2<y1 是正常的。
    """
    fl = [
        ["east",  [0.0,        float(dz)], [float(dz),  float(dy)]],
        ["west",  [float(dz) + dx, float(dz)], [float(dz), float(dy)]],
        ["up",    [float(dz) + dx, float(dz)], [-float(dx), -float(dz)]],
        ["down",  [float(dz) + dx * 2, 0.0],   [-float(dx), float(dz)]],
        ["south", [float(dz) * 2 + dx, float(dz)], [float(dx), float(dy)]],
        ["north", [float(dz), float(dz)], [float(dx), float(dy)]],
    ]
    if mirror_uv:
        for f in fl:
            f[1][0] += f[2][0]
            f[2][0] *= -1
        # 交换 east / west
        fl[0][1], fl[1][1] = fl[1][1], fl[0][1]
        fl[0][2], fl[1][2] = fl[1][2], fl[0][2]

    faces = {}
    for name, fr, sz in fl:
        faces[name] = [fr[0] + u, fr[1] + v, fr[0] + sz[0] + u, fr[1] + sz[1] + v]
    return faces


# ==========================================================================
# 4. 模型定义（直接照抄 PhantomModel.java / CreeperModel.java 的数值）
# ==========================================================================
# part = (name, parent, pivot, rotation_rad, [(box, texOffset, mirror), ...])

PHANTOM = {
    "id": "phantom",
    "tex": "phantom.png",
    "res": (64, 64),
    "parts": [
        ("body", None, (0.0, 0.0, 0.0), (-0.1, 0.0, 0.0),
         [((-3.0, -2.0, -8.0, 5.0, 3.0, 9.0), (0, 8), False)]),
        ("head", "body", (0.0, 1.0, -7.0), (0.2, 0.0, 0.0),
         [((-4.0, -2.0, -5.0, 7.0, 3.0, 5.0), (0, 0), False)]),
        ("tail_base", "body", (0.0, -2.0, 1.0), None,
         [((-2.0, 0.0, 0.0, 3.0, 2.0, 6.0), (3, 20), False)]),
        ("tail_tip", "tail_base", (0.0, 0.5, 6.0), None,
         [((-1.0, 0.0, 0.0, 1.0, 1.0, 6.0), (4, 29), False)]),
        ("left_wing_base", "body", (2.0, -2.0, -8.0), (0.0, 0.0, 0.1),
         [((0.0, 0.0, 0.0, 6.0, 2.0, 9.0), (23, 12), False)]),
        ("left_wing_tip", "left_wing_base", (6.0, 0.0, 0.0), (0.0, 0.0, 0.1),
         [((0.0, 0.0, 0.0, 13.0, 1.0, 9.0), (16, 24), False)]),
        ("right_wing_base", "body", (-3.0, -2.0, -8.0), (0.0, 0.0, -0.1),
         [((-6.0, 0.0, 0.0, 6.0, 2.0, 9.0), (23, 12), True)]),
        ("right_wing_tip", "right_wing_base", (-6.0, 0.0, 0.0), (0.0, 0.0, -0.1),
         [((-13.0, 0.0, 0.0, 13.0, 1.0, 9.0), (16, 24), True)]),
    ],
}

CREEPER = {
    "id": "creeper",
    "tex": "creeper.png",
    "res": (64, 32),
    "parts": [
        ("head", None, (0.0, 6.0, 0.0), None,
         [((-4.0, -8.0, -4.0, 8.0, 8.0, 8.0), (0, 0), False)]),
        ("body", None, (0.0, 6.0, 0.0), None,
         [((-4.0, 0.0, -2.0, 8.0, 12.0, 4.0), (16, 16), False)]),
        ("right_hind_leg", None, (-2.0, 18.0, 4.0), None,
         [((-2.0, 0.0, -2.0, 4.0, 6.0, 4.0), (0, 16), False)]),
        ("left_hind_leg", None, (2.0, 18.0, 4.0), None,
         [((-2.0, 0.0, -2.0, 4.0, 6.0, 4.0), (0, 16), False)]),
        ("right_front_leg", None, (-2.0, 18.0, -4.0), None,
         [((-2.0, 0.0, -2.0, 4.0, 6.0, 4.0), (0, 16), False)]),
        ("left_front_leg", None, (2.0, 18.0, -4.0), None,
         [((-2.0, 0.0, -2.0, 4.0, 6.0, 4.0), (0, 16), False)]),
    ],
}

# 数值直接照抄 DolphinModel.java（8 个立方体 / 7 个部件，纹理 64x64）
DOLPHIN = {
    "id": "dolphin",
    "tex": "dolphin.png",
    "res": (64, 64),
    "parts": [
        ("body", None, (0.0, 22.0, -5.0), None,
         [((-4.0, -7.0, 0.0, 8.0, 7.0, 13.0), (22, 0), False)]),
        ("back_fin", "body", (0.0, 0.0, 0.0), (math.pi / 3.0, 0.0, 0.0),
         [((-0.5, 0.0, 8.0, 1.0, 4.0, 5.0), (51, 0), False)]),
        ("left_fin", "body", (2.0, -2.0, 4.0),
         (math.pi / 3.0, 0.0, math.pi * 2.0 / 3.0),
         [((-0.5, -4.0, 0.0, 1.0, 4.0, 7.0), (48, 20), True)]),
        ("right_fin", "body", (-2.0, -2.0, 4.0),
         (math.pi / 3.0, 0.0, -math.pi * 2.0 / 3.0),
         [((-0.5, -4.0, 0.0, 1.0, 4.0, 7.0), (48, 20), False)]),
        ("tail", "body", (0.0, -2.5, 11.0), (-0.10471976, 0.0, 0.0),
         [((-2.0, -2.5, 0.0, 4.0, 5.0, 11.0), (0, 19), False)]),
        ("tail_fin", "tail", (0.0, 0.0, 9.0), None,
         [((-5.0, -0.5, 0.0, 10.0, 1.0, 6.0), (19, 20), False)]),
        ("head", "body", (0.0, -4.0, -3.0), None,
         [((-4.0, -3.0, -3.0, 8.0, 7.0, 6.0), (0, 0), False)]),
        ("nose", "head", (0.0, 0.0, 0.0), None,
         [((-1.0, 2.0, -7.0, 2.0, 2.0, 4.0), (0, 13), False)]),
    ],
}


def resolve(model):
    """算出每个部件的绝对轴心，并生成 Blockbench 侧的部件数据。"""
    by_name = {p[0]: p for p in model["parts"]}
    abs_pivot = {}

    def calc(name):
        if name in abs_pivot:
            return abs_pivot[name]
        _n, parent, pivot, _rot, _cubes = by_name[name]
        base = calc(parent) if parent else (0.0, 0.0, 0.0)
        abs_pivot[name] = (base[0] + pivot[0], base[1] + pivot[1], base[2] + pivot[2])
        return abs_pivot[name]

    out = []
    for name, parent, pivot, rot, cubes in model["parts"]:
        ap = calc(name)
        o = bb_origin(ap)
        out.append({
            "name": name,
            "parent": parent,
            "origin": o,
            "origin_java": ap,
            "rotation": bb_rotation(rot) if rot else [0.0, 0.0, 0.0],
            "cubes": [{"from": bb_cube_from(o, box), "box": box,
                       "texOffset": tex, "mirror": mir} for box, tex, mir in cubes],
        })
    return out


def build_bbmodel(model, tex_path, model_name):
    tw, th, texpx = load_png(tex_path)
    assert (tw, th) == model["res"], "纹理尺寸 %s 与预期 %s 不符" % ((tw, th), model["res"])

    parts = resolve(model)
    elements = []
    groups = {}

    def new_uuid():
        return str(uuidlib.uuid4())

    for part in parts:
        children = []
        for cube in part["cubes"]:
            x, y, z, dx, dy, dz = cube["box"]
            u, v = cube["texOffset"]
            faces = box_uv_faces(dx, dy, dz, u, v, cube["mirror"])
            eid = new_uuid()
            elements.append({
                "name": part["name"],
                "box_uv": True,
                "uv_offset": [u, v],
                "mirror_uv": cube["mirror"],
                "rescale": False,
                "locked": False,
                "render_order": "default",
                "allow_mirror_modeling": True,
                "from": [round(c, 6) for c in cube["from"]],
                "to": [round(cube["from"][i] + s, 6) for i, s in enumerate((dx, dy, dz))],
                "autouv": 0,
                "color": 0,
                "origin": [round(c, 6) for c in part["origin"]],
                "faces": {n: {"uv": [round(c, 6) for c in r], "texture": 0}
                          for n, r in faces.items()},
                "type": "cube",
                "uuid": eid,
            })
            children.append(eid)

        groups[part["name"]] = {
            "name": part["name"],
            "origin": [round(c, 6) for c in part["origin"]],
            "rotation": [round(c, 6) for c in part["rotation"]],
            "uuid": new_uuid(),
            "export": True,
            "mirror_uv": False,
            "isOpen": True,
            "children": children,
        }

    # 子部件挂到父部件下 —— 注意必须是"内联的 group 对象"，不是 uuid 字符串。
    # Blockbench 的 Outliner.loadJSON/processList 只在 children 里把 dict 当
    # 嵌套 group 递归展开，字符串只会被当成 element 的 uuid 去查表。
    for part in parts:
        if part["parent"]:
            groups[part["parent"]]["children"].append(groups[part["name"]])
    top = [groups[p["name"]] for p in parts if not p["parent"]]

    data_url = "data:image/png;base64," + base64.b64encode(
        open(tex_path, "rb").read()).decode("ascii")

    return {
        "meta": {
            "format_version": "4.10",
            "model_format": "modded_entity",
            "box_uv": True,
        },
        "name": model_name,
        "parent": "",
        "ambientocclusion": False,
        "front_gui_light": False,
        "visible_box": [1, 1, 0],
        "variable_placeholders": "",
        "resolution": {"width": tw, "height": th},
        "elements": elements,
        "groups": [],
        "outliner": top,
        "textures": [{
            "path": "",
            "name": model["tex"],
            "folder": "entity",
            "namespace": "",
            "id": "0",
            "particle": False,
            "render_mode": "default",
            "visible": True,
            "mode": "bitmap",
            "saved": False,
            "uuid": new_uuid(),
            "source": data_url,
            "internal": True,
        }],
        "animations": [],
        "animation_controllers": [],
        "display": {},
        "reference_images": [],
        "export_options": {},
    }


# ==========================================================================
# 5. 自检渲染器 —— 忠实移植 ModelPart.Cube 的顶点/UV 生成
# ==========================================================================
# Polygon 顶点索引 -> 矩形角（由 Polygon 构造函数的 remap 逐行推出）：
#   非镜像： 0->(u1,v0)  1->(u0,v0)  2->(u0,v1)  3->(u1,v1)
#   镜像  ： 0->(u1,v1)  1->(u0,v1)  2->(u0,v0)  3->(u1,v0)     (顶点数组反转导致 V 翻转)
#
# 各面的 (u0,v0,u1,v1) 矩形取自 ModelPart.Cube 里 6 个 Polygon 的实参。

def mc_faces(box, tex_offset, mirror, inflate=0.0):
    x, y, z, dx, dy, dz = box
    u, v = tex_offset
    minX, minY, minZ = x - inflate, y - inflate, z - inflate
    maxX, maxY, maxZ = x + dx + inflate, y + dy + inflate, z + dz + inflate
    m = mirror
    # 顶点表：(pos, 在 polygon 数组里的局部序号 0..3)
    V7 = ((minX, minY, minZ), 0, 0)
    V_ = ((maxX, minY, minZ), 0, 8)
    V1 = ((maxX, maxY, minZ), 8, 8)
    V2 = ((minX, maxY, minZ), 8, 0)
    V3 = ((minX, minY, maxZ), 0, 0)
    V4 = ((maxX, minY, maxZ), 0, 8)
    V5 = ((maxX, maxY, maxZ), 8, 8)
    V6 = ((minX, maxY, maxZ), 8, 0)

    f4, f5 = float(u), float(u) + dz
    f6 = f5 + dx
    f8 = f6 + dz
    f9 = f8 + dx
    f10, f11 = float(v), float(v) + dz
    f12 = f11 + dy

    # (面名, 顶点数组, u0, v0, u1, v1)
    polys = [
        ("down",  [V4, V3, V7, V_], f5, f10, f6, f11),
        ("up",    [V1, V2, V6, V5], f6, f11, f6 + dx, f10),
        ("west",  [V7, V3, V6, V2], f4, f11, f5, f12),
        ("north", [V_, V7, V2, V1], f5, f11, f6, f12),
        ("east",  [V4, V_, V1, V5], f6, f11, f8, f12),
        ("south", [V3, V4, V5, V6], f8, f11, f9, f12),
    ]
    out = []
    for name, arr, u0, v0, u1, v1 in polys:
        verts = [a[0] for a in arr]
        if m:
            verts = verts[::-1]
        corner = [(u1, v1), (u0, v1), (u0, v0), (u1, v0)] if m else \
                 [(u1, v0), (u0, v0), (u0, v1), (u1, v1)]
        out.append((name, list(zip(verts, corner))))
    return out


def render(model, tex_path, axes, size=(360, 460)):
    """两遍渲染：先收集所有三角形算出包围盒做自动适配，再光栅化。"""
    tw, th, texpx = load_png(tex_path)
    parts = resolve(model)
    projx, projy, depth_axis, keep_max = axes

    def rotation_matrix(rx, ry, rz):
        rx, ry, rz = math.radians(rx), math.radians(ry), math.radians(rz)
        cX, sX = math.cos(rx), math.sin(rx)
        cY, sY = math.cos(ry), math.sin(ry)
        cZ, sZ = math.cos(rz), math.sin(rz)

        def mul(A, B):
            return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)]
                    for i in range(3)]
        Rz = [[cZ, -sZ, 0], [sZ, cZ, 0], [0, 0, 1]]
        Ry = [[cY, 0, sY], [0, 1, 0], [-sY, 0, cY]]
        Rx = [[1, 0, 0], [0, cX, -sX], [0, sX, cX]]
        return mul(Rx, mul(Ry, Rz))

    def apply(R, p):
        return [sum(R[i][k] * p[k] for k in range(3)) for i in range(3)]

    by_name = {p["name"]: p for p in parts}
    chain_cache = {}

    def chain_of(name):
        if name in chain_cache:
            return chain_cache[name]
        p = by_name[name]
        ax, ay, az = p["origin_java"]
        R = rotation_matrix(*p["rotation"])

        def local(v):
            q = apply(R, [v[0], v[1], v[2]])
            return [q[0] + ax, q[1] + ay, q[2] + az]

        parent = chain_of(p["parent"]) if p["parent"] else None
        full = (lambda v: parent(local(v))) if parent else local
        chain_cache[name] = full
        return full

    tris = []          # (screen_a, screen_b, screen_c, depth_a,b,c, uv_a,uv_b,uv_c)
    sx_min = sy_min = 1e18
    sx_max = sy_max = -1e18
    for part in parts:
        emit = chain_of(part["name"])
        for cube in part["cubes"]:
            for _n, vc in mc_faces(cube["box"], cube["texOffset"], cube["mirror"]):
                pts = [emit(v[0]) for v in vc]
                uvs = [v[1] for v in vc]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    idx = [tri[0], tri[1], tri[2]]
                    scr = [(projx[1] * pts[i][projx[0]], projy[1] * pts[i][projy[0]])
                           for i in idx]
                    dep = [pts[i][depth_axis] for i in idx]
                    uvv = [uvs[i] for i in idx]
                    for s in scr:
                        sx_min = min(sx_min, s[0]); sx_max = max(sx_max, s[0])
                        sy_min = min(sy_min, s[1]); sy_max = max(sy_max, s[1])
                    tris.append((scr, dep, uvv))

    W, H = size
    span_x = max(sx_max - sx_min, 1e-6)
    span_y = max(sy_max - sy_min, 1e-6)
    scale = min((W - 24) / span_x, (H - 24) / span_y, 24.0)
    off_x = W / 2.0 - (sx_min + sx_max) / 2.0 * scale
    off_y = H / 2.0 - (sy_min + sy_max) / 2.0 * scale

    color = bytearray(W * H * 4)
    for i in range(0, len(color), 4):
        color[i] = color[i + 1] = color[i + 2] = 24; color[i + 3] = 255
    zbuf = [-1e18 if keep_max else 1e18] * (W * H)

    def sample(u, v):
        uu = int(math.floor(u)) % tw
        vv = int(math.floor(v)) % th
        i = (vv * tw + uu) * 4
        return texpx[i], texpx[i + 1], texpx[i + 2], texpx[i + 3]

    for scr, dep, uvv in tris:
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
                if keep_max:
                    if d <= zbuf[idx]:
                        continue
                else:
                    if d >= zbuf[idx]:
                        continue
                u = w0 * ua[0] + w1 * ub[0] + w2 * uc[0]
                v = w0 * ua[1] + w1 * ub[1] + w2 * uc[1]
                r, g, b_, al = sample(u, v)
                zbuf[idx] = d
                if al == 0:
                    continue
                j = idx * 4
                color[j] = r; color[j + 1] = g; color[j + 2] = b_; color[j + 3] = 255
    return W, H, color


def compose(views, name):
    """把多视图横向拼成一张 PNG，并画出网格。"""
    pad = 8
    total_w = sum(w for w, _h, _c in views) + pad * (len(views) + 1)
    total_h = max(h for _w, h, _c in views) + pad * 2
    out = bytearray(total_w * total_h * 4)
    for i in range(0, len(out), 4):
        out[i] = out[i + 1] = out[i + 2] = 26; out[i + 3] = 255
    ox = pad
    for w, h, c in views:
        for y in range(h):
            src = y * w * 4
            dst = ((y + pad) * total_w + ox) * 4
            out[dst:dst + w * 4] = c[src:src + w * 4]
        ox += w + pad
    return total_w, total_h, out


PALETTE = [
    (235, 87, 87), (242, 153, 74), (242, 201, 76), (111, 207, 151),
    (86, 204, 242), (47, 128, 237), (155, 81, 224), (235, 87, 160),
    (0, 200, 180), (255, 140, 0),
]


def render_debug(model, axes, size=(360, 460)):
    """按部件配色的结构调试图：不带贴图，纯色块，用来看清各部件的位置关系。"""
    parts = resolve(model)
    projx, projy, depth_axis, keep_max = axes

    def rotation_matrix(rx, ry, rz):
        rx, ry, rz = math.radians(rx), math.radians(ry), math.radians(rz)
        cX, sX = math.cos(rx), math.sin(rx)
        cY, sY = math.cos(ry), math.sin(ry)
        cZ, sZ = math.cos(rz), math.sin(rz)

        def mul(A, B):
            return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)]
                    for i in range(3)]
        Rz = [[cZ, -sZ, 0], [sZ, cZ, 0], [0, 0, 1]]
        Ry = [[cY, 0, sY], [0, 1, 0], [-sY, 0, cY]]
        Rx = [[1, 0, 0], [0, cX, -sX], [0, sX, cX]]
        return mul(Rx, mul(Ry, Rz))

    def apply(R, p):
        return [sum(R[i][k] * p[k] for k in range(3)) for i in range(3)]

    by_name = {p["name"]: p for p in parts}
    cache = {}

    def chain_of(name):
        if name in cache:
            return cache[name]
        p = by_name[name]
        ax, ay, az = p["origin_java"]
        R = rotation_matrix(*p["rotation"])

        def local(v):
            q = apply(R, [v[0], v[1], v[2]])
            return [q[0] + ax, q[1] + ay, q[2] + az]

        parent = chain_of(p["parent"]) if p["parent"] else None
        f = (lambda v: parent(local(v))) if parent else local
        cache[name] = f
        return f

    tris, sx_min, sy_min, sx_max, sy_max = [], 1e18, 1e18, -1e18, -1e18
    for pi, part in enumerate(parts):
        col = PALETTE[pi % len(PALETTE)]
        emit = chain_of(part["name"])
        for cube in part["cubes"]:
            for _n, vc in mc_faces(cube["box"], cube["texOffset"], cube["mirror"]):
                pts = [emit(v[0]) for v in vc]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    scr = [(projx[1] * pts[i][projx[0]], projy[1] * pts[i][projy[0]]) for i in tri]
                    dep = [pts[i][depth_axis] for i in tri]
                    for s in scr:
                        sx_min = min(sx_min, s[0]); sx_max = max(sx_max, s[0])
                        sy_min = min(sy_min, s[1]); sy_max = max(sy_max, s[1])
                    tris.append((scr, dep, col))

    W, H = size
    scale = min((W - 24) / max(sx_max - sx_min, 1e-6), (H - 24) / max(sy_max - sy_min, 1e-6), 24.0)
    off_x = W / 2.0 - (sx_min + sx_max) / 2.0 * scale
    off_y = H / 2.0 - (sy_min + sy_max) / 2.0 * scale

    color = bytearray(W * H * 4)
    for i in range(0, len(color), 4):
        color[i] = color[i + 1] = color[i + 2] = 24; color[i + 3] = 255
    zbuf = [-1e18 if keep_max else 1e18] * (W * H)

    for scr, dep, col in tris:
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
                # 用深度做一点明暗，方便区分前后
                shade = 0.55 + 0.45 * (1.0 if keep_max else 0.0)
                color[j] = int(col[0] * shade)
                color[j + 1] = int(col[1] * shade)
                color[j + 2] = int(col[2] * shade)
                color[j + 3] = 255
    return W, H, color


# ==========================================================================
# 6. 主流程
# ==========================================================================

def verify(model, bb):
    """
    独立数值校验：不用 bb_cube_from 的公式，而是从"坐标映射语义"直接推导
    每个立方体应有的 Blockbench 范围，再和生成结果逐个比对。

        映射：bb.x = -java.x   bb.y = 24 - java.y   bb.z = java.z
        所以：bb_from_x = -java_max_x      bb_to_x = -java_min_x
              bb_from_y = 24 - java_max_y bb_to_y = 24 - java_min_y
              bb_from_z =  java_min_z     bb_to_z =  java_max_z

    同时校验 hierachy（outliner 里子部件是否挂在正确的父级下）。
    """
    ok = True
    parts = resolve(model)
    idx = 0
    print("    %-18s %-28s %-26s %s" % ("部件", "java 绝对范围 X/Y/Z", "bb 范围 X/Y/Z", "结果"))
    for part in parts:
        px, py, pz = part["origin_java"]
        for cube in part["cubes"]:
            x, y, z, dx, dy, dz = cube["box"]
            jx = (px + x, px + x + dx)
            jy = (py + y, py + y + dy)
            jz = (pz + z, pz + z + dz)
            exp_from = [-jx[1], 24.0 - jy[1], jz[0]]
            exp_to = [-jx[0], 24.0 - jy[0], jz[1]]
            el = bb["elements"][idx]
            idx += 1
            got_from = el["from"]
            got_to = el["to"]
            good = all(abs(a - b) < 1e-6 for a, b in
                       zip(exp_from + exp_to, got_from + got_to))
            ok = ok and good
            print("    %-18s X[%5.1f,%5.1f] Y[%5.1f,%5.1f] Z[%5.1f,%5.1f]  ->  "
                  "X[%5.1f,%5.1f] Y[%5.1f,%5.1f] Z[%5.1f,%5.1f]  %s"
                  % (part["name"], jx[0], jx[1], jy[0], jy[1], jz[0], jz[1],
                     got_from[0], got_to[0], got_from[1], got_to[1],
                     got_from[2], got_to[2], "OK" if good else "!! 不一致"))

    # 层级校验：每个有父级的部件，其父级必须能在 outliner 树里找到
    all_groups = _all_groups(bb)
    names = {g["name"] for g in all_groups}
    for part in parts:
        if part["parent"] and part["parent"] not in names:
            print("    !! 父级缺失:", part["name"], "->", part["parent"])
            ok = False
    # 每个 element 必须出现在 outliner 的 children 里（否则 Blockbench 里会"丢"立方体）
    referenced = set()

    def collect(nodes):
        for n in nodes:
            if isinstance(n, str):
                referenced.add(n)
            elif isinstance(n, dict):
                collect(n.get("children", []))
    collect(bb["outliner"])
    for el in bb["elements"]:
        if el["uuid"] not in referenced:
            print("    !! 立方体未挂进 outliner:", el["name"], el["uuid"])
            ok = False
    # UV：盒式 UV 的六面矩形必须落在纹理范围内
    tw, th = model["res"]
    for el in bb["elements"]:
        for face, f in el["faces"].items():
            xs = [f["uv"][0], f["uv"][2]]
            ys = [f["uv"][1], f["uv"][3]]
            if min(xs) < 0 or max(xs) > tw or min(ys) < 0 or max(ys) > th:
                print("    !! UV 越界:", el["name"], face, f["uv"], "纹理", (tw, th))
                ok = False
    print("    => 校验%s" % ("全部通过" if ok else "**失败**"))
    return ok


def _all_groups(bb):
    out = []

    def walk(nodes):
        for n in nodes:
            if isinstance(n, dict):
                out.append(n)
                walk(n.get("children", []))
    walk(bb["outliner"])
    return out


VIEWS = {
    # (屏幕x: (模型轴, 符号), 屏幕y: (模型轴, 符号), 深度轴, 深度取大者为远)
    # MC 模型空间：+X = 实体自身的左, +Y = 下, -Z = 实体正前方
    "front(-Z)": ((0, 1.0), (1, 1.0), 2, False),
    "back(+Z)":  ((0, -1.0), (1, 1.0), 2, True),
    "side(+X)":  ((2, 1.0), (1, 1.0), 0, True),
    "top(-Y)":   ((0, 1.0), (2, 1.0), 1, False),
}


def main():
    report = []
    for model, basename in ((PHANTOM, "phantom"), (CREEPER, "creeper"), (DOLPHIN, "dolphin")):
        tex = os.path.join(TEXDIR, model["tex"])
        bb = build_bbmodel(model, tex, basename + "_vanilla")

        path = os.path.join(OUTDIR, basename + ".bbmodel")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(bb, f, ensure_ascii=False, indent=1)
        verify(model, bb)

        views = []
        for label, axes in VIEWS.items():
            w, h, c = render(model, tex, axes, (360, 460))
            views.append((w, h, c))
        tw_, th_, px = compose(views, basename)
        save_png(os.path.join(OUTDIR, "preview_" + basename + ".png"), tw_, th_, px)

        # 结构调试图（按部件配色）
        dviews = []
        for label, axes in VIEWS.items():
            w, h, c = render_debug(model, axes, (360, 460))
            dviews.append((w, h, c))
        tw_, th_, px = compose(dviews, basename)
        save_png(os.path.join(OUTDIR, "debug_" + basename + ".png"), tw_, th_, px)
        for i, p in enumerate(resolve(model)):
            print("    %-18s %s" % (p["name"], PALETTE[i % len(PALETTE)]))

        xs = [e["from"][0] for e in bb["elements"]] + [e["to"][0] for e in bb["elements"]]
        ys = [e["from"][1] for e in bb["elements"]] + [e["to"][1] for e in bb["elements"]]
        zs = [e["from"][2] for e in bb["elements"]] + [e["to"][2] for e in bb["elements"]]
        report.append("%-8s parts=%d cubes=%d  bb范围 X[%.0f,%.0f] Y[%.0f,%.0f] Z[%.0f,%.0f]"
                      % (basename, len(bb["outliner"]), len(bb["elements"]),
                         min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    print("\n".join(report))
    print("输出目录:", OUTDIR)


if __name__ == "__main__":
    main()
