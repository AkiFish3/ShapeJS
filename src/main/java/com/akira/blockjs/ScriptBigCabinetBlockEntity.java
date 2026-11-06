package com.akira.blockjs;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 大柜子实体：与箱子【完全一致】的容量语义 —— 单格 27 格、双联 54 格。
 *
 * <p>★别再覆写 size() / createScreenHandler★：上一版把它改成 54 格单一实体，
 * 结果单格就是 54 且与箱子行为不一致。原版的双联是
 * {@code ChestBlock$2$1.createMenu → createGeneric9x6(DoubleInventory(be1, be2))}，
 * 两个 27 格拼成 54 格 9×6；单格走 {@code ChestBlockEntity.createScreenHandler → createGeneric9x3}。
 * 只要 BE 保持默认 27 格、block 不自己给工厂，两条路径都是现成的。
 *
 * <p>保留自定义 type 的唯一原因：让客户端不注册 BER（只画我们自己的模型），
 * 顺便让 GUI 标题是「大柜子」。
 */
public class ScriptBigCabinetBlockEntity extends ChestBlockEntity {

    public ScriptBigCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return BlockJS.CONTAINER_BIGCABINET_TYPE;
    }

    @Override
    protected Text getContainerName() {
        return Text.translatable("container.blockjs.bigcabinet");
    }
}
