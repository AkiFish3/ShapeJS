package com.akira.blockjs;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;

/**
 * 床实体：只保留颜色数据，去掉原版 BED 类型上的 BER（BedBlockEntityRenderer）。
 *
 * <p>床的外观在原版是「模型 + BER」两层，我们的方块已经 {@code getRenderType → MODEL}
 * 且自带床形模型，留着原版 BER 就是两张床叠在一起。
 */
public class ScriptBedBlockEntity extends BedBlockEntity {

    public ScriptBedBlockEntity(BlockPos pos, BlockState state, DyeColor color) {
        super(pos, state, color);
    }

    /** BlockEntityType 的工厂签名是 (BlockPos, BlockState)：颜色由 NBT 读回（BedBlockEntity.writeNbt 会写） */
    public ScriptBedBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, DyeColor.WHITE);
    }

    @Override
    public BlockEntityType<?> getType() {
        return BlockJS.CONTAINER_BED_TYPE;
    }
}
