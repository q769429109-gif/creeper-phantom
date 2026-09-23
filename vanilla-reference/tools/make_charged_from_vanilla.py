#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
用原版苦力怕充能贴图 creeper_armor.png (64x32) 直接拼出一张尺寸正确的能量贴图。

做法：把原图在垂直方向上下拼合 —— 上半部是原图，下半部是原图副本，
得到 64x64 正方形（power-of-2、可被 GPU 正常采样，也方便 energySwirl 滚动时整图对齐）。

不做任何像素重绘、不新算法生成，完全是 Mojang 原图，只是重新拼尺寸。
颜色、alpha 二值硬边、低像素方块感都原汁原味保留。

输出：
  src/main/resources/assets/hybridcreeper/textures/entity/charged_energy.png  (64x64)
  vanilla-reference/textures/preview_charged_energy.png                      (2x2 平铺预览, 128x128)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_bbmodel as B

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "vanilla-reference", "textures", "creeper_armor.png")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hybridcreeper",
                  "textures", "entity", "charged_energy.png")
PREVIEW = os.path.join(ROOT, "vanilla-reference", "textures", "preview_charged_energy.png")


def main():
    sw, sh, spx = B.load_png(SRC)
    assert (sw, sh) == (64, 32), "源贴图尺寸不是 64x32：%dx%d" % (sw, sh)

    # 目标 64x64：上半部原图，下半部原图副本（上下拼合）
    W = sw            # 64
    H = sh * 2        # 64
    out = bytearray(W * H * 4)
    for y in range(H):
        src_y = y % sh           # 0..31 重复两次
        for x in range(W):
            si = (src_y * sw + x) * 4
            di = (y * W + x) * 4
            out[di] = spx[si]
            out[di + 1] = spx[si + 1]
            out[di + 2] = spx[si + 2]
            out[di + 3] = spx[si + 3]

    B.save_png(OUT, W, H, out)
    print("写出 %s  (%dx%d)" % (OUT, W, H))

    # 2x2 平铺预览
    T = 2
    PW, PH = W * T, H * T
    prev = bytearray(PW * PH * 4)
    for py in range(PH):
        for px in range(PW):
            si = ((py % H) * W + (px % W)) * 4
            di = (py * PW + px) * 4
            prev[di] = out[si]
            prev[di + 1] = out[si + 1]
            prev[di + 2] = out[si + 2]
            prev[di + 3] = out[si + 3]
    B.save_png(PREVIEW, PW, PH, prev)
    print("预览 %s  (%dx%d)" % (PREVIEW, PW, PH))

    # 简单统计
    nz = sum(1 for i in range(0, len(out), 4) if out[i + 3] > 0)
    print("非透明像素: %d / %d = %.1f%%" % (nz, W * H, 100.0 * nz / (W * H)))


if __name__ == "__main__":
    main()
