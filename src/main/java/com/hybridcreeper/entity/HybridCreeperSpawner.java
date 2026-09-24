// v1.11.0 起，苦力怕幻翼改为「独立怪物生成」：标准 SpawnPlacements 注册
// （HybridCreeper#onRegisterSpawnPlacements）+ 生物群系修饰符 add_spawns
// （data/hybridcreeper/neoforge/biome_modifier/creeper_phantom_spawns.json）。
//
// 它不再通过 CustomSpawner 伴随原版幻翼生成，本类已废弃。
// 环境限制：沙箱无法删除文件，只能清空为存根 —— 下个大版本可直接删掉本文件。
package com.hybridcreeper.entity;
