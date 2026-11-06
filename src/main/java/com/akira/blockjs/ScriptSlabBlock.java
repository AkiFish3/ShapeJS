package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.List;
import java.util.Map;

/** 台阶（原版 SlabBlock） + 可选追加属性（shapejs 全接管用）。 */
public class ScriptSlabBlock extends SlabBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();
    private static final ThreadLocal<Map<Property<?>, Comparable<?>>> PENDING_DEFAULTS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PENDING_CULL = new ThreadLocal<>();
    /** 盒表 id（`cocricot:xxx`）：shift 面板要按模型取碰撞，不能吃原版半砖形状 */
    private static final ThreadLocal<String> PENDING_BOX = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void prepareDefaults(Map<Property<?>, Comparable<?>> d) {
        PENDING_DEFAULTS.set(d == null ? Map.of() : d);
    }

    public static void prepareCull(boolean v) { PENDING_CULL.set(v); }

    public static void prepareBox(String id) { PENDING_BOX.set(id); }

    public static void clear() {
        PENDING_PROPS.remove();
        PENDING_DEFAULTS.remove();
        PENDING_CULL.remove();
        PENDING_BOX.remove();
    }

    private final boolean sameCull;
    /** 盒表 id：有条目就按模型出形状（shift 面板 3px / double 整块…），没有就吃原版半砖形状 */
    private final String boxedId;

    /**
     * 带 axis 的台阶（cocricot 的 `wood_*_slab`）：按点击的面决定轴向。
     * 原版 SlabBlock 只处理 type，axis 永远停在默认值 ⇒ 三个轴向状态只能放出一个。
     */
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState st = super.getPlacementState(ctx);
        if (st != null && st.contains(Properties.AXIS)) {
            Direction side = ctx.getSide();
            st = st.with(Properties.AXIS, side.getAxis());
        }
        return st;
    }

    public ScriptSlabBlock(Settings settings) {
        super(settings);
        this.sameCull = Boolean.TRUE.equals(PENDING_CULL.get());
        this.boxedId = PENDING_BOX.get();
        // ★手挂属性必须显式定默认态★：BooleanProperty 的首值是 TRUE，
        // 不设的话放置出来就是 shift=true（偏移版），与 blockstate 的"首选"不一致。
        Map<Property<?>, Comparable<?>> d = PENDING_DEFAULTS.get();
        if (d != null && !d.isEmpty()) {
            BlockState st = getDefaultState();
            for (Property<?> pr : PENDING_PROPS.get() == null ? List.<Property<?>>of() : PENDING_PROPS.get()) {
                Comparable<?> v = d.get(pr);
                if (v != null) st = withUnchecked(st, pr, v);
            }
            setDefaultState(st);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withUnchecked(BlockState st, Property p, Comparable v) {
        return st.with(p, v);
    }

    /**
     * 形状（轮廓/碰撞）优先查盒表。
     *
     * <p>★shift 面板必须走这里★：`shift=true` 的模型是 `[0,0,0]-[16,16,3]` 的 3px 薄板，
     * 而原版 SlabBlock 只按 `type` 给形状（bottom 0..8 / top 8..16 / double 满块），
     * 于是 shift 状态下"看着是薄板、碰撞还是半块"（Aki 2026-10-26）。
     * 盒表按 (id, 状态) 逐条给出真值：shift / double / 上半 / 下半 都对，
     * 也顺带让 {@link FaceCull} 能按真实形状剔面。
     */
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape s = BoxTable.query(boxedId, state);
        return s != null ? s : super.getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        VoxelShape s = BoxTable.query(boxedId, state);
        return s != null ? s : super.getCollisionShape(state, world, pos, context);
    }

    /**
     * 相邻面剔除（玻璃台阶的"材质连接"）。
     *
     * <p>★旧实现是"占位区间相等才剔"★：bottom 0..8 与 double 0..16 区间不等 ⇒ 一个面都不剔，
     * 于是 bottom 挨着 double（一低一高并排）时两个贴合面都画，透明材质上就是一条缝
     * （Aki 2026-10-26 报的"bottom 和 double 上下放置时侧面不会有材质连接"）。
     *
     * <p>现在改成**按几何覆盖、单向判定**：自己这一面被邻居完整盖住才剔。
     * bottom 的侧面被 double 整面盖住 ⇒ 剔；double 侧面只被盖住下半 ⇒ 保留（剔了会露洞）。
     * 判定在 {@link FaceCull}，语义与 {@code Block.shouldDrawSide} 里的
     * {@code VoxelShapes.isSideCovered} 一致（原版只对不透明邻居做，这里补上）。
     *
     * <p>参数次序（1.20.1 字节码实证）：第一个是**自己**，第二个是**邻居**。
     */
    @Override
    public boolean isSideInvisible(BlockState state, BlockState neighbor, Direction direction) {
        if (FaceCull.related(state, neighbor) && FaceCull.covered(state, neighbor, direction)) {
            return true;
        }
        if (sameCull && state.isOf(this) && neighbor.isOf(this)) return true;
        return super.isSideInvisible(state, neighbor, direction);
    }

    private static Property<?> propNamed(BlockState s, String name) {
        for (Property<?> p : s.getProperties()) {
            if (p.getName().equals(name)) return p;
        }
        return null;
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
