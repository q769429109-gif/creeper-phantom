#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
给原版海豚的 .bbmodel 烘焙一段 Blockbench 游动动画（swim）。

为什么需要"烘焙"：
  Java 版海豚没有关键帧动画文件 —— 它的游动完全写在
  `net.minecraft.client.model.DolphinModel#setupAnim` 里，是一段由
  `ageInTicks` 驱动的连续余弦振荡：

      if (getDeltaMovement().horizontalDistanceSqr() > 1e-7) {
          body.xRot  += -0.05 - 0.05 * cos(t * 0.3);   // 身体轻微俯仰抖动
          tail.xRot   = -0.1  * cos(t * 0.3);          // 尾巴摆
          tailFin.xRot= -0.2  * cos(t * 0.3);          // 尾鳍摆
      }

  本脚本把这段连续函数采样成关键帧，写进 bbmodel 的 `animations` 字段，
  这样在 Blockbench 里打开就能直接播放、并复制到别的模型上。

坐标/单位约定：
  * bbmodel 里骨头旋转存的是「度」（outliner 的 rotation 就是 [-60,0,0] 这种）；
    Java 侧换算关系是 java.xRot = -rad(bb.rx)，所以 bb.rx = -deg(java.xRot)。
  * bbmodel 的动画 time / length 单位是「秒」，snapping=20（即 1/20 秒 = 1 tick）。
  * cos(t*0.3) 的周期 = 2π/0.3 ≈ 20.9439 tick ≈ 1.0472 秒；一个周期正好首尾相等，
    所以循环无缝。
"""

import json
import math
import os
import uuid as uuidlib

HERE = os.path.dirname(os.path.abspath(__file__))
REF = os.path.dirname(HERE)
SRC = os.path.join(REF, "bbmodel", "dolphin.bbmodel")
DST = os.path.join(REF, "bbmodel", "dolphin_animated.bbmodel")

FPS = 20.0
PERIOD_TICKS = 2.0 * math.pi / 0.3          # ≈ 20.9439
LENGTH_SEC = PERIOD_TICKS / FPS             # ≈ 1.0472
SAMPLES = 20                                # 一个周期采样 20 段 → 21 个关键帧


def bone_uuids(m):
    """从 outliner 收集 name -> uuid。"""
    out = {}

    def walk(nodes):
        for n in nodes:
            if isinstance(n, dict):
                out[n.get("name")] = n.get("uuid")
                walk(n.get("children", []))
    walk(m.get("outliner", []))
    return out


def bb_rx_deg(java_xrot):
    """Java xRot(rad) -> Blockbench rx(deg)。java.xRot = -rad(bb.rx)。"""
    return -math.degrees(java_xrot)


def anim_values(t_ticks):
    c = math.cos(t_ticks * 0.3)
    return {
        "body": bb_rx_deg(-0.05 - 0.05 * c),
        "tail": bb_rx_deg(-0.1 * c),
        "tail_fin": bb_rx_deg(-0.2 * c),
    }


def fmt(v):
    s = "%.4f" % v
    return s


def build_animation(uuids):
    animators = {}
    bones = ["body", "tail", "tail_fin"]
    for bone in bones:
        kfs = []
        for k in range(SAMPLES + 1):
            t_ticks = PERIOD_TICKS * k / SAMPLES
            t_sec = round(t_ticks / FPS, 6)
            rx = anim_values(t_ticks)[bone]
            kfs.append({
                "channel": "rotation",
                "time": t_sec,
                "color": -1,
                "interpolation": "linear",
                "data_points": [{"x": fmt(rx), "y": "0", "z": "0"}],
                "uuid": str(uuidlib.uuid4()),
            })
        animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
    return {
        "name": "swim",
        "loop": "loop",
        "override": False,
        "length": round(LENGTH_SEC, 6),
        "snapping": 20,
        "selected": False,
        "anim_time_update": "",
        "blend_weight": "",
        "start_delay": "",
        "loop_delay": "",
        "animators": animators,
    }


def main():
    with open(SRC, encoding="utf-8") as f:
        m = json.load(f)
    uuids = bone_uuids(m)
    missing = [b for b in ("body", "tail", "tail_fin") if b not in uuids]
    if missing:
        raise SystemExit("模型里找不到骨头: %s" % missing)
    m["animations"] = [build_animation(uuids)]
    print("骨头 uuid:", {k: uuids[k] for k in ("body", "tail", "tail_fin")})
    print("动画 length = %.4f s (%.4f tick)，%d 关键帧/骨头"
          % (LENGTH_SEC, PERIOD_TICKS, SAMPLES + 1))
    with open(DST, "w", encoding="utf-8") as f:
        json.dump(m, f, ensure_ascii=False, indent=1)
    print("已写出:", DST)


if __name__ == "__main__":
    main()
