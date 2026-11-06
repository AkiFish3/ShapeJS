package com.akira.blockjs;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 告示牌实体：保留「可编辑文本」能力，去掉原版 SIGN 类型上的 BER（SignBlockEntityRenderer）。
 *
 * <p>上一版直接继承了 {@code SignBlock} 的 {@code createBlockEntity}，
 * 于是得到 {@code BlockEntityType.SIGN} 的实体 —— 日志里那条
 * {@code Block entity minecraft:sign @ ... invalid for ticking} 就是它：
 * 原版 type 的 blocks 集合里没有我们的方块，tick 被整块跳过。
 *
 * <p>这里用 {@code SignBlockEntity(BlockEntityType, BlockPos, BlockState)} 直接注入自己的类型，
 * 不再靠覆写 getType()（构造期就能定死，避免 super 期间 getType 已被别处读到）。
 */
public class ScriptSignBlockEntity extends SignBlockEntity {

    public ScriptSignBlockEntity(BlockPos pos, BlockState state) {
        super(BlockJS.CONTAINER_SIGN_TYPE, pos, state);
    }
}
