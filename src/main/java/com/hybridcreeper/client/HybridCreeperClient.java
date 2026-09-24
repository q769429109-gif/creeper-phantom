package com.hybridcreeper.client;

import com.hybridcreeper.HybridCreeper;
import com.hybridcreeper.client.model.HybridCreeperModel;
import com.hybridcreeper.client.model.WaterCreeperModel;
import com.hybridcreeper.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 客户端注册：模型图层 + 新生物的渲染器。
 *
 * <p>标了 {@code value = Dist.CLIENT}，所以整个类只会在客户端加载 ——
 * 专用服务器上没有客户端类库，这个过滤是必须的。</p>
 *
 * <h2>和上一版的区别</h2>
 * <p>之前是 {@code registerEntityRenderer(EntityType.PHANTOM, ...)}，靠
 * "原版在静态块里先注册、我们后 put 覆盖"来顶掉原版幻翼的渲染器。
 * 现在苦力怕幻翼是<b>独立实体</b>，注册的是自己的 {@code EntityType}，
 * 原版 {@code PhantomRenderer} 原封不动 —— 原版幻翼在游戏里还是老样子。</p>
 */
@EventBusSubscriber(modid = HybridCreeper.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HybridCreeperClient {

    /** 注册两只生物各自的模型图层。 */
    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HybridCreeperModel.LAYER, HybridCreeperModel::createBodyLayer);
        event.registerLayerDefinition(WaterCreeperModel.LAYER, WaterCreeperModel::createBodyLayer);
    }

    /** 给两只生物挂上各自的渲染器。 */
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CREEPER_PHANTOM.get(), HybridCreeperRenderer::new);
        event.registerEntityRenderer(ModEntities.WATER_CREEPER.get(), WaterCreeperRenderer::new);
    }

    private HybridCreeperClient() {
    }
}
