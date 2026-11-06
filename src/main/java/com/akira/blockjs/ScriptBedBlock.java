package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;

import java.util.List;

/**
 * 床（原版 BedBlock） + 可选追加属性。
 *
 * <p>只保留「颜色」这份数据，BE 换成不带 BER 的自定义类型：
 * 原版 {@code BlockEntityType.BED} 注册了 BedBlockEntityRenderer，
 * 与本类的 MODEL 渲染叠在一起就是两张床。
 */
public class ScriptBedBlock extends BedBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    private final DyeColor color;

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
    }

    public ScriptBedBlock(DyeColor color, Settings settings) {
        super(color, settings);
        this.color = color;
    }

    /** 原版这类方块用 BER 渲染外观（模型不显示），探针需要看模型 ⇒ 强制走 MODEL */
    @Override
    public net.minecraft.block.BlockRenderType getRenderType(net.minecraft.block.BlockState state) {
        return net.minecraft.block.BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptBedBlockEntity(pos, state, color);
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
