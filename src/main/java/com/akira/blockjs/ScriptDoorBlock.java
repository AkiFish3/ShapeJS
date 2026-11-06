package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;

import java.util.List;
import java.util.Map;

/**
 * 原版门（{@link DoorBlock}）+ 可选追加属性。
 *
 * <p>门的 facing/half/hinge/open/powered 由原版类提供，双格放置与开合逻辑也全在父类里；
 * 这里只做「并进额外属性」这一件事，和 {@link ScriptStairBlock} 同一个套路（ThreadLocal 传参）。
 */
public class ScriptDoorBlock extends DoorBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();
    /** 盒表 id：日式推拉门（shoji/hikido/fusuma）打开时整扇**滑出方块外**，
     *  原版 DoorBlock 的"绕铰链转 90°"形状完全对不上 ⇒ 提供时改成按模型包围盒查表。 */
    private static final ThreadLocal<String> PENDING_BOX = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void prepareBox(String id) { PENDING_BOX.set(id); }

    public static void clear() {
        PENDING_PROPS.remove();
        PENDING_BOX.remove();
    }

    private final String boxedId;

    public ScriptDoorBlock(Settings settings, BlockSetType blockSetType) {
        super(settings, blockSetType);
        this.boxedId = PENDING_BOX.get();
    }

    @Override
    public net.minecraft.util.shape.VoxelShape getCollisionShape(BlockState state,
            net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.ShapeContext context) {
        net.minecraft.util.shape.VoxelShape s = BoxTable.query(boxedId, state);
        return s != null ? s : super.getCollisionShape(state, world, pos, context);
    }

    @Override
    public net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state,
            net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.ShapeContext context) {
        net.minecraft.util.shape.VoxelShape s = BoxTable.query(boxedId, state);
        // ★空盒 ≠ 空轮廓★：官方 TypeDoorDiagonal 的 getShape 恒为整格（碰撞才按 open 分空/满）。
        // 若直接返回空 shape，开着的门没轮廓 ⇒ 准星点不到 ⇒ 关不回去（C131 实测前拦下）。
        if (s != null) {
            return s.isEmpty() ? net.minecraft.util.shape.VoxelShapes.fullCube() : s;
        }
        return super.getOutlineShape(state, world, pos, context);
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
