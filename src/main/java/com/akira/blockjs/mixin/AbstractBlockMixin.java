package com.akira.blockjs.mixin;

import com.akira.blockjs.DynamicProps;
import com.akira.blockjs.DynamicShapes;
import com.akira.blockjs.Multiblocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 方块级注入（AbstractBlock 基类）。
 *
 * <p>覆盖：碰撞/选择盒、raycast 盒、相机碰撞盒、遮光率、弱/强红石功率、
 * emitsRedstonePower 门控、锚点拆除后的 extension 清理。
 *
 * <p>注意：KubeJS 的 BasicBlockJS 覆写了 getOutlineShape / getCameraCollisionShape /
 * onPlaced 且【不调 super】—— 那三处由 BasicBlockJSMixin 单独兜（碰撞 getCollisionShape
 * 和 raycast 它没覆写，走这里）。覆写了本类方法的 vanilla 方块（楼梯等）不会执行本类方法体，
 * 天然不受影响。
 */
@Mixin(AbstractBlock.class)
public abstract class AbstractBlockMixin {

    // ---------------------------------------------------------------- 形状
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void shapejs$collision(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                   CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape s = DynamicShapes.shapeFor(state);
        if (s != null) cir.setReturnValue(s);
    }

    @Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
    private void shapejs$outline(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                 CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape s = DynamicShapes.shapeFor(state);
        if (s != null) cir.setReturnValue(s);
    }

    @Inject(method = "getRaycastShape", at = @At("HEAD"), cancellable = true)
    private void shapejs$raycast(BlockState state, BlockView world, BlockPos pos,
                                 CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape s = DynamicShapes.shapeFor(state);
        if (s != null) cir.setReturnValue(s);
    }

    @Inject(method = "getCameraCollisionShape", at = @At("HEAD"), cancellable = true)
    private void shapejs$camera(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape s = DynamicShapes.shapeFor(state);
        if (s != null) cir.setReturnValue(s);
    }

    // ---------------------------------------------------------------- 遮光 / 红石
    @Inject(method = "getOpacity", at = @At("HEAD"), cancellable = true)
    private void shapejs$opacity(BlockState state, BlockView world, BlockPos pos,
                                 CallbackInfoReturnable<Integer> cir) {
        Integer v = DynamicProps.opacityFor(state);
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "getWeakRedstonePower", at = @At("HEAD"), cancellable = true)
    private void shapejs$weakPower(BlockState state, BlockView world, BlockPos pos, Direction direction,
                                   CallbackInfoReturnable<Integer> cir) {
        Integer v = DynamicProps.weakPowerFor(state);
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "getStrongRedstonePower", at = @At("HEAD"), cancellable = true)
    private void shapejs$strongPower(BlockState state, BlockView world, BlockPos pos, Direction direction,
                                     CallbackInfoReturnable<Integer> cir) {
        Integer v = DynamicProps.strongPowerFor(state);
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "emitsRedstonePower", at = @At("HEAD"), cancellable = true)
    private void shapejs$emits(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (DynamicProps.hasRedstone(state.getBlock())) cir.setReturnValue(true);
    }

    // ---------------------------------------------------------------- 多方块：锚点被拆 → 清 extension
    // TAIL：此时锚点格已是新状态（空气），extension 级联查不到锚点 ⇒ 静默消失、不掉落、不递归
    @Inject(method = "onStateReplaced", at = @At("TAIL"))
    private void shapejs$clearExtensions(BlockState state, World world, BlockPos pos, BlockState newState,
                                         boolean moved, CallbackInfo ci) {
        if (!world.isClient() && !state.isOf(newState.getBlock())) {
            Multiblocks.clearExtensions(world, pos, state);
        }
    }
}
