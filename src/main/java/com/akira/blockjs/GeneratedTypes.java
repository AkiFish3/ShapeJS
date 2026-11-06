// 自动生成：_gen_p3p9.py（勿手改）
package com.akira.blockjs;

import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;

/**
 * P3~P9 用的批量类型：每个"属性签名"一个类型，几何一律走 {@code kind=boxed} 的盒表。
 *
 * <p>类型名 = 属性名缩写 + 取值个数（{@code x_f4_half2_shape5_wl2}），
 * {@code deco} = 无属性，{@code x_cross} = 十字植被（无碰撞）。
 */
final class GeneratedTypes {

    /** ★灯类共用同一个 lighting 实例★：lightsFrom 按属性实例匹配，
     *  每个类型各建一个 BooleanProperty.of("lighting") 会导致明暗不生效。 */
    private static final BooleanProperty LIGHTING = BooleanProperty.of("lighting");

    private GeneratedTypes() {}

    static void register() {
        BlockJS.registerGen("deco", b -> {
            b.kind("boxed");
        });
        BlockJS.registerGen("x_arml_armr_f4_shape5", b -> {
            b.extraProp(BooleanProperty.of("arm_left"));
            b.extraProp(BooleanProperty.of("arm_right"));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.STAIR_SHAPE);
            b.placeMode("stairlike");   // ★半砖/拱门/沙发：朝向=玩家朝向，shape 才稳（原 facingFromHit 会随点击近/远半翻 180°）
            b.kind("boxed");
        });
        BlockJS.registerGen("x_bottom_click8_half2_top", b -> {
            b.extraProp(BooleanProperty.of("bottom"));
            b.extraProp(IntProperty.of("click", 0, 7));
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(BooleanProperty.of("top"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_carpet_f4", b -> {
            b.extraProp(BooleanProperty.of("carpet"));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_click2_f4", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_click2_f4_fence_shape4", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("fence"));
            b.extraProp(IntProperty.of("shape", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_click2_f6", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.FACING);
            b.kind("boxed");
        });
        // ★stick_v 改造（Aki 2026-10-27）：facing(6向) → axis(3向，按点击面轴) + 含水★
        //   click(0..1) 仍按点击高度分档；axis 由 ScriptBlock 用 ctx.getSide().getAxis() 决定；
        //   waterlogged 由 waterloggable() 在放置点含水时自动置 true。
        BlockJS.registerGen("x_click2_axis_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.AXIS);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
        });
        BlockJS.registerGen("x_click3_f4_vert_suml", b -> {
            b.extraProp(IntProperty.of("click", 0, 2));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("suml", "vertical"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_click4_f4", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_click4_f4_fence_shape4", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("fence"));
            b.extraProp(IntProperty.of("shape", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_conn_f4", b -> {
            b.extraProp(BooleanProperty.of("connect"));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_corner5_f4_pos2", b -> {
            b.extraProp(IntProperty.of("corner", 0, 4));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("position", 0, 1));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_cross", b -> {
            b.kind("cross");
        });
        BlockJS.registerGen("x_cross_f4", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("cross");
        });
        BlockJS.registerGen("x_cross_f4_half2_shape5_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("cross");
        });
        BlockJS.registerGen("x_cross_f6_lit", b -> {
            b.extraProp(Properties.FACING);
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.kind("cross");
        });
        BlockJS.registerGen("x_cross_lit", b -> {
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.kind("cross");
        });
        BlockJS.registerGen("x_cross_vert4", b -> {
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.kind("cross");
        });
        BlockJS.registerGen("x_ctable", b -> {
            b.extraProp(BooleanProperty.of("coffeetable"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_ctable_f4", b -> {
            b.extraProp(BooleanProperty.of("coffeetable"));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_dbl_f4", b -> {
            b.extraProp(BooleanProperty.of("double"));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_down", b -> {
            b.extraProp(BooleanProperty.of("down"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_down_east_north_south_up_west", b -> {
            b.extraProp(BooleanProperty.of("down"));
            b.extraProp(BooleanProperty.of("east"));
            b.extraProp(BooleanProperty.of("north"));
            b.extraProp(BooleanProperty.of("south"));
            b.extraProp(BooleanProperty.of("up"));
            b.extraProp(BooleanProperty.of("west"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_down_f4_type_slrb_up", b -> {
            b.extraProp(BooleanProperty.of("down"));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("slrb", "type"));
            b.extraProp(BooleanProperty.of("up"));
            // ★抽屉族：左右 type（单/左/双/右）+ 上下 up/down 都要随邻居实时算★
            b.placeMode("type4");
            b.vstack();
            b.kind("boxed");
        });
        BlockJS.registerGen("x_east_f4_north_south_west", b -> {
            b.extraProp(BooleanProperty.of("east"));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("north"));
            b.extraProp(BooleanProperty.of("south"));
            b.extraProp(BooleanProperty.of("west"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_east_lit_north_south_west", b -> {
            b.extraProp(BooleanProperty.of("east"));
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.extraProp(BooleanProperty.of("north"));
            b.extraProp(BooleanProperty.of("south"));
            b.extraProp(BooleanProperty.of("west"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_east_north_south_up_wl_west", b -> {
            b.extraProp(BooleanProperty.of("east"));
            b.extraProp(BooleanProperty.of("north"));
            b.extraProp(BooleanProperty.of("south"));
            b.extraProp(BooleanProperty.of("up"));
            b.extraProp(Properties.WATERLOGGED);
            b.extraProp(BooleanProperty.of("west"));
            b.waterloggable();
            b.kind("boxed");
        });
        BlockJS.registerGen("x_east_north_south_west", b -> {
            b.extraProp(BooleanProperty.of("east"));
            b.extraProp(BooleanProperty.of("north"));
            b.extraProp(BooleanProperty.of("south"));
            b.extraProp(BooleanProperty.of("west"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_east_north_south_wl_west", b -> {
            b.extraProp(BooleanProperty.of("east"));
            b.extraProp(BooleanProperty.of("north"));
            b.extraProp(BooleanProperty.of("south"));
            b.extraProp(Properties.WATERLOGGED);
            b.extraProp(BooleanProperty.of("west"));
            b.waterloggable();
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f12", b -> {
            b.extraProp(IntProperty.of("facing", 0, 11));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_fence_pos3", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("fence"));
            b.extraProp(IntProperty.of("position", 0, 2));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_half2_shape5_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");   // ★半砖/拱门/沙发：朝向=玩家朝向，shape 才稳（原 facingFromHit 会随点击近/远半翻 180°）
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_half2_type3", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(IntProperty.of("type", 0, 2));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_half_lrm_type_slrb", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("lrm", "half"));
            b.extraProp(DynamicEnums.get("slrb", "type"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_half_ul", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("ul", "half"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_inwall_open_pow", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("in_wall"));
            b.extraProp(BooleanProperty.of("open"));
            b.extraProp(BooleanProperty.of("powered"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_lit", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_lit_type_slrb", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.extraProp(DynamicEnums.get("slrb", "type"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_lit_type_subd", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.extraProp(DynamicEnums.get("subd", "type"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_ly8", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("layers", 1, 8));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_mts2", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("mtshape", 0, 1));
            // ★墙角石 mtshape 按点击位置左右半边取★（官方是随机 nextInt(3)，固定 0 会永远朝一侧）
            b.placeMode("mtshape");
            b.kind("boxed");
        });
        // C144c（Aki 2026-11-05）：墙角石 facing 改成「被点击的面」= 贴合目标方块。
        // 只给 `mt_quoin_*` 61 个 id 用；`x_f4_mts2` 另外还有 181 个 id（roof_edge 等）保持玩家朝向。
        BlockJS.registerGen("x_f4_mts2_hit", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("mtshape", 0, 1));
            b.placeMode("mtshape_hit");
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_mts2_shape5_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("mtshape", 0, 1));
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");   // ★半砖/拱门/沙发：朝向=玩家朝向，shape 才稳（原 facingFromHit 会随点击近/远半翻 180°）
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_mts3", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("mtshape", 0, 2));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_mts4", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("mtshape", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_mts5", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("mtshape", 0, 4));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_on_vert4", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("on"));
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_pos2", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("position", 0, 1));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_sep_vert4", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("separate"));
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_shape5", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.STAIR_SHAPE);
            b.placeMode("stairlike");   // ★半砖/拱门/沙发：朝向=玩家朝向，shape 才稳（原 facingFromHit 会随点击近/远半翻 180°）
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_shift", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("shift"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_shift_type_slrb", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("shift"));
            b.extraProp(DynamicEnums.get("slrb", "type"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_shift_vshift", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("shift"));
            b.extraProp(BooleanProperty.of("vshift"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_slab", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("slab"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_slab3", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("slab", 0, 2));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_sofa10", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("sofa", 0, 9));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_sw", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("switch"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_type2_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("type", 0, 1));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
        });
        // ★2026-10-29 Aki：rug_anywhere —— 面向 + 「是否铺在沙/土上方」布尔 sand。
        //   之前缺这个类型，导致 rug_anywhere 只能压 couched 进其它类型 ⇒ blockstate 的 sand= 永远读不到。
        BlockJS.registerGen("x_f4_sand", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("sand"));
            b.extraDefault(BooleanProperty.of("sand"), Boolean.FALSE);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_type4", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("type", 0, 3));
            // ★沙发/长凳/柜/桌 type=0单 1左 2双 3右★：按左右邻块自动算（原来恒 0 ⇒ 同排永远不连）
            b.placeMode("type4");
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_type_slrb", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("slrb", "type"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_vert4", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_water", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("water"));
            b.kind("block");
        });
        BlockJS.registerGen("x_f6", b -> {
            b.extraProp(Properties.FACING);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f6_lit", b -> {
            b.extraProp(Properties.FACING);
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f6_vert4", b -> {
            b.extraProp(Properties.FACING);
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f6_wl", b -> {
            b.extraProp(Properties.FACING);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f8", b -> {
            b.extraProp(IntProperty.of("facing", 0, 7));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_half_ul", b -> {
            b.extraProp(DynamicEnums.get("ul", "half"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_lit", b -> {
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.kind("boxed");
        });
        BlockJS.registerGen("x_mts2", b -> {
            b.extraProp(IntProperty.of("mtshape", 0, 1));
            b.placeMode("mtshape");
            b.kind("boxed");
        });
        BlockJS.registerGen("x_on", b -> {
            b.extraProp(BooleanProperty.of("on"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_pos2", b -> {
            b.extraProp(IntProperty.of("position", 0, 1));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_pos9", b -> {
            b.extraProp(IntProperty.of("position", 0, 8));
            b.kind("block");
        });
        BlockJS.registerGen("x_pot", b -> {
            b.extraProp(BooleanProperty.of("flowerpot"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_slab", b -> {
            b.extraProp(BooleanProperty.of("slab"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_up", b -> {
            b.extraProp(BooleanProperty.of("up"));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_var16", b -> {
            b.extraProp(IntProperty.of("variant", 0, 15));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_var3", b -> {
            b.extraProp(IntProperty.of("variant", 0, 2));
            b.kind("block");
        });
        BlockJS.registerGen("x_var8", b -> {
            b.extraProp(IntProperty.of("variant", 0, 7));
            b.kind("block");
        });
        BlockJS.registerGen("x_vert4", b -> {
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.kind("boxed");
        });
        BlockJS.registerGen("x_water", b -> {
            b.extraProp(BooleanProperty.of("water"));
            b.kind("boxed");
        });
    }
}
