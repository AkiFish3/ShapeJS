package com.akira.blockjs;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * BlockJS —— 给 KubeJS 提供「按 blockstate 动态形状/亮度/遮光/红石/活塞行为 +
 * 多方块足迹」的附属 mod。
 *
 * <p>入口做两件事：注册多方块用的隐形占位方块 {@code blockjs:extension}（不注册物品），
 * 打印一行加载日志。真正的逻辑在 mixin 与各 Dynamic* / Multiblocks 类。
 */
public class BlockJS implements ModInitializer {

    /**
     * 多方块隐形占位：不渲染（INVISIBLE）、无粒子、无掉落、活塞一碰即碎（触发级联）。
     * 强度 0.3：玩家可秒拆。nonOpaque 防止它挡光/触发遮挡剔除。
     */
    public static final ExtensionBlock EXTENSION = new ExtensionBlock(
            AbstractBlock.Settings.create()
                    .strength(0.3F)
                    .nonOpaque()
                    .noBlockBreakParticles()
                    .dropsNothing()
                    .pistonBehavior(PistonBehavior.DESTROY));

    /** 自定义容器 BE 的方块集合（创建方块后填进来；BlockEntityType 构造可接收可变集合） */
    public static final java.util.Set<net.minecraft.block.Block> CONTAINER_BLOCKS = new java.util.HashSet<>();

    /** 玻璃台阶的"偏移"开关：cocricot 的 blockstate 用 `shift=true/false` 切换模型。
     *  ★必须是单例常量★：extraProp 与 extraDefault 传同一个对象，默认值才会生效。 */
    public static final BooleanProperty SHIFT = BooleanProperty.of("shift");

    /** 容器的 BlockEntityType：不带任何 BER ⇒ 外观完全由模型决定 */
    public static final net.minecraft.block.entity.BlockEntityType<ScriptChestBlockEntity> CONTAINER_CHEST_TYPE =
            new net.minecraft.block.entity.BlockEntityType<>(ScriptChestBlockEntity::new, CONTAINER_BLOCKS, null);

    // 大柜子 / 桶 / 床 / 告示牌各要一个独立 type：
    // ① 工厂不同（大柜子要 54 格列表），一个 type 只能有一个工厂；
    // ② 原版 BARREL / BED / SIGN 三个 type 在客户端都注册了 BER，共用一个 type 就会把 BER 带回来。
    public static final java.util.Set<net.minecraft.block.Block> BIGCABINET_BLOCKS = new java.util.HashSet<>();
    public static final net.minecraft.block.entity.BlockEntityType<ScriptBigCabinetBlockEntity> CONTAINER_BIGCABINET_TYPE =
            new net.minecraft.block.entity.BlockEntityType<>(ScriptBigCabinetBlockEntity::new, BIGCABINET_BLOCKS, null);

    public static final java.util.Set<net.minecraft.block.Block> BARREL_BLOCKS = new java.util.HashSet<>();
    public static final net.minecraft.block.entity.BlockEntityType<ScriptBarrelBlockEntity> CONTAINER_BARREL_TYPE =
            new net.minecraft.block.entity.BlockEntityType<>(ScriptBarrelBlockEntity::new, BARREL_BLOCKS, null);

    public static final java.util.Set<net.minecraft.block.Block> BED_BLOCKS = new java.util.HashSet<>();
    public static final net.minecraft.block.entity.BlockEntityType<ScriptBedBlockEntity> CONTAINER_BED_TYPE =
            new net.minecraft.block.entity.BlockEntityType<>(ScriptBedBlockEntity::new, BED_BLOCKS, null);

    public static final java.util.Set<net.minecraft.block.Block> SIGN_BLOCKS = new java.util.HashSet<>();
    public static final net.minecraft.block.entity.BlockEntityType<ScriptSignBlockEntity> CONTAINER_SIGN_TYPE =
            new net.minecraft.block.entity.BlockEntityType<>(ScriptSignBlockEntity::new, SIGN_BLOCKS, null);

    /** 演示流体：快（tickRate 5 / dropOff 1）与稠（tickRate 30 / dropOff 3） */
    public static final ScriptFluid FLUID_FAST = new ScriptFluid(5, 1);
    public static final ScriptFluid FLUID_THICK = new ScriptFluid(30, 3);

    /** 形状锤（手持切换方块属性） */
    public static final ShapeHammerItem SHAPE_HAMMER = new ShapeHammerItem(
            new net.minecraft.item.Item.Settings().maxCount(1));

    @Override
    public void onInitialize() {
        // /blockjs probe —— 对着方块打印 id/属性/碰撞盒/meta（C12，2026-10-29）
        ProbeCommand.register();

        Registry.register(Registries.BLOCK, new Identifier(DynamicShapes.MOD_ID, "extension"), EXTENSION);
        Registry.register(Registries.ITEM, new Identifier(DynamicShapes.MOD_ID, "shape_hammer"), SHAPE_HAMMER);

        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(DynamicShapes.MOD_ID, "script_container"), CONTAINER_CHEST_TYPE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(DynamicShapes.MOD_ID, "script_bigcabinet"), CONTAINER_BIGCABINET_TYPE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(DynamicShapes.MOD_ID, "script_barrel"), CONTAINER_BARREL_TYPE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(DynamicShapes.MOD_ID, "script_bed"), CONTAINER_BED_TYPE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE,
                new Identifier(DynamicShapes.MOD_ID, "script_sign"), CONTAINER_SIGN_TYPE);

        // 流体：fluid + 对应的流体方块
        registerFluid("fluid_fast", FLUID_FAST);
        registerFluid("fluid_thick", FLUID_THICK);

        // 左键：切第二个可切属性（右键切第一个）
        // 锤子：左键只切换属性，不破坏方块（等同创造模式拿剑）
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!player.getStackInHand(hand).isOf(SHAPE_HAMMER)) return net.minecraft.util.ActionResult.PASS;
            if (!world.isClient) {
                ShapeHammerItem.cycle(world, pos, player, 1, player.isSneaking());
            }
            return net.minecraft.util.ActionResult.SUCCESS;   // 直接吃掉，不开始挖掘
        });
        // 兜底：已经在挖的时刻也取消（防止挖断）
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
                (world, player, pos, state, entity) -> !player.getMainHandStack().isOf(SHAPE_HAMMER));
        // 右键开关：抢在 KubeJS / 原版 onUse 之前（开关、窗帘整列联动）
        // lighting（灯）只在空手时切 —— 手上有方块时放行，否则「左手拿灯右键另一个灯」会变成切开关。
        // ★"空手"按【正在用的那只手】算★（2026-10-28 Aki：百叶窗/扇窗空手打不开）。
        //   原来要求主副手**都**空，副手拿火把/方块时右键百叶窗就毫无反应；
        //   现在主手空即算空手，手持方块仍然是"放置优先"。
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            net.minecraft.block.BlockState st = world.getBlockState(hit.getBlockPos());
            if (!(st.getBlock() instanceof ScriptBlock)) return net.minecraft.util.ActionResult.PASS;
            // ★variant 循环★（Aki 2026-10-28）：手持**该方块本身** + 潜行 + 右键 ⇒ 原地切 variant，
            //   不放置新块。facade 的 meta0..4 之前只有 meta0 能落地，其余 4 档进不去。
            if (player.isSneaking()) {
                net.minecraft.item.ItemStack hs = player.getStackInHand(hand);
                if (!hs.isEmpty() && hs.getItem() instanceof net.minecraft.item.BlockItem bi
                        && bi.getBlock() == st.getBlock()
                        && ScriptBlock.cycleNamedProp(world, hit.getBlockPos(), st, "variant", "mtype")) {
                    player.swingHand(hand);
                    return net.minecraft.util.ActionResult.SUCCESS;
                }
            }
            boolean emptyHand = player.getStackInHand(hand).isEmpty();
            if (!ScriptBlock.toggleFlag(world, hit.getBlockPos(), st, emptyHand)) {
                return net.minecraft.util.ActionResult.PASS;
            }
            // ★手臂摆动★：回调抢在服务端 onUse 之前返回 SUCCESS，原版那条挥手路径不会跑
            //   ⇒ 手持剑/镐时能开关但"没动画"（Aki 2026-10-28）。这里补一次。
            player.swingHand(hand);
            return net.minecraft.util.ActionResult.SUCCESS;
        });

        // ★手持切形态（放置前）★（Aki 2026-10-28）：手持脚本方块 + 潜行 + 右键**对空**
        //   ⇒ 在 stack 的 BlockStateTag 里轮换下一个形态，之后落地就是那个形态（不放置、不耗材）。
        //   只在「没点到方块」时触发：对着方块右键，原版先走 interactBlock 直接放置，根本走不到这里。
        //   ⇒ 建造时该放的照放，想换形态就抬头对空 Shift+右键。
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            net.minecraft.item.ItemStack st = player.getStackInHand(hand);
            if (!player.isSneaking() || !ShapeHammerItem.cycleStack(st, false)) {
                return net.minecraft.util.TypedActionResult.pass(st);
            }
            player.swingHand(hand);
            return net.minecraft.util.TypedActionResult.success(st);
        });

        // ── 自定义方块类型 ────────────────────────────────────────────
        // 形状差异全部由 属性组合 + blockstate 模型 表达，Java 侧只有两个类：
        //   cocstair  = 继承 StairsBlock（自带 facing/half/shape/waterlogged + 放置即定形）
        //   coc*      = 通用 ScriptBlock（属性可配 + 真含水 + 放置即定朝向）
        registerBlockType("stair",     ScriptStairBlockBuilder.class, null);
        registerBlockType("generic",   ScriptBlockBuilder.class, b -> b.kind("block"));
        // 搬运用：只有朝向 / 只有轴向，但外观仍是整块（kind=block 把碰撞锁成满立方，
        // 否则会落进 facing 分支算出楼梯形 —— 竖楼梯那个 bug 的同源问题）
        registerBlockType("cardinal",  ScriptBlockBuilder.class, b -> { b.extraProp(Properties.HORIZONTAL_FACING); b.kind("block"); });
        registerBlockType("axis",      ScriptBlockBuilder.class, b -> { b.extraProp(Properties.AXIS); b.kind("block"); });
        // 半块：天窗 / 斜窗（`*_skylight_half`、`window_slanted_*`）。
        // 属性与 blockstate 完全一致，几何按 half 切上下两半（旧版 BLOCKJS_RULES 的同一组盒）。
        // 不能复用 `stair`：StairsBlock 的碰撞是阶梯形，天窗实际是整块的一半。
        registerBlockType("halfslab",  ScriptBlockBuilder.class, b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("halfslab");
            // ★stairlike★ 放置朝向按原版楼梯（玩家朝向取反），与点击落在方块的哪一半无关。
            //   以前落到 facingFromHit ⇒ 同一朝向点近半/远半给相差 180° 的 facing，
            //   shape 再按错的朝向算 ⇒ 天窗拐角时有时无、inner/outer 反了（2026-10-27）。
            b.placeMode("stairlike");
        });
        registerBlockType("halfslab_nw", ScriptBlockBuilder.class, b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraProp(Properties.STAIR_SHAPE);
            b.kind("halfslab");
            b.placeMode("stairlike");
        });
        // 整块 + 形状连接：斜窗中段（`window_slanted_middle_*`）。
        // 判据是模型不是名字：`awning_b_*` 的 OBJ 顶点 y 上下界 0.0~1.0（满高），
        // `awning_*`（边段）只有 0.0~0.5。旧 blockstate 里这族的 half=bottom / half=top
        // 指向同一个模型同一个 y（20 对逐条比对，0 差异）⇒ half 是死属性，去掉；
        // facing + shape 保留（转角/内外的连接要用）。几何走 kind=block → 满立方碰撞。
        registerBlockType("fullshape", ScriptBlockBuilder.class, b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.STAIR_SHAPE);
            b.extraProp(Properties.WATERLOGGED);
            b.waterloggable();
            b.kind("block");
            b.placeMode("stairlike");   // 斜窗中段同样是楼梯型朝向（见 halfslab 的注释）
        });

        // ── 第 6/7 批：模型包围盒族（盒来自 boxes.json，键为空串 ⇒ 全状态共用一格盒）──
        // 这批的几何一律取「模型包围盒」，不按属性算 —— 因为模型是作者手摆的，
        // 按 facing/shape 推出来的盒与视觉对不上（线脚/挂钩/地毯尤其明显）。
        // NSEW + waterlogged，但外观是整块（树干/理发店柱）：碰撞走满立方，模型不动。
        registerBlockType("nsew_full", ScriptBlockBuilder.class, b -> {
            addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
            b.waterloggable();
            b.kind("block");
        });
        // NSEW（无 waterlogged）：矮桌等
        registerBlockType("nsew_box", ScriptBlockBuilder.class, b -> {
            addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
            b.kind("boxed");
        });
        // NSEW + waterlogged：挂钩/立面/餐桌腿
        registerBlockType("nsew_wl_box", ScriptBlockBuilder.class, b -> {
            addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
            b.waterloggable();
            b.kind("boxed");
        });
        // NSEW + shift（圆形地毯：贴地/上移半格）
        registerBlockType("nsew_shift_box", ScriptBlockBuilder.class, b -> {
            addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
            b.extraProp(BooleanProperty.of("shift"));
            b.extraDefault(BooleanProperty.of("shift"), Boolean.FALSE);
            b.kind("boxed");
        });
        // NSEW + on（地毯"叠在台阶上"）
        registerBlockType("nsew_on_box", ScriptBlockBuilder.class, b -> {
            addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
            b.extraProp(BooleanProperty.of("on"));
            b.extraDefault(BooleanProperty.of("on"), Boolean.FALSE);
            b.kind("boxed");
        });
        // 只有 facing 的贴地/贴墙件（地毯角块等）
        registerBlockType("facing_box", ScriptBlockBuilder.class, b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.kind("boxed");
        });
        // 单态装饰（一个状态一个模型）
        registerBlockType("single_box", ScriptBlockBuilder.class, b -> b.kind("boxed"));
        // 木栏杆连接件：facing + fence(布尔) + shape(0..3 整数)
        // ★shape 是 0/1/2/3 四个取值★，用 IntProperty 而不是 StairShape 枚举
        //   （blockstate 里就写 shape=0，枚举名不能以数字开头）
        registerBlockType("face_fence_shape", ScriptBlockBuilder.class, b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            BooleanProperty fence = BooleanProperty.of("fence");
            b.extraProp(fence);
            b.extraDefault(fence, Boolean.FALSE);
            b.extraProp(IntProperty.of("shape", 0, 3));
            b.kind("boxed");
        });
        // ★地毯楼梯（carpet_block_gorgeous_*_stair）★：只有 facing + half。
        // 资源里没有 inner/outer 拐角模型 ⇒ 不做 vanilla stairs（80 变体），否则拐角会画成直梯。
        registerBlockType("carpet_stair", ScriptBlockBuilder.class, b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(Properties.BLOCK_HALF);
            b.extraDefault(Properties.BLOCK_HALF, BlockHalf.BOTTOM);
            b.waterloggable();
            b.placeMode("carpetstair");
            b.kind("boxed");
        });
        registerBlockType("vstair",    ScriptBlockBuilder.class, b -> { presetVstair(b); b.kind("boxed"); b.placeMode("vstair"); });
        registerBlockType("vslab",     ScriptBlockBuilder.class, b -> { presetVslab(b); b.kind("boxed"); b.placeMode("vslab"); });
        registerBlockType("bar",       ScriptBlockBuilder.class, b -> { presetAxis(b); b.crossSize(8); });
        registerBlockType("rod",       ScriptBlockBuilder.class, b -> { presetAxis(b); b.crossSize(4); });
        registerBlockType("beam",      ScriptBlockBuilder.class, BlockJS::presetBeam);
        registerBlockType("style",     ScriptBlockBuilder.class, b -> { presetStyle(b); b.kind("plate"); });
        registerBlockType("layered",   ScriptBlockBuilder.class, b -> { presetLayered(b, 4); b.kind("layered"); });
        registerBlockType("layer4",    ScriptBlockBuilder.class, b -> { presetLayered(b, 4); b.kind("layeredface"); });
        registerBlockType("layer8",    ScriptBlockBuilder.class, b -> { presetLayered(b, 8); b.kind("layeredface"); });
        registerBlockType("connect",   ScriptBlockBuilder.class, BlockJS::presetConnect);
        registerBlockType("pane",      ScriptBlockBuilder.class, BlockJS::presetPane);
        registerBlockType("frame",     ScriptBlockBuilder.class, b -> { presetFrame(b); b.kind("frame"); });
        registerBlockType("arch",      ScriptBlockBuilder.class, b -> { presetArch(b); b.kind("arch"); });
        registerBlockType("counter",   ScriptBlockBuilder.class, b -> { presetCounter(b); b.kind("block"); });
        registerBlockType("curtain",   ScriptBlockBuilder.class, b -> { presetCurtain(b); b.kind("curtain"); });
        registerBlockType("rug",       ScriptBlockBuilder.class, b -> { presetRug(b); b.kind("rug"); });
        registerBlockType("seat",      ScriptBlockBuilder.class, b -> { presetSeat(b); b.kind("seat"); });
        registerBlockType("variant",   ScriptBlockBuilder.class, b -> { presetVariant(b); b.kind("plate"); });
        registerBlockType("flowerpot",  ScriptBlockBuilder.class, b -> { presetFlower(b); b.kind("flower"); });
        registerBlockType("sofa",      ScriptBlockBuilder.class, b -> { presetSofa(b); b.kind("sofa"); });
        registerBlockType("vertical",  ScriptBlockBuilder.class, b -> { presetVertical(b); b.kind("plate"); });
        registerBlockType("click",     ScriptBlockBuilder.class, b -> { presetClick(b); b.kind("plate"); });
        registerBlockType("switch",    ScriptBlockBuilder.class, b -> { presetSwitch(b); b.kind("plate"); });
        registerBlockType("water",     ScriptBlockBuilder.class, b -> { presetWater(b); b.kind("plate"); });
        registerBlockType("sand",      ScriptBlockBuilder.class, b -> { presetSand(b); b.kind("plate"); });
        registerBlockType("separate",  ScriptBlockBuilder.class, b -> { presetSeparate(b); b.kind("plate"); });
        registerBlockType("pole",      ScriptBlockBuilder.class, b -> { presetPole(b); b.kind("pole"); });
        registerBlockType("lighting",  ScriptBlockBuilder.class, b -> { presetLighting(b); b.kind("lamp"); });
        registerBlockType("double",    ScriptBlockBuilder.class, b -> { presetDouble(b); b.kind("doubleside"); });
        registerBlockType("arm",       ScriptBlockBuilder.class, b -> { presetArm(b); b.kind("arm"); });
        registerBlockType("vshift",    ScriptBlockBuilder.class, b -> { presetVshift(b); b.kind("plate4"); });
        registerBlockType("table",     ScriptBlockBuilder.class, b -> { presetTable(b); b.kind("table"); });
        registerBlockType("slope",     ScriptBlockBuilder.class, b -> { presetSlope(b); b.kind("slope"); });
        registerBlockType("cylinder",  ScriptBlockBuilder.class, b -> { presetCylinder(b); b.kind("cylinder"); });
        registerBlockType("quarter8",  ScriptBlockBuilder.class, b -> { presetStep(b); b.kind("strip"); b.crossSize(8); });
        registerBlockType("quarter4",  ScriptBlockBuilder.class, b -> { presetStep(b); b.kind("strip"); b.crossSize(4); });
        registerBlockType("quarter3",  ScriptBlockBuilder.class, b -> { presetStep(b); b.kind("strip"); b.crossSize(3); });
        registerBlockType("quarter2",  ScriptBlockBuilder.class, b -> { presetStep(b); b.kind("strip"); b.crossSize(2); });
        registerBlockType("panel",     ScriptBlockBuilder.class, BlockJS::presetPanel);
        registerBlockType("lattice",   ScriptBlockBuilder.class, BlockJS::presetLattice);
        registerBlockType("shiftset",  ScriptBlockBuilder.class, b -> { presetShiftset(b); b.kind("plate"); });
        registerBlockType("plank",     ScriptBlockBuilder.class, b -> { presetPlank(b); b.kind("plate4"); });
        registerBlockType("threeset",  ScriptBlockBuilder.class, b -> { presetThreeset(b); b.kind("plate"); });
        registerBlockType("sheet",     ScriptBlockBuilder.class, BlockJS::presetSheet);

        // 原版类派生：门 / 活板门（KubeJS 内置没有这两种）
        registerBlockType("bed",      ScriptBedBlockBuilder.class, null);
        registerBlockType("chest",    ScriptChestBlockBuilder.class, null);
        registerBlockType("bigcabinet", ScriptBigCabinetBlockBuilder.class, null);
        registerBlockType("barrel",   ScriptBarrelBlockBuilder.class, null);
        registerBlockType("sign",     ScriptSignBlockBuilder.class, null);
        registerBlockType("crafting", ScriptCraftingBlockBuilder.class, null);
        registerBlockType("door",     ScriptDoorBlockBuilder.class, null);
        registerBlockType("trapdoor", ScriptTrapdoorBlockBuilder.class, null);

        // shapejs 全接管：把 KubeJS 内置那批也换成我们自己的实现（支持追加属性）
        registerBlockType("slab",           ScriptSlabBlockBuilder.class, null);
        // 玻璃台阶：facing + shift + type + waterlogged（cocricot 的 `glass_*_slab` 一族）
        registerSlabType("slab_shift",     ScriptSlabBlockBuilder.class, b -> {
            b.extraProp(Properties.HORIZONTAL_FACING);
            b.extraProp(SHIFT);
            b.extraDefault(SHIFT, Boolean.FALSE);
        });
        // 木板台阶：axis + type（cocricot 的 `wood_*_slab` 一族）
        registerSlabType("slab_axis",      ScriptSlabBlockBuilder.class, b -> b.extraProp(Properties.AXIS));
        registerBlockType("wall",           ScriptWallBlockBuilder.class, null);
        registerBlockType("fence",          ScriptFenceBlockBuilder.class, null);
        registerBlockType("fence_gate",     ScriptFenceGateBlockBuilder.class, null);
        registerBlockType("button",         ScriptButtonBlockBuilder.class, null);
        registerBlockType("pressure_plate", ScriptPressurePlateBlockBuilder.class, null);
        registerBlockType("carpet",         ScriptCarpetBlockBuilder.class, null);
        // P3~P9：按属性签名批量生成的类型（GeneratedTypes.java）
        GeneratedTypes.register();
        ExtraTypes.register();
        // 盒表预热（Aki 2026-10-26）：懒加载会在第一次碰到 boxed 方块时卡一下；
        // 主动加载一次，日志也能证明解析成功（失败会写"盒表加载失败"）。
        BoxTable.warmup();
        DynamicShapes.LOG.info("[BlockJS] 动态方块扩展已加载（dynShape/dynLight/dynOpacity/dynPower/dynPiston/dynFootprint + 运行时改写 API）");
    }


    // ------------------------------------------------------- 类型预设（标准属性由 Java 提供，脚本不用自己 new）

    /** 竖楼梯：facing + waterlogged（8 变体） */
    private static void presetVstair(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.waterloggable();
    }

    /** 竖台阶：facing + shape(5) + waterlogged（40 变体） */
    private static void presetVslab(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(EnumProperty.of("shape", StairShape.class));
        b.waterloggable();
    }

    /** 粗条 / 细杆：axis + waterlogged */
    private static void presetAxis(ScriptBlockBuilder b) {
        b.extraProp(Properties.AXIS);
        b.waterloggable();
    }

    private static void presetPlank(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("slab"));
        b.extraDefault(BooleanProperty.of("slab"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetThreeset(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("shift"));
        b.extraDefault(BooleanProperty.of("shift"), Boolean.FALSE);
        b.extraProp(EnumProperty.of("type", Segment.class));
        b.waterloggable();
    }

    private static void presetPanel(ScriptBlockBuilder b) {
        addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
        b.extraProp(BooleanProperty.of("shift"));
        b.extraDefault(BooleanProperty.of("shift"), Boolean.FALSE);
        b.extraDefault(BooleanProperty.of("shift"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetLattice(ScriptBlockBuilder b) {
        addSidesFalse(b, Properties.UP, Properties.DOWN,
                Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
    }

    private static void presetShiftset(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("shift"));
        b.extraDefault(BooleanProperty.of("shift"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetSlope(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.waterloggable();
    }

    private static void presetCylinder(ScriptBlockBuilder b) {
        b.extraProp(Properties.AXIS);
        b.waterloggable();
    }

    /** 角块 / 细条台阶：facing + shape（楼梯属性）+ waterlogged */
    private static void presetStep(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(EnumProperty.of("shape", StairShape.class));
        b.waterloggable();
    }

    /** 双款式朝向件：facing + mtshape(0..4) + waterlogged */
    private static void presetStyle(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("mtshape", 0, 4));
        b.waterloggable();
    }

    /** 薄片：六向 facing + waterlogged */
    private static void presetSheet(ScriptBlockBuilder b) {
        b.extraProp(Properties.FACING);
        b.waterloggable();
    }

    /** 连接类：NSEW + waterlogged */
    private static void presetConnect(ScriptBlockBuilder b) {
        addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
        b.waterloggable();
    }

    /** 竖杆：facing + position(0..8) */
    private static void presetPole(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("position", 0, 8));
        b.waterloggable();
    }

    /** 灯：facing + lighting(bool) */
    private static void presetLighting(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        BooleanProperty lit = BooleanProperty.of("lighting");
        b.extraProp(lit);
        b.extraDefault(lit, Boolean.TRUE);      // ★同一个实例★：不同实例会让 defaults 查不到、回落首值
        b.lightsFrom(lit, 15, 0);               // 官方语义：lighting ? 15 : 0（逐状态求值）
        b.waterloggable();
    }

    /** 双面件：facing + double(bool) */
    private static void presetDouble(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("double"));
        b.extraDefault(BooleanProperty.of("double"), Boolean.FALSE);
        b.waterloggable();
    }

    /** 带左右臂：facing + arm_left + arm_right */
    private static void presetArm(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("arm_left"));
        b.extraDefault(BooleanProperty.of("arm_left"), Boolean.FALSE);
        b.extraProp(BooleanProperty.of("arm_right"));
        b.extraDefault(BooleanProperty.of("arm_right"), Boolean.FALSE);
        b.waterloggable();
    }

    /** 毯类变体：facing + carpet(bool) */
    private static void presetMat(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("carpet"));
        b.extraDefault(BooleanProperty.of("carpet"), Boolean.FALSE);
    }

    /** 竖偏移：facing + shape + vshift(bool) + waterlogged */
    private static void presetVshift(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(EnumProperty.of("shape", StairShape.class));
        b.extraProp(BooleanProperty.of("vshift"));
        b.extraDefault(BooleanProperty.of("vshift"), Boolean.FALSE);
        b.waterloggable();
    }

    /** 茶几：facing + coffeetable(bool) + waterlogged */
    private static void presetTable(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("coffeetable"));
        b.extraDefault(BooleanProperty.of("coffeetable"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetSofa(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("sofa", 0, 9));
        b.waterloggable();
    }

    private static void presetVertical(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("vertical", 0, 3));
        b.waterloggable();
    }

    private static void presetClick(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("click", 0, 7));
    }

    private static void presetSwitch(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("switch"));
        b.extraDefault(BooleanProperty.of("switch"), Boolean.FALSE);
    }

    private static void presetWater(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("water"));
        b.extraDefault(BooleanProperty.of("water"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetSand(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("sand"));
        b.extraDefault(BooleanProperty.of("sand"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetSeparate(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("separate"));
        b.extraDefault(BooleanProperty.of("separate"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetCounter(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(EnumProperty.of("type", Segment.class));
        b.waterloggable();
    }

    private static void presetCurtain(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(Properties.SLAB_TYPE);      // 复用 bottom/top/double 表示 下段/上段/整幅
        b.extraProp(BooleanProperty.of("open")); // true = 收起（只留顶部一条）
        b.extraDefault(BooleanProperty.of("open"), Boolean.FALSE);
        b.waterloggable();
    }

    private static void presetRug(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("corner", 0, 4));
    }

    private static void presetSeat(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("type", 0, 9));
    }

    private static void presetVariant(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("variant", 0, 15));
    }

    private static void presetFlower(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(BooleanProperty.of("flowerpot"));
        b.extraDefault(BooleanProperty.of("flowerpot"), Boolean.FALSE);
        b.waterloggable();
    }

    /** 窗框：facing + position(0..2) + fence */
    private static void presetFrame(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("position", 0, 2));
        b.extraProp(BooleanProperty.of("fence"));
        b.extraDefault(BooleanProperty.of("fence"), Boolean.FALSE);
        b.waterloggable();
    }

    /** 拱形窗框：facing + type(0..3) */
    private static void presetArch(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("type", 0, 3));
        b.waterloggable();
    }

    /** 玻璃板 / 栏杆：NSEW + waterlogged */
    private static void presetPane(ScriptBlockBuilder b) {
        addSidesFalse(b, Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
        b.waterloggable();
    }

    /** 四个方向布尔，默认 false（BooleanProperty.of 的默认值是 true，必须显式覆盖） */
    private static void addSidesFalse(ScriptBlockBuilder b, net.minecraft.state.property.BooleanProperty... ps) {
        for (net.minecraft.state.property.BooleanProperty p : ps) {
            b.extraProp(p);
            b.extraDefault(p, Boolean.FALSE);
        }
    }

    /** 梁柱：facing + shape + type(left/middle/right) + waterlogged */
    private static void presetPost(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(EnumProperty.of("shape", StairShape.class));
        b.extraProp(EnumProperty.of("type", Segment.class));
        b.waterloggable();
    }

    /** 梁：facing + shape + type + half + waterlogged
     *  ★half 必须用独立属性名★ Properties.SLAB_TYPE 内部名也是 "type"，
     *  与 Segment 的 "type" 同名 ⇒ StateManager 抛 "duplicate property: type"。 */
    private static void presetBeam(ScriptBlockBuilder b) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(EnumProperty.of("type", Segment.class));
        b.extraProp(EnumProperty.of("half", BlockHalf.class));
        b.waterloggable();
    }

    /** 薄层：facing + layers(1..max) + waterlogged */
    private static void presetLayered(ScriptBlockBuilder b, int max) {
        b.extraProp(Properties.HORIZONTAL_FACING);
        b.extraProp(IntProperty.of("layers", 1, max));
        b.waterloggable();
    }

    /** 上下半砖：type(top/bottom/double) + waterlogged */
    private static void presetHalfslab(ScriptBlockBuilder b) {
        b.extraProp(EnumProperty.of("type", SlabType.class));
        b.waterloggable();
    }

    /**
     * 往 KubeJS 的 block 注册表里登记一个自定义类型（例如 {@code cocstair}）。
     *
     * <p>用反射而不是直接调 {@code RegistryInfo.BLOCK.addType(...)}，原因：
     * KubeJS 的 API 在 2001.x 各 build 之间有过签名变动，反射能在编译期零依赖、
     * 运行期缺方法时只打一行 warn 而不让整个 mod 挂掉。
     *
     * <p>签名（build.16 实测）：{@code public void addType(String, Class<? extends BuilderBase>, BuilderFactory)}
     */
    /** 注册一个流体与它的流体方块（流速/范围由 ScriptFluid 构造参数决定） */
    private static void registerFluid(String id, ScriptFluid fluid) {
        Identifier key = new Identifier(DynamicShapes.MOD_ID, id);
        ScriptFluidBlock block = new ScriptFluidBlock(fluid,
                AbstractBlock.Settings.create().replaceable().noCollision().dropsNothing().liquid().strength(100f));
        fluid.bindBlock(block);
        Registry.register(Registries.FLUID, key, fluid);
        Registry.register(Registries.BLOCK, key, block);
        DynamicShapes.LOG.info("[BlockJS] 已注册流体：{}", id);
    }

    @SuppressWarnings("unchecked")
    /** slab 族专用：`ScriptSlabBlockBuilder` 不是 `ScriptBlockBuilder` 的子类，
     *  用上面那个重载会因 {@code instanceof} 判定失败而<b>静默跳过 preset</b>
     *  （2026-10-26 实测：slab_shift/slab_axis 的 facing/shift/axis 全部没加上，blockstate 直接紫黑）。 */
    private static void registerSlabType(String typeId, Class<?> builderClass,
                                         java.util.function.Consumer<ScriptSlabBlockBuilder> preset) {
        registerTypeImpl(typeId, builderClass, b -> preset.accept((ScriptSlabBlockBuilder) b));
    }

    /** P3~P9 的批量类型（GeneratedTypes.java 自动生成）走的入口 */
    static void registerGen(String typeId, java.util.function.Consumer<ScriptBlockBuilder> preset) {
        registerBlockType(typeId, ScriptBlockBuilder.class, preset);
    }

    private static void registerBlockType(String typeId, Class<?> builderClass,
                                          java.util.function.Consumer<ScriptBlockBuilder> preset) {
        registerTypeImpl(typeId, builderClass,
                preset == null ? null : b -> preset.accept((ScriptBlockBuilder) b));
    }

    private static void registerTypeImpl(String typeId, Class<?> builderClass,
                                         java.util.function.Consumer<Object> preset) {
        try {
            Class<?> infoCls = Class.forName("dev.latvian.mods.kubejs.registry.RegistryInfo");
            Object blockInfo = infoCls.getField("BLOCK").get(null);
            Class<?> factoryCls = Class.forName("dev.latvian.mods.kubejs.registry.BuilderFactory");

            Object factory = java.lang.reflect.Proxy.newProxyInstance(
                    factoryCls.getClassLoader(),
                    new Class<?>[]{factoryCls},
                    (proxy, method, args) -> {
                        if ("createBuilder".equals(method.getName()) && args != null && args.length == 1) {
                            Object b = builderClass.getConstructor(Identifier.class).newInstance(args[0]);
                            if (preset != null) preset.accept(b);
                            return b;
                        }
                        return null;
                    });

            infoCls.getMethod("addType", String.class, Class.class, factoryCls)
                    .invoke(blockInfo, typeId, builderClass, factory);
            DynamicShapes.LOG.info("[BlockJS] 已注册自定义方块类型：{}", typeId);
        } catch (Throwable t) {
            DynamicShapes.LOG.warn("[BlockJS] 注册方块类型 {} 失败：{}", typeId, t.toString());
        }
    }
}
