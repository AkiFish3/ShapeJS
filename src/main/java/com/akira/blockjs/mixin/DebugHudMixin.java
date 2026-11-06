package com.akira.blockjs.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 F3 调试屏里 Targeted Block 行的 {@code sides=N} 摘掉。
 *
 * <p>为什么要有这个：{@code sides} 是 window_arch 那 18 族 vslab 的墙端连接位（0..3），
 * 只用于顶面封口换图。它对玩家没有意义，但会跟着 blockstate 一起打印在 F3 上，
 * 看着像是方块出了问题。
 *
 * <p>Jade 那边不用管：Jade 默认不显示 block states（只有开 F3+H 高级提示时才显示）。
 *
 * <p>{@code require = 0}：映射名对不上时只记一条警告，不拦启动。
 */
@Mixin(DebugHud.class)
public class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"), require = 0)
    private void blockjs$hideSides(CallbackInfoReturnable<List<String>> cir) {
        List<String> src = cir.getReturnValue();
        if (src == null || src.isEmpty()) {
            return;
        }
        boolean changed = false;
        List<String> out = new ArrayList<>(src.size());
        for (String s : src) {
            if (s == null) {
                out.add(null);
                continue;
            }
            String t = s.trim();
            // 1.20.1 的 F3/Jade 把每个属性单独一行、写成「sides: 2」；
            // 老式单行写法是「[facing=north,sides=2,...]」。两种都处理。
            if (t.startsWith("sides:")) {
                changed = true;
                continue;
            }
            if (s.contains("sides=")) {
                out.add(s.replaceAll("[, ]*sides=\\d+", ""));
                changed = true;
                continue;
            }
            out.add(s);
        }
        if (changed) {
            cir.setReturnValue(out);
        }
    }
}
