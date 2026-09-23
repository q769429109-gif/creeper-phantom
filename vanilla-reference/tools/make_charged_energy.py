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
  也就是说原版那身能量是「硬边方块图案」，不是一层均匀的电浆雾。
  我们要求的是这种「低像素方块」质感（2026-09-23 用户指定）。

======================================================================
为什么是 128×128 且全幅无接缝 —— 方块版的做法
======================================================================
1. 把 128×128 切成 GRID×GRID 个大方块（默认 16×16，每格 CELL=8 像素）。
   先在 GRID×GRID 的「低分辨率」网格上算可平铺值噪声，决定每个方块是
   暗蓝 / 中蓝 / 亮青白 / 透明，再用【最近邻】上采样回 128。
   硬边马赛克就是这么来的 —— 没有任何抗锯齿、没有任何模糊。
2. 网格用「索引取模」的值噪声，周期天然闭合，上采样也不会破缝。
   滚动的是纹理坐标（TextureMat 平移），所以只要贴图自身无缝，滚动时
   就不会看到接缝在模型上爬。
3. 用全幅无缝图而不是像原版那样按模型 UV 画异形图：
   · 模型只采样到自己 UV 覆盖的像素，全幅效果与异形图完全一样；
   · 一张图能同时服务两只 UV 排布完全不同的生物；
   · 纯算法生成，不含任何 Mojang 素材衍生，没有版权负担。
4. 用 128 而不是 64：方块仍是 8px 的硬边，但整张贴图信息更足，
   在 2 格高的模型上铺出来不至于糊。代价只有 64KB。

运行：python make_charged_energy.py
"""

import math
import os
import random
import struct
import zlib

# ---------------------------------------------------------------- 配置

OUT_SIZE = 128                 # 输出边长（正方形，2 的幂）
SEED = 20260923                # 固定种子，保证可复现

# 输出路径（相对本脚本：tools/ -> ../../src/main/resources/...）
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
TARGET = os.path.join(
    PROJECT_ROOT, "src", "main", "resources",
    "assets", "hybridcreeper", "textures", "entity", "charged_energy.png")
# 预览放进被 .gitignore 兜住的 vanilla-reference/textures/，避免误提交
PREVIEW = os.path.join(
    PROJECT_ROOT, "vanilla-reference", "textures", "preview_charged_energy.png")

# 【方块感的核心参数】
# 128 / 8 = 16 -> 整张贴图被切成 16×16 个 8px 大方块。
# 这个比例与原版 creeper_armor 在 64 宽贴图上的 2~4px 特征相当：
# 既够"低像素方块"，滚动时又不会糊成一片噪点。
CELL = 8
GRID = OUT_SIZE // CELL        # = 16

# 噪声晶格数（必须整除 GRID）。NOISE_CELLS=4 -> 方块聚成约 4×4 的"光斑群"，
# 而不是国际象棋盘那种均匀散点。第二阶翻倍叠一层细碎起伏，让边界不那么死板。
NOISE_CELLS = 4

# 目标可见覆盖率。与原版 creeper_armor 的 31.4% 同量级，略放宽到 0.40
# 让蓝色更明显一点（我们的方块比原版更稀疏也更亮）。
TARGET_COVERAGE = 0.40

# 三档蓝色（贴图侧，着色器会再 ×0.5）。
# 从暗到亮，亮档带青白高光 —— 复刻原版那种"有高光的层次"，不是一片均匀蓝雾。
COLOR_TIERS = [
    (34, 96, 196),     # 暗蓝（多数可见方块）
    (96, 176, 255),    # 中蓝
    (200, 236, 255),   # 亮青白（峰顶高光）
]

# 各档的判定阈值（相对"可见区间内"的归一化强度 t∈[0,1]）。
# t<0.5 暗蓝；0.5~0.85 中蓝；>0.85 亮青白。
TIER_MID = 0.50
TIER_HI = 0.85


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

def build():
    rng = random.Random(SEED)

    # ① 在 GRID×GRID（16×16）的低分辨率网格上做可平铺噪声。
    #    两阶：NOISE_CELLS 出大光斑群，翻倍那阶补一点细碎起伏。
    field = fbm(GRID, [(NOISE_CELLS, 1.00), (NOISE_CELLS * 2, 0.5)], rng)

    # ② 阈值：按目标覆盖率取分位数 —— 无论权重怎么调，覆盖率都稳。
    ordered = sorted(field)
    n = len(field)
    thr = ordered[min(n - 1, int(n * (1.0 - TARGET_COVERAGE)))]
    vmax = ordered[-1]
    span = max(vmax - thr, 1e-6)

    # ③ 逐方块定颜色。可见方块按强度分三档蓝，不可见就全透明（二值 alpha）。
    cell_rgba = []
    for v in field:
        if v <= thr:
            cell_rgba.append((0, 0, 0, 0))
            continue
        t = (v - thr) / span
        if t < TIER_MID:
            c = COLOR_TIERS[0]
        elif t < TIER_HI:
            c = COLOR_TIERS[1]
        else:
            c = COLOR_TIERS[2]
        cell_rgba.append((c[0], c[1], c[2], 255))

    # ④ 最近邻上采样：GRID×GRID -> OUT_SIZE×OUT_SIZE，硬边马赛克由此而来。
    px = bytearray(OUT_SIZE * OUT_SIZE * 4)
    for gy in range(GRID):
        for gx in range(GRID):
            r, g, b, a = cell_rgba[gy * GRID + gx]
            by = gy * CELL
            bx = gx * CELL
            for dy in range(CELL):
                row = (by + dy) * OUT_SIZE
                for dx in range(CELL):
                    j = (row + bx + dx) * 4
                    px[j] = r
                    px[j + 1] = g
                    px[j + 2] = b
                    px[j + 3] = a

    visible = sum(1 for (_, _, _, a) in cell_rgba if a > 0) * CELL * CELL
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
    """把贴图 2×2 平铺再放大，用来看接缝和整体观感。"""
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
    print("生成充能能量贴图（低像素方块版）%dx%d  CELL=%d GRID=%d 种子 %d"
          % (OUT_SIZE, OUT_SIZE, CELL, GRID, SEED))
    print()

    px, visible = build()
    total = OUT_SIZE * OUT_SIZE
    cov = visible / total
    print("  非透明像素: %d / %d = %.1f%%（原版 creeper_armor 参考 31.4%%，目标 %.0f%%）"
          % (visible, total, cov * 100, TARGET_COVERAGE * 100))

    print()
    print("无缝校验:")
    check_seam(px, OUT_SIZE)

    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    save_png(TARGET, OUT_SIZE, OUT_SIZE, px)
    print()
    print("  已写出:", os.path.relpath(TARGET, PROJECT_ROOT))

    w, h = make_preview(px, OUT_SIZE, PREVIEW)
    print("  已写出预览:", os.path.relpath(PREVIEW, PROJECT_ROOT), "(%dx%d, 2x2 平铺)" % (w, h))


if __name__ == "__main__":
    main()
