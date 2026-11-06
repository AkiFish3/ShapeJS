package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 桶（原版 BarrelBlock，27 格容器） + 可选追加属性。
 *
 * <p>换自定义 BE 类型是为了甩掉原版 BER；代价是 {@code BarrelBlock.getTicker} 里的
 * {@code validateTicker(type, BlockEntityType.BARREL, …)} 恒不相等 ⇒ tick 被静默跳过。
 * 桶的 tick 负责「关盖子后 5 tick 把 open 属性复位」，漏了就会一直张嘴，
 * 所以这里把 ticker 自己接回去。
 */
public class ScriptBarrelBlock extends BarrelBlock {

    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
    }

    public ScriptBarrelBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ScriptBarrelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type != BlockJS.CONTAINER_BARREL_TYPE) return null;
        return (w, pos, st, be) -> {
            if (be instanceof ScriptBarrelBlockEntity barrel) barrel.tick();
        };
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
