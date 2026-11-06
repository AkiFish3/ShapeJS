package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;

import java.util.List;

/** 栅栏（原版 FenceBlock） + 可选追加属性（shapejs 全接管用）。 */
public class ScriptFenceBlock extends FenceBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
    }

    public ScriptFenceBlock(Settings settings) {
        super(settings);
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
