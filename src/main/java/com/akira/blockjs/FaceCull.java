package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 按**几何覆盖**剔面（玻璃类"材质连接"的通用实现）。
 *
 * <p>原版只有在邻居 {@code isOpaque()} 时才会做覆盖判定（{@code Block.shouldDrawSide} 里那段
 * {@code VoxelShapes.isSideCovered}），玻璃/半透明方块一律跳过 ⇒ 两块相邻时两个面都画，
 * 透明材质上就是一条能看穿的缝。这里把这套判定补上，让非不透明方块也能按形状剔面。
 *
 * <p>语义（与 {@code Block.shouldDrawSide} 完全一致）：
 * <b>自己</b>朝 {@code direction} 的那一面，被<b>邻居</b>在 {@code direction.getOpposite()}
 * 侧的几何完整盖住 ⇒ 这一面不用画。
 *
 * <p>★单向★：只判定"自己这一面是否被盖住"。半砖 bottom(0..8) 挨着 double(0..16) 时，
 * bottom 的侧面被整面盖住 ⇒ 剔；double 的侧面只被盖住一半 ⇒ 不剔（剔了会露洞）。
 *
 * <p>参数方向：{@code AbstractBlockState.isSideInvisible(state, dir)} 内部是
 * {@code getBlock().isSideInvisible(this, state, dir)}，所以三参签名里
 * 第一个是**自己**、第二个是**邻居**（已用 1.20.1 字节码核对过，
 * 早期注释写的相反，是错的）。
 */
public final class FaceCull {

    private FaceCull() {}

    /**
     * 形状缓存（只按状态算，不吃世界）。
     *
     * <p>{@code isSideInvisible} 会被网格化对每个带 cullface 的面调用一次，而
     * {@code computedShape} 每次都会重建 VoxelShape（union/cuboid 都是新对象）⇒ 必须缓存。
     * BlockState 是"每个属性组合一个实例"，用 IdentityHashMap 既不触发 equals 又能命中。
     * 上限 8192 条，超了整体清空（简单、不会无限长）。
     */
    private static final Map<BlockState, VoxelShape> SHAPES = new IdentityHashMap<>();
    private static final int SHAPE_CAP = 8192;

    /** 方块 → 连接族（null 表示没配）。网格化时每对面都要问一次，缓存掉注册表查询 */
    private static final Map<Block, String> FAMILY = new IdentityHashMap<>();

    private static String familyOf(Block b) {
        if (FAMILY.containsKey(b)) return FAMILY.get(b);
        Identifier id = Registries.BLOCK.getId(b);
        String f = id == null ? null : BlockMeta.family(id.toString());
        if (FAMILY.size() > 16384) FAMILY.clear();
        FAMILY.put(b, f);
        return f;
    }

    /** 同一个方块，或 meta 里配了同一个连接族（`fam`：同款不同材质/颜色） */
    public static boolean related(BlockState mine, BlockState other) {
        if (mine.getBlock() == other.getBlock()) return true;
        String fa = familyOf(mine.getBlock());
        return fa != null && fa.equals(familyOf(other.getBlock()));
    }

    /** 自己朝 dir 的那一面是否被邻居完整覆盖（true ⇒ 可以不画） */
    public static boolean covered(BlockState mine, BlockState other, Direction dir) {
        VoxelShape a = shape(mine);
        VoxelShape b = shape(other);
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        try {
            return VoxelShapes.isSideCovered(a, b, dir);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 状态自身的轮廓形状（不吃世界，只需要状态），带缓存 */
    private static VoxelShape shape(BlockState s) {
        VoxelShape cached = SHAPES.get(s);
        if (cached != null) return cached;
        VoxelShape out;
        try {
            out = s.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        } catch (Throwable t) {
            return null;
        }
        if (SHAPES.size() > SHAPE_CAP) SHAPES.clear();
        SHAPES.put(s, out);
        return out;
    }
}
