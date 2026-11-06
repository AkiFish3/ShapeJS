package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;

import java.util.List;

/** 工作台（原版 CraftingTableBlock）+ 可选追加属性。 */
public class ScriptCraftingBlock extends CraftingTableBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) { PENDING_PROPS.set(props == null ? List.of() : props); }
    public static void clear() { PENDING_PROPS.remove(); }

    public ScriptCraftingBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        List<Property<?>> pending = PENDING_PROPS.get();
        if (pending != null) for (Property<?> p : pending) builder.add(p);
    }
}
