package com.akira.blockjs;

import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;

/**
 * 手写补充类型（不进 {@link GeneratedTypes} 的自动生成流程）。
 *
 * <p>这些都是 2026-10-27 恢复「官方有、我们丢失」的属性时新增的：
 * <ul>
 *   <li>{@code half_bt}        — podzol_half 这类只有 {@code half=bottom|top} 的方块</li>
 *   <li>{@code click8_half_ul}  — 8doubleplants：{@code click=0..7} × {@code half=upper|lower}</li>
 *   <li>{@code pos6_up}        — wood_hpole：{@code position} 六值 × {@code up}</li>
 *   <li>{@code x_f4_open}      — 百叶窗/遮板：{@code facing} + {@code open}（可开关）</li>
 * </ul>
 */
final class ExtraTypes {

    private ExtraTypes() {}

    /** 灯类共用同一实例（lightsFrom 按实例匹配） */
    private static final BooleanProperty LIGHTING = BooleanProperty.of("lighting");

    /** minia 官方灯的开关属性（`BooleanProperty.create("switch")`，默认 FALSE）；同样只能有一个实例。 */
    private static final BooleanProperty SWITCH = BooleanProperty.of("switch");

    /** ★布尔属性默认值是 TRUE（枚举首值）★，这几个都得显式定 false。 */
    private static final BooleanProperty UP = BooleanProperty.of("up");
    private static final BooleanProperty OPEN = BooleanProperty.of("open");
    /** 红石驱动标记（百叶窗全族）；默认必须显式 false，否则放下就是「通电」态 */
    private static final BooleanProperty POWERED = BooleanProperty.of("powered");

    /** 树枝的 4 个「接在树干上」标记（C68；官方 `LogBranch.TRUNK_*`，与 north/... 是两个独立属性） */
    private static final BooleanProperty TRUNK_NORTH = BooleanProperty.of("trunk_north");
    private static final BooleanProperty TRUNK_EAST = BooleanProperty.of("trunk_east");
    private static final BooleanProperty TRUNK_SOUTH = BooleanProperty.of("trunk_south");
    private static final BooleanProperty TRUNK_WEST = BooleanProperty.of("trunk_west");

    static void register() {
        // ---- podzol_half：只有 half ----
        BlockJS.registerGen("half_bt", b -> {
            b.extraProp(Properties.BLOCK_HALF);
            b.extraDefault(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.BOTTOM);
            b.kind("boxed");
        });

        // ---- C19 收尾（2026-10-30）：4 个 variant 值的整块（bm_b1/brick_b/steel_checkered_a/wood_d2）----
        //  官方 8 键 = 4 variant × 2 half，但 half=bottom/top **指向同一模型** ⇒ half 是死属性，只落 variant。
        BlockJS.registerGen("x_var4", b -> {
            b.extraProp(IntProperty.of("variant", 0, 3));
            b.kind("block");
        });

        // ---- C19 收尾：grass_half_a_series = half(bottom/top) × variant(0..4)，10 键 ----
        //  half 在这里是真维度（bottom→grass_half 下半、top→grass_upper 上半），几何用 halfslab。
        BlockJS.registerGen("half_var5", b -> {
            b.extraProp(Properties.BLOCK_HALF);
            b.extraDefault(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.BOTTOM);
            b.extraProp(IntProperty.of("variant", 0, 4));
            b.kind("halfslab");
        });

        // ---- 8doubleplants：click × half(upper/lower) ----
        BlockJS.registerGen("click8_half_ul", b -> {
            b.extraProp(IntProperty.of("click", 0, 7));
            b.extraProp(DynamicEnums.get("ul", "half"));
            b.kind("boxed");
        });

        // ---- wood_hpole（旧）：position × up ----
        BlockJS.registerGen("pos6_up", b -> {
            b.extraProp(DynamicEnums.get("hpos", "position"));
            b.extraProp(UP);
            b.extraDefault(UP, Boolean.TRUE);   // 默认贴上沿，与旧观感一致
            b.kind("boxed");
        });

        // ---- wood_hpole（C3 重构）：facing(轨道走向) × type(左/中/右车道) × up ----
        //  旧 position 六值把「轴」和「车道」死绑（x_* 只能沿 X、z_* 只能沿 Z），
        //  换成 facing+type 后 4×3 可自由组合；同一排沿 facing 轴本来就满格贯通（同排自连），
        //  端头的 3.5px 外挑就是「端头」。
        BlockJS.registerGen("x_f4_lrm_up", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("lrm", "type"));
            b.extraProp(UP);
            b.extraDefault(UP, Boolean.TRUE);   // 默认贴上沿，与旧观感一致
            b.placeMode("lrmrow");
            b.kind("boxed");
        });



        // ---- ★minia 的 half+shape（MT_VHalf 语义：前→outer、后→inner）★ ----
        BlockJS.registerGen("x_f4_half2_shape5_wl_mt", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt");
            b.kind("boxed");
        });


        // ---- 同上 + click（minia 的多款式索引；官方 click 会换模型）----
        BlockJS.registerGen("x_f4_half2_click2_shape5_wl_mt", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt");
            b.kind("boxed");
        });
        BlockJS.registerGen("x_f4_half2_click4_shape5_wl_mt", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt");
            b.kind("boxed");
        });
        // ---- ★C15-2 第 1 组（2026-10-30）：click(0..1) + facing + shape5 + 含水，放置沿用 vslab★ ----
        //  官方 minia `mt_<id>.json` 40 键 = click2 × facing4 × shape5；本地原来只有 facing×shape×waterlogged
        //  ⇒ 每个 id 补回 click 后 80 键（waterlogged 不写进键，由 loader 按「键里出现的属性」折叠）。
        //  placeMode 必须还是 vslab：这批 id 全在 `zz_port_batch4.js` 的 `B4_VSLAB` 里按 vslab 注册。
        BlockJS.registerGen("x_click2_f4_shape5_vslab", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.STAIR_SHAPE);
            b.waterloggable();
            b.placeMode("vslab");
            b.kind("boxed");
        });

        // ---- ★C71（2026-11-06 第 134 轮）：click4 × facing4，click 恒 0★ ----
        //  官方 `TypeGlassRack`（1 个 `wine_glass_rack`）extends `MT_4ClickFacing`：
        //    getStateForPlacement: FACING = getHorizontalDirection().getOpposite()（与本地默认分支一致）、CLICK = iconst_0。
        //  click 1/2/3 只在 mtGetOffset 里用（`getShift(facing)` / `SHIFT_HALF_UP` / `getShiftHalfUp(facing)`），
        //  **不进轮廓也不进碰撞**（`MT_4ClickFacing` 未覆写 getShape/getCollisionShape）⇒ 盒表仍是 4 条 facing cond。
        BlockJS.registerGen("x_click4_f4_mt", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.autoClick(false);
            b.kind("boxed");
        });

        // ---- ★C71c/C71d（2026-11-06 第 134 轮）：click2 × facing4 × half2，**不含水**、**click 恒 0**★ ----
        //  官方 `TypeCookware` / `TypeBarrelHorizontalShift` 均 extends `MT_2ClickFacingHUD`：
        //    getStateForPlacement: facing = 玩家朝向本身；half = 点击面（UP→BOTTOM / DOWN→TOP / 侧面→TOP）；click = iconst_0。
        //  ⇒ 必须 `autoClick(false)`（否则我们的自动分段会按点击高度写 click=1，位移一开就整体漂 0.5~1 格）；
        //     并且 `placeMode("mt2hud")` 让 half 走**面判定**（侧面恒 TOP），而不是本地的 hitY>0.5 判定。
        //  官方 blockstate 只有 16 键（无 waterlogged）⇒ 本类型不挂 waterloggable。
        BlockJS.registerGen("x_click2_f4_half2_mt2hud", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.autoClick(false);
            b.placeMode("mt2hud");
            b.kind("boxed");
        });

        // ---- ★C15-2 第 4 组（2026-10-30 第 25 轮）：click(0..1) + facing + half + 含水（无 shape）★ ----
        //  官方 `mt_<id>.json` 16 键 = click2 × facing4 × half2（桶/箱/钢琴/油桶这类「躺倒或立起」的物件）。
        //  本地原来只有 facing ⇒ 补 click 与 half 两维；本实例 A10 口径再加 waterlogged ⇒ 类型域 32 键。
        //  ★不依赖新盒表★：这批 id 在 boxes.json 已有条目（按 facing 建的自算盒），BoxTable 是子集匹配
        //  （cond 里没写 half 时 half 两值共用同一组盒），只有 ornament_sphere_0/1 缺条目 ⇒ 本批已剔除。
        BlockJS.registerGen("x_click2_f4_half2", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.waterloggable();
            b.placeMode("stairlike");
            b.kind("boxed");
        });

        // ---- ★C15-2 第 3 组（2026-10-30）：click(0..1) + facing + half + shape8（ROOF8）+ 含水★ ----
        //  官方 minia `mt_<id>.json` 128 键 = click2 × facing4 × half2 × shape8；
        //  shape8 = 原版楼梯 5 形 + `single` / `l_straight` / `r_straight`（半格瓦），
        //  vanilla STAIR_SHAPE 只有 5 值 ⇒ 必须用 DynamicEnums.ROOF8，否则 3 个值写不进 blockstate。
        //  shapeRule 仍用 mt（只产出 5 个楼梯形，全部是 ROOF8 的子集 ⇒ setByName 不会写坏）。
        //  ★几何/碰撞全部来自盒表（kind=boxed）★：本类的 id 在 boxes.json 里已有 facing×half×shape 8 值条目；
        //  没有条目的族**不要**挂这个类型（会落到自算几何、楼梯变半块）——见 `_HANDOFF.md` C15-3 阻塞。
        BlockJS.registerGen("x_click2_f4_half2_shape8", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(DynamicEnums.get("roof8", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt");
            b.kind("boxed");
        });

        // ---- coc 的 half+shape，但缺 half 的那一族（drainpipe_gutter） ----
        BlockJS.registerGen("x_f4_half2_shape5", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.placeMode("stairlike");
            b.kind("boxed");
        });

        // ---- shape = single/left/right/middle（4 值）----
        BlockJS.registerGen("x_f4_half2_slrm_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(DynamicEnums.get("slrm", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("slrm");
            b.kind("boxed");
        });


        // ---- 4 值 shape + click（minia 的 frill_arm 等）----
        BlockJS.registerGen("x_f4_half2_click2_slrm_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(DynamicEnums.get("slrm", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("slrm");
            b.kind("boxed");
        });
        // ---- shape = left/right（2 值，静态：不做邻居推导，默认 left）----
        BlockJS.registerGen("x_f4_half2_lr_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(DynamicEnums.get("lr", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.kind("boxed");
        });

        // ---- ★minia 垂直分段家具★：柱 / 竖梯 / 灯柱 / 货架 / 冰箱 ----
        //   官方 shape = single|bottom|middle|top（竖直第几段），不是楼梯的内外角。
        //   2026-10-27 回归：这 30 个方块被投到楼梯 5 值 shape ⇒ 永远只有 bottom 模型。
        BlockJS.registerGen("x_f4_click4_vsec4_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("vsec", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("vconnect");
            b.kind("boxed");
        });

        // ---- ★minia 货架 / 床架★：click(0..3) + facing + 16 值段位 shape ----
        //   官方 256 变体 = click4 × facing4 × shape16（水平 l/m/r/单列 × 垂直 b/m/t/单段）。
        //   2026-10-28 前这批被错投成楼梯型（facing+half+shape5）⇒ 只剩 80 变体、模型全错。
        BlockJS.registerGen("x_f4_click4_shelf_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("shelf", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            // facing = 玩家朝向（与同批 bench/desk 家具一致）；shape 由 shapeRule=shelf 现算
            b.placeMode("stairlike");
            b.shapeRule("shelf");
            b.kind("boxed");
        });

        // 同上 + 发光（lamp_stand_* 这类灯柱）
        BlockJS.registerGen("x_f4_click4_vsec4_wl_lit", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("vsec", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.extraProp(LIGHTING);
            b.waterloggable();
            b.lightsFrom(LIGHTING, 15, 0);
            b.placeMode("vconnect");
            b.kind("boxed");
        });


        // ---- ★facade 外墙板★：NSEW + variant(meta0..4)，**不带 waterlogged** ----
        //   官方 TypeFacade extends MT_Meta5setConnectFence：NSEW = 邻居 isSolidRender（或栅栏门），
        //   meta3/4 才是带线脚的转角/端头/中段模型；放置恒定 meta0（EnumType.values()[0]）。
        BlockJS.registerGen("x_nsew_var5", b -> {
            for (net.minecraft.state.property.BooleanProperty sp : new net.minecraft.state.property.BooleanProperty[]{
                    Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST}) {
                b.extraProp(sp);
                b.extraDefault(sp, Boolean.FALSE);
            }
            b.extraProp(DynamicEnums.get("meta5", "variant"));
            b.kind("boxed");
        });
        // ---- *_rod：竖柱三轴 + 含水 ----
        // ---- 拱门 arch3 用：facing + half + waterlogged（无 shape，只有上下两段模型）----
        BlockJS.registerGen("x_f4_half2_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.kind("boxed");
        });

        // ---- 只有 axis、不含 waterlogged（原版 cube 类：木板块 / 柴块）----
        BlockJS.registerGen("axis_only", b -> {
            b.extraProp(Properties.AXIS);
            b.kind("boxed");
        });

        BlockJS.registerGen("axis_box", b -> {
            b.extraProp(Properties.AXIS);
            b.waterloggable();
            b.kind("boxed");
        });

        // ---- 会闪的灯：facing + lighting（每 10 tick 翻一次）----
        BlockJS.registerGen("x_f4_lit_blink", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.blink();
            b.kind("boxed");
        });
        // ---- 百叶窗/遮板：facing + open（空手右键开关，开=模型转 90°平贴邻墙） ----
        BlockJS.registerGen("x_f4_open", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(OPEN);
            b.extraDefault(OPEN, Boolean.FALSE);
            b.kind("boxed");
        });

        // ---- 百叶窗全族标准形（Aki 2026-10-28）：facing + open + powered ----
        //   powered 由 ScriptBlock#neighborUpdate 跟随红石信号，信号变化时同步 open
        //   （同原版活板门）；手动右键只翻 open，powered 不动。
        BlockJS.registerGen("x_f4_open_pow", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(OPEN);
            b.extraProp(POWERED);
            b.extraDefault(OPEN, Boolean.FALSE);
            b.extraDefault(POWERED, Boolean.FALSE);
            b.kind("boxed");
        });

        // 带 vertical（上/中/下三分）的百叶/卷帘
        BlockJS.registerGen("x_f4_vert4_open_pow", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.extraProp(OPEN);
            b.extraProp(POWERED);
            b.extraDefault(OPEN, Boolean.FALSE);
            b.extraDefault(POWERED, Boolean.FALSE);
            b.kind("boxed");
        });

        // 带 separate + vertical 的竹帘类（separate 默认值保持原样 TRUE，不动）
        BlockJS.registerGen("x_f4_sep_vert4_open_pow", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(BooleanProperty.of("separate"));
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.extraProp(OPEN);
            b.extraProp(POWERED);
            b.extraDefault(OPEN, Boolean.FALSE);
            b.extraDefault(POWERED, Boolean.FALSE);
            b.kind("boxed");
        });

        // ---- facing + waterlogged（官方只有 facing，Aki 2026-10-27 要求非完整方块都能含水）----
        BlockJS.registerGen("x_f4_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
        });

        // ---- minia marble_stick_h：facing + mtshape(0..2) + shape + waterlogged ----
        BlockJS.registerGen("x_f4_mts3_shape5_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(IntProperty.of("mtshape", 0, 2));
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
            b.placeMode("mtshape");
        });

        // ---- coc 竖链：三轴 + 含水；chain_horizontal 仍保留 NSEW 连接 ----
        BlockJS.registerGen("x_cross_axis_wl", b -> {
            b.extraProp(Properties.AXIS);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("cross");
        });
        // coc hangingchain：up/down 已由模型区分，只补含水
        BlockJS.registerGen("x_cross_wl", b -> {
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("cross");
        });

        // ---- 自动竖直分段：窗帘、幔帐、竹帘 ----
        BlockJS.registerGen("x_f4_vert4_auto", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.vertical();
            b.kind("boxed");
        });
        // coc 茶几：同朝向左右相邻时自动 single/left/both/right
        BlockJS.registerGen("x_f4_type_slrb_auto", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("slrb", "type"));
            b.placeMode("type4");
            b.kind("boxed");
        });
        // ---- ★C143（第 120 轮）minia 吊挂食物：click(0..2) + facing(4)★ ----
        //  官方 `mt_hanging_food_{a..j}` 是 `TypeGlassPane extends MT_3ClickFacing`，blockstate 12 键
        //  （click 0..2 × facing 4）；本地原来挂 `x_f4` 只有 4 键 ⇒ 缺 click 维。
        //  ★碰撞（javap `TypeGlassPane.getCollisionShape`）★：
        //    click==0 ⇒ CENTER = `UtilAABB.getBlockBounds(facing, .45,0,0, .55,1,1)`（0.1 厚居中薄片）；
        //    click!=0 ⇒ EDGE   = `getBlockBounds(facing, 0,0,0, .1,1,1)`（0.1 厚，靠 facing 反向那侧）。
        //    `UtilAABB.fixRotation`：EAST=identity / SOUTH=(1-z2,x1,1-z1,x2) / WEST=(1-x2,1-z2,1-x1,z1) /
        //    NORTH=(z1,1-x2,z2,1-x1)。盒表见 `_c143_hanging_boxes.py`。
        //  ★已知缺口（写进 END/CPLAN，不在本批修）★：官方 `mtGetOffset` 对 click=1/2 还有
        //    ±0.5 / ±1.0 的**渲染**位移（`UtilOffsets.getShiftVHalf|getShift`），本地无逐状态位移通道。
        //  轮廓（`getShape`）click==0 走整格，盒表单通道取碰撞值 ⇒ 选中框略小于官方（仅外观）。
        //  placeMode 故意留空：与现网 `x_f4` 同一分支（#286：换类型别顺手翻 180°）。
        BlockJS.registerGen("x_click3_f4", b -> {
            b.extraProp(IntProperty.of("click", 0, 2));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
            // ★C71a 修（2026-11-06 第 134 轮）★ 这一族 21 个 id 官方都是 minia `MT_3ClickFacing`：
            //   getStateForPlacement 里 CLICK 恒 iconst_0。我们的自动分段会按点击高度写 click=1/2
            //   ⇒ 位移通道一开就整体漂 0.5~1 格（Aki 报的 wallpaint 上半放置错位）。
            b.autoClick(false);
        });
        BlockJS.registerGen("x_click3_f4_vert_suml_auto", b -> {
            b.extraProp(IntProperty.of("click", 0, 2));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("suml", "vertical"));
            b.vertical();
            b.kind("boxed");
        });
        // minia curtain_b：横向 shape + 上下垂直分段 + 含水
        BlockJS.registerGen("x_f4_shape5_vert4_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt");
            b.vertical();
            b.kind("boxed");
        });

        // ---- arch1（Aki 2026-10-28 拆分 minia `*_arch`）：半砖型拱 ----
        //   原 `*_arch` = click0（arch_single，shape 完全无用）+ click1（stair 型）两块合体；
        //   拆开后 arch1 只保留 facing + half + waterlogged（16 变体），放置按点击高度定上下。
        BlockJS.registerGen("x_f4_half_wl", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraDefault(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.BOTTOM);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");   // facing = 玩家朝向（与 arch2 一致，避免 facingFromHit 随机翻 180°）
            b.kind("stairmt");          // C121：官方 TypeArchSingle（vanilla 直梯），不再走盒表
        });

        // ---- arch2：coc 与 minia 两套 shape 语义，几何统一按拱形四段收口 ----
        BlockJS.registerGen("x_f4_half2_shape5_wl_arch2", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.kind("arch2");
        });
        BlockJS.registerGen("x_f4_half2_shape5_wl_mt_arch2", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt");
            b.kind("archmt");   // C121：官方 TypeArch 几何（盒表对这两族不生效）
        });
        BlockJS.registerGen("x_f4_half2_click2_shape5_wl_mt_arch2", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt");
            b.kind("arch2");
        });

        // ---- C9：minia TypeDouble5（cobblestone_b_series）—— variant 0..4 的整方块 ----
        //  官方 MT_Meta5setSlab：`half` + `variant`(META0..META4)，`isDouble()=true` ⇒ half 是死属性（getShape 恒整格、
        //  getStateForPlacement 恒 bottom），所以本地只留 variant；变体只能 /setblock 选（官方放置也恒 META0）。
        BlockJS.registerGen("x_var5", b -> {
            b.extraProp(IntProperty.of("variant", 0, 4));
            b.kind("block");
        });

        // ladder_pool：细梯面碰撞，避免旧模型包围盒形成整块空气墙
        BlockJS.registerGen("x_f4_ladder", b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("ladder");
        });

        // ---- C20：coc `rug_rustic` 的 8 向 facing（官方 facing8 + shift = 16 键）----
        //  官方键：facing=north|east|south|west（贴墙，rug_rustic_wall）+ down_north|down_east|down_south|down_west
        //  （平铺/吊顶，rug_rustic / rug_rustic_true）× shift=false|true。
        //  ★官方 blockstate 里没有 waterlogged★（逐键比过 jar），所以这里也**不加**：
        //  加了就是一个永远为 false、多出 16 个死状态的空维度（#279 的反向形态）。
        BlockJS.registerGen("x_f8_shift", b -> {
            b.extraProp(DynamicEnums.get("face8", "facing"));
            b.extraProp(BooleanProperty.of("shift"));
            b.extraDefault(BooleanProperty.of("shift"), Boolean.FALSE);
            b.placeMode("rug8");        // 贴墙→facing=点击面；地面/吊顶→down_<玩家朝向>
            b.kind("boxed");
        });

        // ---- C15-2 第 5 组：click(3)+facing(4)（minia 铁栅栏斜段 `fence_*_slope` 11 个）----
        //  官方 mt_<id>.json = click 0..2 × facing 4 = 12 键（click 选 斜率/下段/上段三个模型），
        //  本地按 A10 再乘 waterlogged ⇒ 24 键。
        //  ★placeMode 故意留空★：现网这 11 个挂的是 `x_f4`（无 placeMode ⇒ facing = 玩家朝向取反），
        //  新类型必须走同一个分支，否则本批会顺手把栅栏朝向翻 180°（#286 同族）。
        BlockJS.registerGen("x_click3_f4_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 2));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
        });

        // ---- C15-2 第 6 组：click(2)+facing(4)+half(lower|upper)（minia 三角钢琴 `grandpiano_0/1`）----
        //  官方 mt_<id>.json 的 half 是 **lower/upper**（不是 vanilla 的 bottom/top），
        //  所以不能复用 `x_click2_f4_half2`（#283：属性名同、取值集合不同）。
        //  `DynamicEnums.UL` 就是 upper/lower 两值，`ScriptBlock` 已有「half=upper|lower 按点击高度分档」的通用分支。
        BlockJS.registerGen("x_click2_f4_ul_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("ul", "half"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
        });

        // ---- C15-2 第 7 组：click(2)+facing(4)+shape(left|right)（`car_side_*` 3 + `roof_*_edge_*` 45）----
        //  官方类 `MT_2ClickLR`：CLICK 0..1 + HORIZONTAL_FACING + SHAPE(left|right)。
        //  ★碰撞与 click/shape 无关★ —— `getShape` 只按 `FACING.getOpposite()` 返回 4 个静态 AABB
        //  （`UtilAABB.getBlockBounds(dir,0.72,0,0,1,1,1)`，全高 0.72 厚板），所以盒表只需 4 条 `facing=` cond。
        //  ★placeMode 留空★：官方 `getStateForPlacement` = `getHorizontalDirection().getOpposite()`，
        //  与本地无 placeMode 的 `x_f4` 同分支；写 stairlike 会去读不存在的 half（#286 同族）。
        BlockJS.registerGen("x_click2_f4_lr_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("lr", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
        });

        // ---- C15-2 第 8 组：click(4)+position(ne|se|sw|nw)（21 个 `*_vstairs_*`）----
        //  官方基类 `MT_4Position`（POSITION=NE/SE/SW/NW，默认 NE）+ `Type4VStairs` 补 CLICK 0..3；
        //  派生 `TypeWindowDiagonal` 同属性集（但 getShape 随 click 在「斜半板 / 整格」之间切换 ⇒ 斜窗另开一组）。
        //  ★本地用 HORIZONTAL_FACING 表示 position★，映射 ne→east｜se→south｜sw→west｜nw→north
        //    （逐个对照官方 blockstate 的 y 旋转得出：ne=y270、se=y0、sw=y90、nw=y180，与既有 facing 的 y 完全一致），
        //    这样 39 个 id 已有的 `facing=` 盒表 cond 全部继续命中，不必重写盒表。
        //  ★placeMode=vstair★：官方 `getStateForPlacement` 的 case 逐条与本地 `vstairFacing`
        //    （点击点所在象限 → N/S/E/W）同构 —— NORTH 面 x>=0.5→SE、x<=0.5→SW，EAST 面 x>=0.5→NW，逐条验过。
        //  ★click 不进碰撞★：`MT_VHalf.getCollisionBoxList` 只看 FACING+SHAPE，`Type4VStairs` 未覆写 ⇒ 盒表仍是 4 条 facing cond。
        BlockJS.registerGen("x_click4_f4_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("boxed");
            b.placeMode("vstair");
        });

        // ---- C15-2 第 9a 组：click(4)+facing(4)+shape8（12 个 awning_basket_*）----
        //  官方 `TypeAwningBasketConnect extends MT_4ClickFacing`，SHAPE 是自定义 `EnumShape` 8 值：
        //  straight / single / l_straight / r_straight / inner_left / inner_right / outer_left / outer_right
        //  —— 与本地 `DynamicEnums.ROOF8` 取值集合**完全相同**（8 值，域 4×4×8 = 128 键，官方也是 128 键）。
        //  官方**没有 waterlogged**（javap 构造器只 setValue(SHAPE,SINGLE)+setValue(CLICK,0)，#284）。
        //  ★placeMode=stairlike★：官方 `getStateForPlacement` = `getHorizontalDirection()` + click=0 + shape=single，
        //    本地 stairlike 走 `ctx.getHorizontalPlayerFacing()`（同义），无 placeMode 会落到 facingFromHit（随点击近/远半翻 180°）。
        //  ★不写 shapeRule★：官方 SHAPE 由 `getClickShape` 邻居判定；本地默认路径（computeShapeName）也是邻居判定，
        //    产出 straight/inner_*/outer_*（都是 ROOF8 子集，setByName 不会写坏）。
        //    ★偏离备案★：单放时官方给 `single`、本地给 `straight`（与既有 `x_arml_armr_f4_shape5` 同行为，本轮不改换判定）。
        BlockJS.registerGen("x_click4_f4_shape8", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("roof8", "shape"));
            b.placeMode("stairlike");
            b.kind("boxed");
        });

        // ---- C15-2 第 10 组：click(2)+facing(4)+switch(2)（minia 灯具/摆件 9 个）----
        //  官方 javap：`TypeLightTable` / `TypeWallLamp` / `TypeFurnace` 三个类**同父** `MT_LightSwitch2Click2Inventory`，
        //  属性 = FACING(N/S/E/W) + SWITCH(BooleanProperty.create("switch")) + CLICK(0..1)，共 16 键，**无 waterlogged**。
        //  ★发光跟 switch 走★：官方 `getLightEmission` 读 SWITCH（0/15），本地旧类型是 `x_f4_lit`（按 `lighting`）⇒
        //    必须 `lightsFrom(SWITCH,15,0)`，否则换成 switch 之后灯永远不亮（且静态门看不出，只有进世界才暴露）。
        //  ★placeMode 留空★：官方 `getStateForPlacement` = `getHorizontalDirection().getOpposite()` + switch=false + click=0，
        //    与本地 `x_f4` 同分支（stairlike/facingFromHit 都会翻 180°，#286 同族）。
        //  ★switch 默认必须显式 FALSE★：`BooleanProperty` 首值是 TRUE，不定默认态放下就是「开着」。
        BlockJS.registerGen("x_click2_f4_sw", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(SWITCH);
            b.extraDefault(SWITCH, Boolean.FALSE);
            b.lightsFrom(SWITCH, 15, 0);
            b.kind("boxed");
        });

        // ---- ★C15-2 第 11 组（2026-11-01 第 39 轮）：click(4)+facing(4)+shape5+含水（`fence_iron_a_*` 8 + `fence_steel_*` 2）----
        //  官方 javap：10 个 id 全在 `RegisterFence` / `RegisterSteel`，构造类同为
        //  `TypeFenceVHalf extends MT_4ClickVHalf extends MT_VHalf`
        //  属性 = CLICK(0..3) + FACING + SHAPE(`MT_VHalf$EnumShape` = straight/inner_left/inner_right/outer_left/outer_right)
        //  —— 取值集合与 vanilla `Properties.STAIR_SHAPE` **完全相同**（同 5 值），可直接复用；**无 half、无 waterlogged**。
        //  ★碰撞只读 SHAPE + FACING★：`getCollisionBoxList` 只按 shape 分支取常量 AABB，CLICK 不参与
        //  ⇒ cond 写 `facing+shape`（20 条 / id）即可，click 不必进 cond（BoxTable 子集匹配）。
        //  ★本地旧状态多出 half（死属性）★：现网 `half=top` 的 5 个 shape 全指 straight 模型（40 cond 里 20 条是重复盒），
        //  官方没有 half ⇒ 本批一并摘掉；placeMode 沿用 stairlike（facing=玩家朝向，与官方 `getStateForPlacement` 同向）。
        BlockJS.registerGen("x_click4_f4_shape5_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.kind("boxed");
        });

        // ---- ★C15-2 第 13 组（2026-11-01 第 40 轮）：click(4)+facing(4)+shape(single|left|right|middle)（13 个 winframe）----
        //  官方 javap `TypeWinframeTB extends MT_4ClickFacingConnectMetaH extends MT_FacingConnectH`：
        //  属性 = CLICK(0..3) + FACING + SHAPE(`MT_FacingConnectH$EnumShape` = left/middle/right/single)，共 64 键，**无 half、无 waterlogged**。
        //  ★SHAPE 取值集合 == 本地 `DynamicEnums.SLRM`★（single/left/right/middle 四值同名）⇒ 无需新建 enum。
        //  ★碰撞只看 FACING★：`TypeWinframeTB.getShape` 覆写为 `UtilAABB.getAABBPannel(facing)`
        //    （4px 厚整面薄板，全高全宽），CLICK/SHAPE 完全不进碰撞 ⇒ 盒表 4 条 facing cond 足够。
        //  ★placeMode=stairlike★：官方 `getStateForPlacement` = `getHorizontalDirection().getOpposite()` + click=0
        //    （shape 保持默认 SINGLE，随后由 `updateShape` 按左右邻居重算）—— 与本地 `getHorizontalPlayerFacing()` 同向。
        //  ★shapeRule=slrm★：官方 `updateShape` = 左右各判 `canConnectTo`（同方块 + 同 descriptionId + 同 facing）
        //    ⇒ 两侧都连 MIDDLE / 只连一侧 LEFT|RIGHT / 都不连 SINGLE，与本地 `connectShapeAt` 逐条同构。
        BlockJS.registerGen("x_click4_f4_slrm", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("slrm", "shape"));
            b.placeMode("stairlike");
            b.shapeRule("slrm");
            b.kind("boxed");
        });

        // ---- ★C15-2 第 16 组（2026-11-01 第 42 轮）：click(0..1)+facing(4)+shape(8) —— 4 个沙发 ----
        //  官方 `TypeSofa`（javap）：FACING + CLICK(0..1) + SHAPE(TypeSofa$EnumShape 8 值)
        //  = click2×facing4×shape8 = 64 键，**无 waterlogged**，放置 = 水平朝向取反。
        //  ★shape8 不能用 ROOF8★：官方有 `m_straight` 没有 `straight`（#292）⇒ 新建 DynamicEnums.SOFA8。
        //  碰撞（javap）：`getShape` / `getCollisionShape` 都是常量 `UtilAABB.getAABBHalfBottom()`
        //  = AABB(0,0,0,1,0.5,1) ⇒ 盒表改成单条 `""` 通配的 8px 半块，与 click/shape 无关。
        //  shapeRule="sofa"：官方 SHAPE 只由锤子改；本地沿用「邻居自动连接」但把 mt 规则产出的 `straight`
        //  映射成 `single`（= 本地旧观感，且是 SOFA8 合法值；`straight` 不在值集合里会写坏属性）。
        BlockJS.registerGen("x_click2_f4_sofa8_wl", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(DynamicEnums.get("sofa8", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("sofa");
            b.kind("boxed");
        });

        // ---- ★C101（2026-11-04 第 92 轮）：click(0..1)+facing(4)+half(2)+shape(8, 含 m_straight) ----
        //  官方 minia 51 个 `mt_roof_*_stairs_*.json` 逐份实证：128 键 = click2×facing4×half2×shape8，
        //  shape ∈ {single, l_straight, m_straight, r_straight, inner_left/right, outer_left/right}
        //  —— **有 m_straight、没有 straight**（与 molding_half_plate_* 恰好相反：#292 逐族核）。
        //  ★复用 DynamicEnums.SOFA8★：取值集合与官方这份逐字相同（8 值、首个 single），
        //    再建第三个同构枚举只会增加误用面；shapeRule 用新加的 "mt8"（straight→m_straight）。
        //  几何/碰撞全走盒表（kind=boxed），m_straight 与原先 straight 的盒相同（中段直）。
        BlockJS.registerGen("x_click2_f4_half2_mt8", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(DynamicEnums.get("sofa8", "shape"));
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.placeMode("stairlike");
            b.shapeRule("mt8");
            b.kind("boxed");
        });

        // ---- ★C15-2 第 17 组（2026-11-01 第 42 轮）：click(0..7)+switch(2) —— 2 个吸顶/地面 8 向灯 ----
        //  官方 javap `MT_8ClickLightSwitch extends MT_LightSwitch`：CLICK(0..7) + SWITCH
        //  = click8×switch2 = 16 键，**无 facing、无 waterlogged**（旋向由 click 编码）。
        //  ★碰撞 = `Shapes.empty()`★（可穿过），轮廓 `getShape` = 满块（下方是半砖时取半块，静态盒表表达不了邻居，
        //    只落满块）。⇒ `meta.json` 加 `nocol:1` + 盒表 `""` → 满块轮廓，两者配合才等于官方。
        //  ★发光跟 switch 走★（`MT_LightSwitch.getLightEmission` 读 SWITCH）⇒ `lightsFrom(SWITCH,15,0)`；
        //    官方 `getStateForPlacement` 显式 `setValue(SWITCH, TRUE)` ⇒ 默认态必须是 TRUE（`BooleanProperty` 首值也是 TRUE）。
        BlockJS.registerGen("x_click8_sw", b -> {
            b.extraProp(IntProperty.of("click", 0, 7));
            b.extraProp(SWITCH);
            b.extraDefault(SWITCH, Boolean.TRUE);
            b.lightsFrom(SWITCH, 15, 0);
            b.kind("boxed");
        });

        // ---- ★C15-2 第 18 组（2026-11-01 第 43 轮）：click(0..1)+poleshape(8) —— 15 个 `roof_*_hips` ----
        //  官方 javap `TypeRoofHips extends MT_8Position2Click`：CLICK(0..1) + POLESHAPE(n/e/s/w/ne/se/sw/nw)
        //  = click2×poleshape8 = 16 键，**无 facing、无 waterlogged**（#284：官方构造器只 setValue 这两个）。
        //  ★碰撞 = `Shapes.empty()`★（完全可穿过）；轮廓 `getShape` = `UtilAABB.getAABBCarpet()`
        //  = AABB(0, 0.0625, 0, 1, 1, 1)（几乎满块）⇒ `meta.json` 加 `nocol:1`，盒表保留模型反算的梁（轮廓用）。
        //  ★放置★ 官方 `getStateForPlacement`：poleshape = `getHorizontalDirection().getOpposite()`，
        //  `$SwitchMap$Direction` 反解 = NORTH→n / EAST→e / SOUTH→s / WEST→w（斜向 4 值只由锤子循环改）。
        BlockJS.registerGen("x_click2_pole8", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(DynamicEnums.get("pole8", "poleshape"));
            b.placeMode("poleshape");
            b.kind("boxed");
        });

        // ---- ★C34（2026-11-01 第 43 轮第 2 批）：click(0..3)+facing(4)+lighting(2) —— 3 个 traffic_lights ----
        //  官方 javap `TypeWallArm extends MT_4ClickFacing`：CLICK(0..3) + FACING(4) = 16 键，无 lighting。
        //  ★本地保留 `lighting` 开关★（既有 `x_f4_lit` 语义：右击切换 / `lightsFrom`）⇒ 16 × 2 = 32 键，
        //  必乘（#279：只镜像 16 键 ⇒ 一半状态没有 variant）。
        //  ★碰撞★ `getCollisionShape = Shapes.empty()`、`getShape = FULL_BLOCK_AABB`（满块轮廓）
        //  ⇒ 盒表 `""` = 满块 + `meta.json` `nocol:1`（与 C32 同处理）。
        BlockJS.registerGen("x_click4_f4_lit", b -> {
            b.extraProp(IntProperty.of("click", 0, 3));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(LIGHTING);
            b.lightsFrom(LIGHTING, 15, 0);
            b.kind("boxed");
        });

        // ---- ★C36（2026-11-01 第 44 轮）：click(0..7)+half(top|bottom) —— 9 个 minia 8ClickUpDown 摆件 ----
        //  官方 javap：8exteriors / 8leaves_a / 8leaves_b / barrel / model_urn_a / model_urn_b / statue_0
        //  直接是 `new MT_8ClickUpDown8Inventory(...)`；pot / pot_b 是 `TypePot extends MT_8ClickUpDown8Inventory`
        //  （**无任何覆写**）⇒ 同一个属性集：CLICK(0..7) + HALF(`MT_Slab$EnumHalf` = top|bottom) = 16 键，
        //  **无 facing、无 waterlogged**（#284：构造器只 setValue 这两个）。
        //  ★不复用 `click8_half_ul`★：那个 half 是 `upper|lower`（8doubleplants），取值集合与官方 `top|bottom` 不同（#292）。
        //  官方 HALF 就是 vanilla `BlockHalf`（top/bottom 两值）⇒ 直接用 `Properties.BLOCK_HALF`，不新建 enum。
        //  ★默认态★ 官方 registerDefaultState = HALF.TOP + click 0，但它的 HALF 在 `getShape`/`updateShape` 里
        //    **按邻居重算**（`getHalfShape`：看下方是不是 isUpDown / isHalfSlab 再翻）——静态属性表达不了。
        //    本地取 **BOTTOM** 作默认：落地摆放时碰撞 = 8px 半块（与官方绝大多数情形一致），避免"所有摆件都变整格"。
        //  ★碰撞（官方常量）★ half=top ⇒ FULL_BLOCK_AABB；half=bottom ⇒ `UtilAABB.getAABBHalfBottom()`（8px）
        //    ⇒ 盒表每个 id 两条 cond（top/bottom），click 不进碰撞。
        BlockJS.registerGen("x_click8_half_bt", b -> {
            b.extraProp(IntProperty.of("click", 0, 7));
            b.extraProp(Properties.BLOCK_HALF);
            b.extraDefault(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.BOTTOM);
            b.kind("boxed");
        });

        // ---- ★C38（2026-11-01 第 45 轮）：click(0..1) + facing + half + shape(single|bottom|middle|top)★ ----
        //  官方 `TypeBarrelHorizontalConnectIDV extends TypeBarrelHorizontal`（`stackplants_0`）：
        //    CLICK0..1 × FACING4 × HALF2 × SHAPE4 = 64 键，**无 waterlogged**（#284）。
        //    SHAPE 取值 = `single|bottom|middle|top` = 现有的 `DynamicEnums.VSEC`（与竖直分段家具同一枚举）。
        //  碰撞继承 `TypeBarrelHorizontal.getShape`：half=top ⇒ 满块、half=bottom ⇒ 8px（click / shape 不进碰撞）。
        //  ★placeMode=vconnect_self★：官方放置用 `getHorizontalDirection()`（玩家朝向本身，**不取反**），
        //    而现有 `vconnect` 是取反版（MT_FacingConnectV 系列）⇒ 为这一族单独加一个分支。
        BlockJS.registerGen("x_click2_f4_half2_vsec4", b -> {
            b.extraProp(IntProperty.of("click", 0, 1));
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(DynamicEnums.get("vsec", "shape"));
            b.placeMode("vconnect_self");
            b.kind("boxed");
        });

        // ---- ★C68（2026-11-03 第 66 轮）：coc 树枝 branch_{acacia,birch,darkoak,jungle,oak,spruce}★ ----
        //  javap `com.kixiko.cocricot.blocks.LogBranch`：8 个 BooleanProperty
        //    `north/east/south/west` + `trunk_north/trunk_east/trunk_south/trunk_west`，
        //    `registerDefaultState` **全部 false**（★BooleanProperty 首值是 TRUE，必须逐个 extraDefault★）。
        //  连接谓词（官方 `canConnectTo`）：`isLogBlock(nb) || (nb 非空气 && isSolidRender)`；
        //    `isLogBlock` = 同一树枝 block || 原版 6 种原木 || coc `log_all_*`；`trunk_*` = coc `trunk_*`（独立于 connected）。
        //  几何：`getShape` 与 `getCollisionShape` **同一个常量** `cuboid(.25,0,.25,.75,1,.75)` ⇒ kind=branchcol，
        //    **不进 boxes.json**（几何与 8 个属性无关，写盒表会被 `_c39` 当死 cond）。
        //  blockstate 用官方 9-part multipart（1 底座 + 4 side + 4 side_trunk），256 状态全由 part 并集覆盖。
        BlockJS.registerGen("x_branch8", b -> {
            b.extraProp(Properties.NORTH);
            b.extraProp(Properties.EAST);
            b.extraProp(Properties.SOUTH);
            b.extraProp(Properties.WEST);
            b.extraProp(TRUNK_NORTH);
            b.extraProp(TRUNK_EAST);
            b.extraProp(TRUNK_SOUTH);
            b.extraProp(TRUNK_WEST);
            b.extraDefault(Properties.NORTH, Boolean.FALSE);
            b.extraDefault(Properties.EAST, Boolean.FALSE);
            b.extraDefault(Properties.SOUTH, Boolean.FALSE);
            b.extraDefault(Properties.WEST, Boolean.FALSE);
            b.extraDefault(TRUNK_NORTH, Boolean.FALSE);
            b.extraDefault(TRUNK_EAST, Boolean.FALSE);
            b.extraDefault(TRUNK_SOUTH, Boolean.FALSE);
            b.extraDefault(TRUNK_WEST, Boolean.FALSE);
            b.placeMode("branch");
            b.kind("branchcol");
        });

        // ---- ★C102（第 87 轮）minia 杆件竖向分段★ ----
        //  官方 `MT_9PositionConnectMetaV.updateShape`（javap）：
        //    up   = 上方是同一 id
        //    down = 下方是同一 id
        //    up&&down ⇒ VMIDDLE / 仅 down ⇒ BOTTOM?? —— 逐条读下来是：
        //      up&&down → VMIDDLE ; up&&!down → BOTTOM ; !up&&down → TOP ; 否则 SINGLE
        //  与本地 {@code ScriptBlock#setVerticalForPlacement} 的
        //    (up&&down→2, down→1=upper, up→3=lower, 否则 0=single) 一一对应：
        //      vertical 0=single / 1=upper(=官方 top) / 2=middle(=官方 vmiddle) / 3=lower(=官方 bottom)
        //  ⇒ bed_pole_*、wallpost_molded_9position_* 直接用这个类型（8 个 id）。
        BlockJS.registerGen("x_vert4_auto", b -> {
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.vertical();
            b.kind("boxed");
        });
        //  官方 `MT_Meta3setConnectVFence`（javap）：NSEW 四个 BooleanProperty + SHAPE 四值
        //    + `MT_Meta3set.VARIANT`（meta0..2，但 getStateForPlacement **永远写 META0** ⇒ 后两值不可达，丢弃）。
        //  NSEW 走 ScriptBlock 的通用重算（`connectsTo` 同 id/同 family），加 `nsewSameOnly()` 不粘墙
        //  —— 官方 `canFenceConnectTo` 也只认同种栏板。⇒ wallpost_molded_{stone,sandstone,white_stone} 3 个 id。
        BlockJS.registerGen("x_nsew_vert4_auto", b -> {
            b.extraProp(Properties.NORTH);
            b.extraProp(Properties.EAST);
            b.extraProp(Properties.SOUTH);
            b.extraProp(Properties.WEST);
            b.extraProp(IntProperty.of("vertical", 0, 3));
            b.extraDefault(Properties.NORTH, Boolean.FALSE);
            b.extraDefault(Properties.EAST, Boolean.FALSE);
            b.extraDefault(Properties.SOUTH, Boolean.FALSE);
            b.extraDefault(Properties.WEST, Boolean.FALSE);
            b.nsewSameOnly();
            b.vertical();
            b.kind("boxed");
        });
    }
}
