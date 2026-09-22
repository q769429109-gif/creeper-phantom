package com.hybridcreeper.entity;

import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.config.HybridCreeperConfig;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;

/**
 * 把 {@link HybridCreeperSpawner} 挂进 {@code ServerLevel}。
 *
 * <h2>为什么需要这一层</h2>
 * <p>{@code ServerLevel#customSpawners} 是 private 字段，原版那份列表
 * （{@code PhantomSpawner} / {@code PatrolSpawner} / {@code CatSpawner} / {@code VillageSiege} /
 * {@code WanderingTraderSpawner}）是 {@code MinecraftServer#createLevels} 里的局部变量。
 * 常规做法得上访问转换器（AT）或 Mixin。</p>
 *
 * <p>但 NeoForge 已经开了口子：{@code ServerLevel} 构造末尾会调用
 * {@code EventHooks#getCustomSpawners(this, 原始列表)}，
 * 里面 post 了 {@link ModifyCustomSpawnersEvent}。订阅它就能往列表里加自己的生成器。
 * <b>零侵入、零反射、零 Mixin。</b></p>
 *
 * <h2>怎么保证"只加在原版幻翼会出现的维度"</h2>
 * <p>{@code MinecraftServer#createLevels} 里那份带 {@code PhantomSpawner} 的列表
 * <b>只传给主世界</b>，下界和末地拿到的是空列表。这个事件对每个
 * {@code ServerLevel} 都会触发，所以如果无脑添加，我们的生物就会在下界/末地也刷出来 ——
 * 那就跟幻翼不一致了。</p>
 *
 * <p>判据很直接：<b>看这个维度的列表里有没有 {@code PhantomSpawner}</b>。
 * 有 → 说明这个维度原版就会刷幻翼 → 我们也加；没有 → 跳过。
 * 这样"生成方式与幻翼一致"是自动成立的，将来原版改了规则我们也跟着走。</p>
 */
@EventBusSubscriber(modid = HybridCreeper.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class HybridCreeperSpawnHook {

    @SubscribeEvent
    public static void onModifyCustomSpawners(ModifyCustomSpawnersEvent event) {
        if (!HybridCreeperConfig.SPAWN_ENABLED.get()) {
            return;
        }

        boolean vanillaSpawnsPhantomsHere = event.getCustomSpawners().stream()
                .anyMatch(spawner -> spawner instanceof PhantomSpawner);

        if (!vanillaSpawnsPhantomsHere) {
            return;
        }

        event.addCustomSpawner(new HybridCreeperSpawner());
    }

    private HybridCreeperSpawnHook() {
    }
}
