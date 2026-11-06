package com.akira.blockjs;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

/**
 * 桶实体：行为与原版完全一致（27 格、单箱、open 属性动画），只把 BE 类型换成我们自己的。
 *
 * <p>换类型的唯一目的：原版 {@code BlockEntityType.BARREL} 在客户端注册了 BER
 * （BarrelBlockEntityRenderer），我们的模型会和它叠成两份；新类型没有 BER ⇒ 只画模型。
 */
public class ScriptBarrelBlockEntity extends BarrelBlockEntity {

    public ScriptBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return BlockJS.CONTAINER_BARREL_TYPE;
    }
}
