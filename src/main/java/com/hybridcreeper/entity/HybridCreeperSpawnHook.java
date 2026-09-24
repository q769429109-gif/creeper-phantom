// v1.11.0 起，苦力怕幻翼改为「独立怪物生成」，不再需要往 ServerLevel 注入
// CustomSpawner（原先通过 ModifyCustomSpawnersEvent 追加 HybridCreeperSpawner，
// 现在 SpawnPlacements 注册 + 生物群系修饰符已完全接管）。
//
// 本类已废弃。环境限制：沙箱无法删除文件，只能清空为存根 ——
// 下个大版本可直接删掉本文件。
package com.hybridcreeper.entity;
