package com.akira.blockjs.mixin;

import com.akira.blockjs.DynamicLights;
import com.akira.blockjs.DynamicOffsets;
import com.akira.blockjs.DynamicProps;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.piston.PistonBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 状态级注入（AbstractBlockState）。
 *
 * <p>亮度：1.20.1 里 {@code luminance} 是构造期缓存字段，
 * 但所有读取都走 {@code getLuminance()} 这个 getter —— 在 HEAD 拦截即可整体接管，
 * 且对【所有】方块生效（与形状不同，亮度没有"KubeJS 覆写不调 super"的坑：
 * BasicBlockJS 根本不覆写 getLuminance）。光照引擎在 setBlockState 时对比新旧
 * getLuminance 自动重算 ⇒ 切灯会正确传播光照。
 *
 * <p>活塞行为：{@code getPistonBehavior()} 同样是状态级 getter（缓存字段
 * {@code pistonBehavior}），同法接管。
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {

	@Inject(method = "getLuminance", at = @At("HEAD"), cancellable = true)
	private void shapejs$luminance(CallbackInfoReturnable<Integer> cir) {
		Integer lv = DynamicLights.lightFor((net.minecraft.block.BlockState) (Object) this);
		if (lv != null) {
			cir.setReturnValue(lv);
		}
	}

	@Inject(method = "getPistonBehavior", at = @At("HEAD"), cancellable = true)
	private void shapejs$piston(CallbackInfoReturnable<PistonBehavior> cir) {
		PistonBehavior pb = DynamicProps.pistonFor((net.minecraft.block.BlockState) (Object) this);
		if (pb != null) {
			cir.setReturnValue(pb);
		}
	}

	/**
	 * 渲染期位移（C114 建通道 / C158 通用化）：官方 minia 用 1.21 的
	 * {@code Properties.offsetFunction} + {@code IMTOffset}，1.20.1 只能接
	 * {@code getModelOffset} —— 原版草/花偏移走的就是这条路径，
	 * {@code BlockModelRenderer} 会把返回值加到模型平移上。
	 * {@code offsets.json} 两种写法：数组 = 旧式（仅 {@code half=top}），
	 * 对象 = 按 {@code 属性=取值} 条件匹配（支持无 half 的族，如椅子/电视/轮子）。
	 */
	@Inject(method = "getModelOffset", at = @At("HEAD"), cancellable = true)
	private void shapejs$modelOffset(net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos,
									 CallbackInfoReturnable<net.minecraft.util.math.Vec3d> cir) {
		net.minecraft.util.math.Vec3d v = DynamicOffsets.offsetFor((net.minecraft.block.BlockState) (Object) this);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}
}
