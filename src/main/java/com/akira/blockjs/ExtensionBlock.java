package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 多方块结构的隐形占位方块（{@code blockjs:extension}）——不注册物品、不渲染、无掉落。
 *
 * <p>dx/dz = 世界系下「本格 - 锚点」偏移（填的时候就按锚点 facing 旋转好了），
 * 所以找锚点 = {@code pos.add(-dx, 0, -dz)}，不需要再知道朝向。
 *
 * <p>碰撞/高亮/选取全部委托给锚点：拿锚点（可能跨格的）形状平移到本格坐标系。
 * 锚点没了（结构已拆）→ 空形状 + 级联静默结束。
 */
public class ExtensionBlock extends Block {

	/**
	 * 偏移属性。⚠ vanilla IntProperty 不允许负 min ⇒ 存「偏移+MAX_RANGE」（0..4），
	 * 读出时减回来。真实偏移 dx∈[-2,2]。
	 */
	public static final IntProperty DX = IntProperty.of("dx", 0, Multiblocks.MAX_RANGE * 2);
	public static final IntProperty DZ = IntProperty.of("dz", 0, Multiblocks.MAX_RANGE * 2);

	public ExtensionBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState()
				.with(DX, Multiblocks.MAX_RANGE).with(DZ, Multiblocks.MAX_RANGE));   // = 偏移(0,0)
	}

	private static int off(BlockState state, IntProperty prop) {
		return state.get(prop) - Multiblocks.MAX_RANGE;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(DX, DZ);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;   // 不烘焙不渲染，连模型都不需要看
	}

	private BlockPos anchorPos(BlockState state, BlockPos pos) {
		return pos.add(-off(state, DX), 0, -off(state, DZ));
	}

	/** @return 锚点状态；锚点不存在或不是多方块 → null */
	@Nullable
	private BlockState anchorState(BlockView world, BlockState state, BlockPos pos) {
		BlockState a = world.getBlockState(anchorPos(state, pos));
		return Multiblocks.footprintOf(a.getBlock()) != null ? a : null;
	}

	private VoxelShape delegate(BlockView world, BlockState state, BlockPos pos, ShapeContext context, boolean outline) {
		BlockState a = anchorState(world, state, pos);
		if (a == null) return VoxelShapes.empty();
		BlockPos ap = anchorPos(state, pos);
		VoxelShape s = outline
				? a.getOutlineShape(world, ap, context)
				: a.getCollisionShape(world, ap, context);
		// 锚点局部系 → 本格局部系（锚点形状允许跨格，平移后本格那份自然正确）
		return s.offset(-(double) off(state, DX), 0, -(double) off(state, DZ));
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return delegate(world, state, pos, context, false);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return delegate(world, state, pos, context, true);
	}

	@Override
	public VoxelShape getRaycastShape(BlockState state, BlockView world, BlockPos pos) {
		BlockState a = anchorState(world, state, pos);
		if (a == null) return VoxelShapes.empty();
		return a.getRaycastShape(world, anchorPos(state, pos))
				.offset(-(double) off(state, DX), 0, -(double) off(state, DZ));
	}

	/**
	 * 级联拆除：本格被拆（手/爆炸/活塞）且锚点还在 → 毁掉整个结构（掉落物在锚点）。
	 * 锚点先拆的情况：锚点侧清理时锚点位置已是空气 ⇒ 这里 footprintOf 为 null ⇒ 静默消失。
	 */
	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		super.onStateReplaced(state, world, pos, newState, moved);
		if (state.isOf(newState.getBlock()) || world.isClient()) return;
		BlockPos anchor = anchorPos(state, pos);
		if (Multiblocks.footprintOf(world.getBlockState(anchor).getBlock()) != null) {
			world.breakBlock(anchor, true);
		}
	}
}
