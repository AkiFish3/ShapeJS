package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FlowableFluid;

/** 流体方块（直接复用原版 FluidBlock 的放置/流动逻辑）。 */
public class ScriptFluidBlock extends FluidBlock {

    public ScriptFluidBlock(FlowableFluid fluid, Settings settings) {
        super(fluid, settings);
    }
}
