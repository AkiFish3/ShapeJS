package com.akira.blockjs;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.minecraft.util.Identifier;

/** 客户端：给演示流体挂贴图，否则流体会「看不见」。 */
public class BlockJSClient implements ClientModInitializer {

    private static final Identifier STILL = new Identifier("minecraft:block/water_still");
    private static final Identifier FLOW = new Identifier("minecraft:block/water_flow");

    @Override
    public void onInitializeClient() {
        // 矩阵 _t 版本：默认实体层会画成不透明；CLIENT_STARTED 时再刷一遍（此时所有方块必然已注册）
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            int n = 0;
            for (net.minecraft.block.Block b : net.minecraft.registry.Registries.BLOCK) {
                net.minecraft.util.Identifier id = net.minecraft.registry.Registries.BLOCK.getId(b);
                if ("coc".equals(id.getNamespace()) && id.getPath().startsWith("probe_") && id.getPath().endsWith("_t")) {
                    net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE
                            .putBlock(b, net.minecraft.client.render.RenderLayer.getTranslucent());
                    n++;
                }
            }
            com.akira.blockjs.DynamicShapes.LOG.info("[BlockJS] 透明层已应用：{} 个 probe_*_t", n);

            // ★2026-11-04 C99★ 按 meta.rnd 逐个补挂渲染层。
            //   KubeJS 自己会在 client 初始化时按 BlockBuilder.renderType 挂一次，但
            //   `e.create(id, type)` 扁平路径 + 自定义 builder 的组合实测漏挂
            //   （cocricot:fence_iron_chain_black 贴图 alpha=0 区域画成纯黑 ⇒ 走的是 solid 层）。
            //   这里直接按 id 查 meta 再挂一遍，幂等：已经对的覆盖成同一个值。
            int nc = 0, nt = 0;
            for (net.minecraft.block.Block b : net.minecraft.registry.Registries.BLOCK) {
                net.minecraft.util.Identifier rid = net.minecraft.registry.Registries.BLOCK.getId(b);
                com.akira.blockjs.BlockMeta.Meta m = com.akira.blockjs.BlockMeta.of(rid.toString());
                if (m == null || m.render() == null) continue;
                if ("translucent".equals(m.render())) {
                    net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE
                            .putBlock(b, net.minecraft.client.render.RenderLayer.getTranslucent());
                    nt++;
                } else {
                    net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE
                            .putBlock(b, net.minecraft.client.render.RenderLayer.getCutout());
                    nc++;
                }
            }
            com.akira.blockjs.DynamicShapes.LOG.info("[BlockJS] 渲染层按 meta 补挂：cutout {} / translucent {}", nc, nt);
        });
        FluidRenderHandlerRegistry.INSTANCE.register(BlockJS.FLUID_FAST, BlockJS.FLUID_FAST,
                new SimpleFluidRenderHandler(STILL, FLOW, 0x4FA8FF));
        FluidRenderHandlerRegistry.INSTANCE.register(BlockJS.FLUID_THICK, BlockJS.FLUID_THICK,
                new SimpleFluidRenderHandler(STILL, FLOW, 0x8E6BD0));
    }
}
