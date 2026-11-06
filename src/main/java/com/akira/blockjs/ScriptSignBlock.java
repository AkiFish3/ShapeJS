package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 告示牌（原版 SignBlock） + 可选追加属性。
 *
 * <p>上一版没有覆写 {@code createBlockEntity}，拿到的是 {@code BlockEntityType.SIGN} 的实体，
 * 于是日志每格一条 {@code Block entity minecraft:sign … invalid for ticking}
 * （原版 type 的 blocks 集合里没有我们的方块，tick 整块跳过）。
 * 现在换成自己的 type：tick 正常，且不再叠原版 BER（文字仍然可编辑）。
 */
public class ScriptSignBlock extends SignBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
    }

    public ScriptSignBlock(Settings settings, WoodType woodType) {
        super(settings, woodType);
    }

    /** 原版这类方块用 BER 渲染外观（模型不显示），探针需要看模型 ⇒ 强制走 MODEL */
    @Override
    public net.minecraft.block.BlockRenderType getRenderType(net.minecraft.block.BlockState state) {
        return net.minecraft.block.BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptSignBlockEntity(pos, state);
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
