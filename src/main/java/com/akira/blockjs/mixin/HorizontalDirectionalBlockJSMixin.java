package com.akira.blockjs.mixin;

import com.akira.blockjs.DynamicShapes;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.block.ShapeContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 同 {@link BasicBlockJSMixin}，但面向 KubeJS 的 {@code HorizontalDirectionalBlockJS}
 * （cardinal 型方块 —— 它又覆写了一次 {@code getOutlineShape}，按 facing 查 Map 旋转）。
 *
 * <p>竖台阶（vslab / vwalls / vstairs 这一族 cardinal 方块）走这里。
 */
@Mixin(targets = "dev.latvian.mods.kubejs.block.custom.HorizontalDirectionalBlockBuilder$HorizontalDirectionalBlockJS")
public abstract class HorizontalDirectionalBlockJSMixin {

	@Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
	private void shapejs$outline(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		VoxelShape s = DynamicShapes.shapeFor(state);
		if (s != null) {
			cir.setReturnValue(s);
		}
	}
}
