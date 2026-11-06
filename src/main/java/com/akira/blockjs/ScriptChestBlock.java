package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;

import java.util.List;

/** 箱子（原版 ChestBlock） + 可选追加属性。 */
public class ScriptChestBlock extends ChestBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
    }

    /**
     * ★这个 supplier 必须和 {@link ScriptChestBlockEntity#getType()} 是同一个★。
     * 它在 {@code ChestBlock.getBlockEntitySource} 里被拿去 {@code world.getBlockEntity(pos, type)}，
     * 类型对不上就返回 null ⇒ {@code createScreenHandlerFactory} 给 null ⇒ 右键箱子完全没反应
     * （零报错、零日志）。同理 {@code getInventory} 拿到 null ⇒ 掉落为 0、比较器恒 0。
     */
    public ScriptChestBlock(Settings settings) {
        super(settings, () -> BlockJS.CONTAINER_CHEST_TYPE);
    }

    /** 原版这类方块用 BER 渲染外观（模型不显示），探针需要看模型 ⇒ 强制走 MODEL */
    @Override
    public net.minecraft.block.BlockRenderType getRenderType(net.minecraft.block.BlockState state) {
        return net.minecraft.block.BlockRenderType.MODEL;
    }

    /** 用自定义 BE（无 BER）⇒ 只渲染模型，不叠加原版箱子 */
    @Override
    public ScriptChestBlockEntity createBlockEntity(net.minecraft.util.math.BlockPos pos,
                                                    net.minecraft.block.BlockState state) {
        return new ScriptChestBlockEntity(pos, state);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        List<Property<?>> pending = PENDING_PROPS.get();
        if (pending != null) {
            for (Property<?> p : pending) builder.add(p);
        }
    }
}
