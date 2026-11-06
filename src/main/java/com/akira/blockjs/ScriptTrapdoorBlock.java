package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;

import java.util.List;

/** 原版活板门 + 可选追加属性。 */
public class ScriptTrapdoorBlock extends TrapdoorBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
    }

    public ScriptTrapdoorBlock(Settings settings, BlockSetType blockSetType) {
        super(settings, blockSetType);
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
