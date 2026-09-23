#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成「充能能量」贴图：assets/hybridcreeper/textures/entity/charged_energy.png

供 com.hybridcreeper.client.PoweredOverlayLayer 使用 —— 被闪电劈中的生物会裹上
一层滚动的蓝色能量，用 RenderType.energySwirl 以加法混合绘制。

======================================================================
贴图规格（由能量着色器倒推出来的，不是拍脑袋定的）
======================================================================
rendertype_energy_swirl.fsh 的关键两行：

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) { discard; }

配合 ADDITIVE_TRANSPARENCY（blendFunc ONE, ONE）：

  · 最终叠加亮度 = texel.rgb × vertexColor.rgb
    而 PoweredOverlayLayer 传的顶点色是 0xFF808080，即各通道 0x80/255 ≈ 0.5。
    => 所以贴图要画得比目标亮度「亮一倍」，由顶点色折半回来。
  · alpha 只负责「够不够不透明、要不要丢弃」，不参与颜色混合。

原版 creeper_armor.png 的实测特征（参考用）：
  64×32、非透明像素 31.4%、平均色 (39,113,191)、
  alpha 是【二值】的 —— 只有 0 和 224~255 两档，没有中间过渡。
  也就是说原版那身能量是「硬边图案」，不是一层均匀的雾。

======================================================================
为什么是 128×128 且全幅无接缝
======================================================================
1. 图层滚动的是纹理坐标（TextureMat 平移）。**只要有一处接缝，滚动时就会看到
   明显的硬边在模型上爬**。所以噪声必须是可平铺的 —— 这里用「网格索引取模」的
   值噪声实现，天生无缝，不需要事后羽化边缘。
2. 用全幅无缝图而不是像原版那样按模型 UV 画异形图，是因为：
   · 模型只会采样到自己 UV 覆盖到的像素，全幅效果与异形图完全一样；
   · 一张图能同时服务两只 UV 排布完全不同的生物；
   · 纯算法生成，不含任何 Mojang 素材衍生，没有版权负担。
3. 用 128 而不是 64：噪声细节更细，滚动时更顺滑。代价只有 64KB。

运行：python make_charged_energy.py
"""

import math
import os
import random
import struct
import zlib

# ---------------------------------------------------------------- 配置

OUT_SIZE = 128                 # 输出边长（正方形，保证 2 的幂以便无缝）
SEED = 20260923                # 固定种子，保证可复现

# 输出路径（相对本脚本：tools/ -> ../../src/main/resources/...）
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
TARGET = os.path.join(
    PROJECT_ROOT, "src", "main", "resources",
    "assets", "hybridcreeper", "textures", "entity", "charged_energy.png")
PREVIEW = os.path.join(PROJECT_ROOT, "vanilla-reference", "bbmodel",
                       "preview_charged_energy.png")

# 可见性阈值不写死，而是按「目标覆盖率」反推 —— 见 build()。
# 原因：v 的分布是偏高的（脊化噪声 skew 很大），写死阈值很难对准，
# 换个权重就得重调一遍。按分位数取阈值则永远命中目标。
#
# 注意：着色器在 alpha < 0.1（即 26/255）时 discard，而边缘有一条抗锯齿斜坡，
# 所以真实可见比例会略高于这里的目标值。0.42 对应实测约 45%。
TARGET_COVERAGE = 0.42

# 边缘抗锯齿斜坡的宽度（相对 v 的取值范围）。太窄会有锯齿，太宽就成雾了。
EDGE_RAMP = 0.05

# 颜色端点：暗蓝 -> 亮青白。中间值大致落在原版平均色 (39,113,191) 附近。
COLOR_DARK = (22, 62, 158)
COLOR_BRIGHT = (208, 248, 255)

# 主体权重 / 脊化权重。脊化那条（电弧纤维）给得比主体重，
# 是为了让轮廓偏向「细长亮线」而不是「大块云团」。
W_BODY = 0.50
W_VEIN = 0.85

# 脊化噪声的锐化指数。>1 会把亮线收得更细更亮，电弧感更强。
VEIN_SHARPEN = 1.6


# ---------------------------------------------------------------- PNG 读写

def save_png(path, w, h, rgba):
    """写 8bit RGBA PNG（filter 全 0，不压缩优化，够用且无依赖）。"""
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    out += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(out)


# ---------------------------------------------------------------- 噪声

def tileable_value_noise(size, cells, rng):
    """
    可平铺的值噪声。cells 必须整除 size。

    做法：随机网格 cells×cells，取值时对索引取模 —— 于是右边缘的插值邻居
    正好是左边缘，天然无缝，不需要额外处理边界。
    """
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [0.0] * (size * size)
    scale = cells / size

    for y in range(size):
        fy = y * scale
        y0 = int(fy)
        ty = fy - y0
        ty = ty * ty * (3.0 - 2.0 * ty)          # smoothstep 插值
        ry0 = y0 % cells
        ry1 = (y0 + 1) % cells
        row0 = grid[ry0]
        row1 = grid[ry1]
        base = y * size
        for x in range(size):
            fx = x * scale
            x0 = int(fx)
            tx = fx - x0
            tx = tx * tx * (3.0 - 2.0 * tx)
            rx0 = x0 % cells
            rx1 = (x0 + 1) % cells

            v00 = row0[rx0]
            v10 = row0[rx1]
            v01 = row1[rx0]
            v11 = row1[rx1]
            v0 = v00 + (v10 - v00) * tx
            v1 = v01 + (v11 - v01) * tx
            out[base + x] = v0 + (v1 - v0) * ty
    return out


def fbm(size, octaves, rng):
    """分形叠加。返回 [0,1] 的列表。"""
    acc = [0.0] * (size * size)
    wsum = 0.0
    for cells, weight in octaves:
        layer = tileable_value_noise(size, cells, rng)
        wsum += weight
        for i, v in enumerate(layer):
            acc[i] += v * weight
    return [v / wsum for v in acc]


# ---------------------------------------------------------------- 合成

def smoothstep(e0, e1, x):
    if e1 == e0:
        return 0.0 if x < e0 else 1.0
    t = (x - e0) / (e1 - e0)
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    return t * t * (3.0 - 2.0 * t)


def build():
    rng = random.Random(SEED)
    size = OUT_SIZE
    n = size * size

    # ① 主体等离子团：低频大块 + 中高频碎纹。
    #    频率刻意取得低 —— 贴图会被 UV 铺满整个模型，128px 铺在 2 格高的生物身上，
    #    再细下去就成"噪点"而不是"光斑"了。对齐原版 creeper_armor 那种大块观感。
    body = fbm(size, [(2, 1.00), (4, 0.62), (8, 0.36), (16, 0.20)], rng)

    # ② 电弧纤维：另一个独立噪声做「脊化」变换，
    #    1-|2n-1| 会把噪声的等值线转成细长的亮线，看起来像电流。
    rng2 = random.Random(SEED + 7)
    veins = fbm(size, [(4, 1.00), (8, 0.70), (16, 0.45), (32, 0.25)], rng2)
    ridged = [(1.0 - abs(2.0 * v - 1.0)) ** VEIN_SHARPEN for v in veins]

    total_w = W_BODY + W_VEIN
    field = [(body[i] * W_BODY + ridged[i] * W_VEIN) / total_w for i in range(n)]

    # 阈值 = 按目标覆盖率取分位数。这样无论上面权重怎么调，覆盖率都稳。
    ordered = sorted(field)
    thr = ordered[min(n - 1, int(n * (1.0 - TARGET_COVERAGE)))]
    # 亮端取 99.5 分位而不是最大值 —— 用最大值会把渐变压得太扁，
    # 结果整片偏暗蓝、只有零星几点白。
    top = ordered[min(n - 1, int(n * 0.995))]
    span = max(top - thr, 1e-6)
    lo = thr - EDGE_RAMP * 0.5
    hi = thr + EDGE_RAMP * 0.5

    px = bytearray(n * 4)
    visible = 0
    for i in range(n):
        v = field[i]

        # 抗锯齿：阈值附近做一条窄斜坡，避免硬阈值的锯齿（原版是纯二值，更糙）
        a = int(round(smoothstep(lo, hi, v) * 255.0))
        t = (v - thr) / span
        t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)

        # 颜色：暗蓝 -> 亮青白。t 做 0.8 次幂，让亮核更集中。
        tt = t ** 0.8
        r = int(COLOR_DARK[0] + (COLOR_BRIGHT[0] - COLOR_DARK[0]) * tt)
        g = int(COLOR_DARK[1] + (COLOR_BRIGHT[1] - COLOR_DARK[1]) * tt)
        b = int(COLOR_DARK[2] + (COLOR_BRIGHT[2] - COLOR_DARK[2]) * tt)

        j = i * 4
        px[j] = r
        px[j + 1] = g
        px[j + 2] = b
        px[j + 3] = a
        if a > 25:              # 26/255 以上才不会被着色器 discard
            visible += 1

    return px, visible


# ---------------------------------------------------------------- 校验与预览

def check_seam(px, size):
    """
    无缝校验：比较「首尾相接处」的像素差 与「内部相邻列」的平均差。
    如果接缝处的差没有明显大于内部，就说明平铺看不出来。
    """
    def col_diff(xa, xb):
        s = 0
        for y in range(size):
            ia = (y * size + xa) * 4
            ib = (y * size + xb) * 4
            s += abs(px[ia] - px[ib]) + abs(px[ia + 3] - px[ib + 3])
        return s / size

    seam = col_diff(size - 1, 0)          # 接缝：最后一列 vs 第一列
    inner = [col_diff(x, x + 1) for x in range(size - 1)]
    avg_inner = sum(inner) / len(inner)
    worst_inner = max(inner)

    print("  接缝处列差 : %8.2f" % seam)
    print("  内部列差均值: %8.2f" % avg_inner)
    print("  内部列差最大: %8.2f" % worst_inner)
    ok = seam <= worst_inner * 1.15
    print("  => %s" % ("无缝（接缝差异未超过内部正常波动）✓" if ok else "!! 疑似有接缝"))
    return ok


def make_preview(px, size, path, scale=3, tiles=2):
    """把贴图 2×2 平铺再放大，用来看接缝。"""
    W = size * tiles * scale
    H = size * tiles * scale
    out = bytearray(W * H * 4)
    for y in range(H):
        for x in range(W):
            sx = (x // scale) % size
            sy = (y // scale) % size
            si = (sy * size + sx) * 4
            a = px[si + 3]
            # 铺在深灰底上，模拟暗环境下的观感
            bg = 18
            r = min(255, bg + px[si] * a // 255)
            g = min(255, bg + px[si + 1] * a // 255)
            b = min(255, bg + px[si + 2] * a // 255)
            di = (y * W + x) * 4
            out[di] = r
            out[di + 1] = g
            out[di + 2] = b
            out[di + 3] = 255
    save_png(path, W, H, out)
    return W, H


# ---------------------------------------------------------------- main

def main():
    print("生成充能能量贴图 %d×%d（种子 %d）" % (OUT_SIZE, OUT_SIZE, SEED))
    print()

    px, visible = build()
    total = OUT_SIZE * OUT_SIZE
    cov = visible / total
    print("  非透明像素: %d / %d = %.1f%%（原版参考 31.4%%，目标 %.0f%%）"
          % (visible, total, cov * 100, TARGET_COVERAGE * 100))

    print()
    print("无缝校验:")
    check_seam(px, OUT_SIZE)

    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    save_png(TARGET, OUT_SIZE, OUT_SIZE, px)
    print()
    print("  已写出:", os.path.relpath(TARGET, PROJECT_ROOT))

    w, h = make_preview(px, OUT_SIZE, PREVIEW)
    print("  已写出预览:", os.path.relpath(PREVIEW, PROJECT_ROOT), "(%d×%d, 2×2 平铺)" % (w, h))


if __name__ == "__main__":
    main()
