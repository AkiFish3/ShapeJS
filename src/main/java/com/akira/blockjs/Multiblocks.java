package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * BlockJS · 多方块足迹（footprint）：让一个方块的碰撞/破坏范围覆盖多格。
 *
 * <h2>模型</h2>
 * 例：{@code b.dynFootprint(-1, -1, 1, 1)} = 以放置格为锚点的 3×3×1（xz 偏移闭区间，含 0,0）。
 * <ul>
 *   <li>放置（onPlaced）：锚点周围每格填一个隐形的 {@code blockjs:extension[dx,dz]}
 *       （dx/dz = 世界系下「格 - 锚」偏移，随锚点 facing 旋转）。任何一格不可替换
 *       → 已填的回滚 + 锚点弹成掉落物（官方多方块的标准行为）。</li>
 *   <li>碰撞/高亮/选取：extension  delegates 到锚点形状并平移到本格坐标系——
 *       所以锚点的 dynShape 盒可以直接跨格写（像素坐标允许 <0 或 >16）。</li>
 *   <li>破坏任意一格（手拆/爆炸/活塞）：extension.onStateReplaced 找到锚点
 *       → destroyBlock(锚点, 掉掉落物) → 锚点侧清理把剩余 extension 全部静默移除。</li>
 *   <li>直接拆锚点：AbstractBlockMixin.onStateReplaced(TAIL) 把所有 extension 清掉。</li>
 * </ul>
 *
 * <h2>为什么不用「单个方块形状跨格」</h2>
 * 1.20.1 的实体碰撞只查询实体 AABB 覆盖到的【格子】的方块形状；形状跨格但格子没实体经过时
 * 永远不会被查询 ⇒ 跨格碰撞不可靠。官方床/门也是用真方块占格。extension 方块方案是唯一稳的。
 */
public final class Multiblocks {

	private Multiblocks() {
	}

	/** 足迹：{x1, z1, x2, z2}（闭区间、north 朝向局部系） */
	private static final Map<Object, int[]> BY_BUILDER = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Block, int[]> BY_BLOCK = new IdentityHashMap<>();
	private static final Set<Block> RESOLVED_EMPTY = Collections.newSetFromMap(new IdentityHashMap<>());

	public static final int MAX_RANGE = 2;   // extension 属性 dx/dz ∈ [-2,2]（5×5 足迹上限）

	// ---------------------------------------------------------------- 脚本侧写入
	public static void addFootprint(Object builder, int x1, int z1, int x2, int z2) {
		if (builder == null) return;
		int lo = -MAX_RANGE, hi = MAX_RANGE;
		int ax1 = clamp(Math.min(x1, x2), lo, hi), ax2 = clamp(Math.max(x1, x2), lo, hi);
		int az1 = clamp(Math.min(z1, z2), lo, hi), az2 = clamp(Math.max(z1, z2), lo, hi);
		// 必须含锚点 (0,0)，不含就扩进去（防脚本写错）
		ax1 = Math.min(ax1, 0); ax2 = Math.max(ax2, 0);
		az1 = Math.min(az1, 0); az2 = Math.max(az2, 0);
		BY_BUILDER.put(builder, new int[]{ax1, az1, ax2, az2});
	}

	public static void clearFootprint(Object builder) {
		if (builder != null) BY_BUILDER.remove(builder);
	}

	public static void invalidateCache() {
		synchronized (Multiblocks.class) {
			BY_BLOCK.clear();
			RESOLVED_EMPTY.clear();
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	// ---------------------------------------------------------------- 运行时查询
	/** @return 该方块的足迹 {x1,z1,x2,z2}；null = 不是多方块 */
	@Nullable
	public static int[] footprintOf(Block block) {
		int[] cached = BY_BLOCK.get(block);
		if (cached != null) return cached;
		if (RESOLVED_EMPTY.contains(block)) return null;
		int[] fp = null;
		try {
			Object builder = DynamicShapes.builderOf(block);
			if (builder != null) fp = BY_BUILDER.get(builder);
		} catch (Throwable t) {
			DynamicShapes.LOG.warn("[BlockJS] 取 BlockBuilder 失败(footprint): {}", block, t);
		}
		if (fp == null) {
			RESOLVED_EMPTY.add(block);
			return null;
		}
		BY_BLOCK.put(block, fp);
		return fp;
	}

	/** 锚点朝向（无 facing 属性 = NORTH，即声明系不旋转） */
	public static Direction facingOf(BlockState state) {
		if (state.contains(Properties.HORIZONTAL_FACING)) {
			return state.get(Properties.HORIZONTAL_FACING);
		}
		return Direction.NORTH;
	}

	/** north 局部系 (x,z) → 世界系（俯视顺时针，与 shapejs_dyn 的旋转公式一致） */
	public static int[] rotate(int x, int z, Direction facing) {
		switch (facing) {
			case EAST:  return new int[]{-z, x};
			case SOUTH: return new int[]{-x, -z};
			case WEST:  return new int[]{z, -x};
			default:    return new int[]{x, z};
		}
	}

	// ---------------------------------------------------------------- 放置填充（BasicBlockJS.onPlaced TAIL 调）
	public static void tryFill(World world, BlockPos pos, BlockState state) {
		int[] fp = footprintOf(state.getBlock());
		if (fp == null) return;
		Direction f = facingOf(state);
		List<BlockPos> filled = new ArrayList<>(8);
		for (int dx = fp[0]; dx <= fp[2]; dx++) {
			for (int dz = fp[1]; dz <= fp[3]; dz++) {
				if (dx == 0 && dz == 0) continue;
				int[] r = rotate(dx, dz, f);
				BlockPos q = pos.add(r[0], 0, r[1]);
				if (!world.getBlockState(q).isReplaceable()) {
					// 格子被占 → 回滚已填的 + 锚点弹掉落物
					for (BlockPos p : filled) world.removeBlock(p, false);
					world.breakBlock(pos, true);
					return;
				}
				world.setBlockState(q, BlockJS.EXTENSION.getDefaultState()
						.with(ExtensionBlock.DX, r[0] + MAX_RANGE)   // 属性存「偏移+MAX_RANGE」
						.with(ExtensionBlock.DZ, r[1] + MAX_RANGE), 3);
				filled.add(q);
			}
		}
	}

	// ---------------------------------------------------------------- 锚点拆除清理（AbstractBlock.onStateReplaced TAIL 调）
	public static void clearExtensions(World world, BlockPos pos, BlockState oldState) {
		int[] fp = footprintOf(oldState.getBlock());
		if (fp == null) return;
		Direction f = facingOf(oldState);
		for (int dx = fp[0]; dx <= fp[2]; dx++) {
			for (int dz = fp[1]; dz <= fp[3]; dz++) {
				if (dx == 0 && dz == 0) continue;
				int[] r = rotate(dx, dz, f);
				BlockPos q = pos.add(r[0], 0, r[1]);
				if (world.getBlockState(q).getBlock() == BlockJS.EXTENSION) {
					// 此时锚点已是空气 ⇒ extension 的级联查找找不到锚点 ⇒ 静默消失（不递归）
					world.removeBlock(q, false);
				}
			}
		}
	}
}
