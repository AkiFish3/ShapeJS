package com.akira.blockjs;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.item.Item;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * 可配置流体：流动速度（tickRate）与流动范围（dropOff）都由构造参数决定。
 *
 * <p>dropOff = 每格衰减的液面高度（越大流得越近）；vanilla 水是 1（约 7 格）。
 * tickRate = 每几 tick 推进一次（越大越慢）。
 */
public class ScriptFluid extends FlowableFluid {

    private net.minecraft.block.Block block;
    private final int tickRate;
    private final int dropOff;

    protected ScriptFluid(int tickRate, int dropOff) {
        this.tickRate = Math.max(1, tickRate);
        this.dropOff = Math.max(1, dropOff);
    }

    /** 由注册代码回填对应的流体方块 */
    public void bindBlock(net.minecraft.block.Block b) {
        this.block = b;
    }

    /**
     * ★必须自己加 LEVEL★ FlowableFluid 的 appendProperties 只加 FALLING，
     * 原版是靠 WaterFluid/LavaFluid 各自的子类补 LEVEL 的。
     * 少了它：FluidBlock 构造 → getStill(false) → with(LEVEL, …) → IllegalArgumentException。
     */
    @Override
    protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) {
        super.appendProperties(builder);
        builder.add(LEVEL);
    }

    /** 静水（液面 8）——默认实现只设 FALLING，不动 LEVEL，所以这里必须自己写 */
    @Override
    public FluidState getStill(boolean falling) {
        return getDefaultState().with(FALLING, falling).with(LEVEL, 8);
    }

    @Override
    public FluidState getFlowing(int level, boolean falling) {
        return getDefaultState().with(FALLING, falling).with(LEVEL, MathHelper.clamp(level, 1, 8));
    }

    @Override
    public BlockState toBlockState(FluidState state) {
        return block == null ? net.minecraft.block.Blocks.WATER.getDefaultState()
                : block.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL, getBlockStateLevel(state));
    }

    @Override
    public Fluid getFlowing() {
        return this;
    }

    @Override
    public Fluid getStill() {
        return this;
    }

    @Override
    protected boolean isInfinite(World world) {
        return false;
    }

    @Override
    protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state) {
        // 默认行为：不额外破坏
    }

    @Override
    protected int getFlowSpeed(WorldView world) {
        return 4;
    }

    @Override
    protected int getLevelDecreasePerBlock(WorldView world) {
        return dropOff;
    }

    @Override
    public int getTickRate(WorldView world) {
        return tickRate;
    }

    @Override
    protected float getBlastResistance() {
        return 100.0F;
    }

    @Override
    public Item getBucketItem() {
        return net.minecraft.item.Items.WATER_BUCKET;
    }

    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockView world, BlockPos pos, Fluid fluid, Direction direction) {
        return direction == Direction.DOWN && !fluid.matchesType(this);
    }

    @Override
    public boolean isStill(FluidState state) {
        return !state.get(FALLING) && state.get(LEVEL) == 8;
    }

    @Override
    public int getLevel(FluidState state) {
        return state.get(LEVEL);
    }
}
