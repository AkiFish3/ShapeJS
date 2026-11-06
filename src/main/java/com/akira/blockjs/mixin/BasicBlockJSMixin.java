package com.akira.blockjs.mixin;

import com.akira.blockjs.DynamicShapes;
import com.akira.blockjs.Multiblocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * KubeJS 的 {@code BasicBlockJS} 覆写了 {@code getOutlineShape}（返回静态 shape 字段，不调 super），
 * 所以 {@link AbstractBlockMixin} 对它的【选择盒】不生效 —— 必须在这里注入。
 *
 * <p>同理它覆写的还有 {@code getCameraCollisionShape}（相机碰撞）和 {@code onPlaced}
 * （放置回调，里面只做 blockEntity 初始化、不调 super）——前者影响第三人称相机，
 * 后者是多方块足迹的填充钩子（HorizontalDirectionalBlockJS 继承这两个覆写，一并覆盖）。
 *
 * <p>命中的规则会整体替换 KubeJS 的形状（返回的就是我们声明的并集）；
 * 没有命中时什么都不做，KubeJS 原逻辑继续跑。
 */
@Mixin(targets = "dev.latvian.mods.kubejs.block.custom.BasicBlockJS")
public abstract class BasicBlockJSMixin {

	@Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
	private void shapejs$outline(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		VoxelShape s = DynamicShapes.shapeFor(state);
		if (s != null) {
			cir.setReturnValue(s);
		}
	}

	@Inject(method = "getCameraCollisionShape", at = @At("HEAD"), cancellable = true)
	private void shapejs$camera(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		VoxelShape s = DynamicShapes.shapeFor(state);
		if (s != null) {
			cir.setReturnValue(s);
		}
	}

	/** 多方块足迹填充（服务端；KubeJS 此方块的 onPlaced 不调 super，只能在这里挂） */
	@Inject(method = "onPlaced", at = @At("TAIL"))
	private void shapejs$fillFootprint(World world, BlockPos pos, BlockState state, LivingEntity placer,
			ItemStack itemStack, CallbackInfo ci) {
		if (!world.isClient()) {
			Multiblocks.tryFill(world, pos, state);
		}
	}
}
