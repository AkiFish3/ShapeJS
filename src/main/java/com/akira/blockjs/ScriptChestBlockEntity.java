package com.akira.blockjs;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 箱子实体：复用原版 {@link ChestBlockEntity} 的仓储 / 双箱 / 界面逻辑，
 * 只把 {@code getType()} 换成我们自己的 BlockEntityType。
 *
 * <p>关键作用：原版箱子靠 ChestBlockEntityRenderer（BER）画外观；
 * 换成自定义 type 后没有 BER 注册，于是只渲染我们自己的模型 —— 不会出现「双份箱子」。
 */
public class ScriptChestBlockEntity extends ChestBlockEntity {

    public ScriptChestBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return BlockJS.CONTAINER_CHEST_TYPE;
    }
}
