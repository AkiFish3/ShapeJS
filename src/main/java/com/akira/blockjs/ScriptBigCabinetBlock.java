package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 大柜子：满块外观 + 与箱子同款的 27/54 格容器，可选追加属性。
 *
 * <p>单格 27、双联 54 全部走原版 {@code ChestBlock} 的双联逻辑：
 * supplier 必须是 {@link BlockJS#CONTAINER_BIGCABINET_TYPE}（与 BE 的 getType 同源），
 * 否则 {@code getBlockEntitySource} 里 {@code world.getBlockEntity(pos, type)} 返回 null
 * ⇒ 右键没反应、掉落为空、比较器恒 0（零日志）。
 *
 * <p>不要自己覆写 {@code createScreenHandlerFactory} / {@code createScreenHandler}：
 * 单格 9×3 与双联 9×6 是原版两套路径，手写一套就会把另一种格子数弄错。
 */
public class ScriptBigCabinetBlock extends ChestBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
    }

    public ScriptBigCabinetBlock(Settings settings) {
        super(settings, () -> BlockJS.CONTAINER_BIGCABINET_TYPE);
    }

    /** 让自定义模型可见（原版箱子默认走 BER） */
    @Override
    public net.minecraft.block.BlockRenderType getRenderType(net.minecraft.block.BlockState state) {
        return net.minecraft.block.BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptBigCabinetBlockEntity(pos, state);
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
