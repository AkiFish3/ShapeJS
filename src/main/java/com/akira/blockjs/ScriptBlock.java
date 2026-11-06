package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidFillable;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.IntProperty;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

import java.util.List;
import java.util.Map;

/**
 * 通用「脚本方块」基类：一套实现覆盖 vslab / half / bar / pane / layered / connect / sheet。
 *
 * <p>设计取舍：**几何不在这里算**。碰撞盒与选择盒统一交给
 * {@link DynamicShapes}（按 blockstate 键查表），所以所有形状族的差异只体现在
 * 「有哪些属性」+「模型文件」，不需要为每个形状写一个 Java 类。
 *
 * <p>本类负责三件真正需要 Java 的事：
 * <ol>
 *   <li><b>追加属性</b>：脚本声明的 {@code mtshape} 之类并进 StateManager；</li>
 *   <li><b>真含水</b>：实现 {@link FluidFillable} + {@code getFluidState}，
 *       这是 KubeJS 的 basic 方块做不到的（属性只是摆设）；</li>
 *   <li><b>放置时定朝向/含水</b>：避免「放下先直、下一帧才变」的闪烁。</li>
 * </ol>
 */
public class ScriptBlock extends Block implements FluidFillable {

    /**
     * 墙端连接位（只给 window_arch 这族 vslab 用，由 builder 的 {@code sides()} 注入）：
     * <pre>
     *   bit0 = 模型 +x 侧（世界 = facing.rotateYClockwise）有同族块
     *   bit1 = 模型 -x 侧（世界 = facing.rotateYCounterclockwise）有同族块
     *   0 = 两端都是端头 → 顶面用 01（两端封口）
     *   1 = 只有 +x 连 → 顶面用 03（**西**端封口，图左封口）
     *   2 = 只有 -x 连 → 顶面用 04（**东**端封口）
     *   3 = 两端都连 → 顶面用 02（`_topwall`，无端头）
     * </pre>
     * 顶面 u=+x（见 _HANDOFF §2），所以「图左」= 模型 -x = 西。
     */
    public static final IntProperty SIDES = IntProperty.of("sides", 0, 15);

    /**
     * 端头位（{@code caps} 0..3）：记录「这一侧的邻块是凸角端头（shape=outer_*）」。
     *
     * <pre>
     *   bit0 = 模型 +x 侧的邻块是 outer_*  ⇒ 本块拱形面取 1.5 块图的 <b>左 2/3</b>
     *   bit1 = 模型 -x 侧的邻块是 outer_*  ⇒ 本块拱形面取 <b>右 2/3</b>
     *   （凸角块自己取剩下的 1/3，见 _v21 的 build_outer）
     * </pre>
     *
     * {@code sides} 只说「连没连」，说不了「连的是直段还是端头」；
     * 而直段 + 端头要在 24 单位（1.5 块）上排一整个窗 ⇒ 必须区分类型。
     * 只有 KubeJS 里那 18 个 window_arch vslab 带这个属性（和 SIDES 一起注入）。
     */
    public static final IntProperty CAPS = IntProperty.of("caps", 0, 3);

    /** 同上：Block 构造期就会回调 appendProperties，字段还没赋值 ⇒ 用 ThreadLocal 传递 */
    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();
    /** 同种方块相邻时剔除公共面（玻璃类"材质连接"），builder 用 prepareCull 传 */
    private static final ThreadLocal<Boolean> PENDING_CULL = new ThreadLocal<>();
    /** NSEW 只认同种（不粘墙）：栏杆/桌椅这类"自家连自家"的族用，builder 用 prepareNsewSameOnly 传 */
    private static final ThreadLocal<Boolean> PENDING_NSEW_SAME = new ThreadLocal<>();
    /** 盒表 id（`cocricot:xxx`）：kind=boxed 时按 id 查旧版 BLOCKJS_RULES 的真值盒 */
    private static final ThreadLocal<String> PENDING_BOX = new ThreadLocal<>();
    /** cocricot 的 vslab/vstair 模型基准朝向是「南」，与 vanilla facing 语义差 180°：
     *  这批方块要在放置时把 facing 取反，模型才正对玩家。 */
    private static final ThreadLocal<Boolean> PENDING_FLIP = new ThreadLocal<>();
    /** 放置算法标识（"vstair" 走点击象限算法） */
    private static final ThreadLocal<String> PENDING_SHAPE_RULE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PENDING_BLINK = new ThreadLocal<>();

    private static final ThreadLocal<String> PENDING_PLACE_MODE = new ThreadLocal<>();
    /** NSEW 连接族：同族不同变体互连（garland_black ↔ garland_pop），builder 用 prepareFamily 传 */
    private static final ThreadLocal<String> PENDING_FAMILY = new ThreadLocal<>();
    /** 完全不做面剔除（半透明 / 大量镂空的摆件），builder 用 prepareNoCull 传 */
    private static final ThreadLocal<Boolean> PENDING_NOCULL = new ThreadLocal<>();
    /** 垂直堆叠（抽屉）：up/down 两个 boolean 按上下邻居实时算 */
    private static final ThreadLocal<Boolean> PENDING_VSTACK = new ThreadLocal<>();
    /** 窗帘/挂帘：vertical 四段按上下邻居实时算 */
    private static final ThreadLocal<Boolean> PENDING_VERTICAL = new ThreadLocal<>();
    /** 没有碰撞体积（只保留轮廓）：火 / 垂花 / 冰淇淋这类装饰，builder 用 prepareNoCollision 传 */
    private static final ThreadLocal<Boolean> PENDING_NOCOL = new ThreadLocal<>();
    /** ★碰撞强制整格★（meta 的 {@code fc:1}）：斜向玻璃板这类"几何很薄但玩家不该穿过去"的方块 */
    private static final ThreadLocal<Boolean> PENDING_FULLCOL = new ThreadLocal<>();
    /** ★关掉放置时按点击高度写 click★（minia MT_3ClickFacing / MT_2ClickFacingHUD 恒 click=0） */
    private static final ThreadLocal<Boolean> PENDING_AUTOCLICK = new ThreadLocal<>();

    public static void prepareCull(boolean v) { PENDING_CULL.set(v); }

    public static void prepareNsewSameOnly(boolean v) { PENDING_NSEW_SAME.set(v); }

    public static void prepareBox(String id) { PENDING_BOX.set(id); }

    public static void prepareFlip(boolean v) { PENDING_FLIP.set(v); }

    public static void preparePlaceMode(String m) { PENDING_PLACE_MODE.set(m); }

    /** ★放置时是否按点击高度自动写 click★（默认 true）。minia `MT_3ClickFacing` 恒 click=0 ⇒ 关掉。 */
    public static void prepareAutoClick(boolean v) { PENDING_AUTOCLICK.set(v); }

    public static void prepareShapeRule(String r) { PENDING_SHAPE_RULE.set(r); }

    public static void prepareBlink(boolean b) { PENDING_BLINK.set(b); }

    public static void prepareFamily(String fam) { PENDING_FAMILY.set(fam); }

    public static void prepareNoCull(boolean v) { PENDING_NOCULL.set(v); }

    public static void prepareVStack(boolean v) { PENDING_VSTACK.set(Boolean.valueOf(v)); }

    public static void prepareVertical(boolean v) { PENDING_VERTICAL.set(Boolean.valueOf(v)); }

    public static void prepareNoCollision(boolean v) { PENDING_NOCOL.set(v); }

    public static void prepareFullCollision(boolean v) { PENDING_FULLCOL.set(v); }

    public static void prepare(List<Property<?>> props) {
        PENDING_PROPS.set(props == null ? List.of() : props);
    }

    public static void clear() {
        PENDING_PROPS.remove();
        PENDING_CULL.remove();
        PENDING_NSEW_SAME.remove();
        PENDING_BOX.remove();
        PENDING_FLIP.remove();
        PENDING_PLACE_MODE.remove();
        PENDING_SHAPE_RULE.remove();
        PENDING_BLINK.remove();
        PENDING_FAMILY.remove();
        PENDING_NOCULL.remove();
        PENDING_VSTACK.remove();
        PENDING_VERTICAL.remove();
        PENDING_NOCOL.remove();
        PENDING_FULLCOL.remove();
        PENDING_AUTOCLICK.remove();
    }

    private final List<Property<?>> extraProps;
    private final Map<Property<?>, Comparable<?>> extraDefaults;
    private final boolean waterloggable;
    /** 截面像素（0 = 不用），用于粗条/细杆 */
    private final int crossSize;
    /** 几何提示：slope / cylinder / step3 / step6 */
    private final String kind;
    private final boolean sameCull;
    /** NSEW 只认同种方块（不把实心墙当邻居） */
    private final boolean nsewSameOnly;
    /** kind=boxed 时用的盒表 id */
    private final String boxedId;
    private final boolean flipFacing;
    private final String placeMode;
    /** 放置时是否按点击高度自动写 click（false = 恒默认值，minia MT_3ClickFacing 语义） */
    private final boolean autoClick;

    /**
     * shape 计算规则（默认 null = 原版楼梯语义）。
     *
     * <p>★"mt" = miniaturia 的 MT_VHalf / MT_2ClickVHalfUL 语义★（javap 实证
     * {@code getStairsShape} / {@code getClickShape} 两个分支）：
     * <b>前邻居 → outer、后邻居 → inner</b>，与原版楼梯（前→inner、后→outer）**恰好相反**。
     * 这就是"棚子/雨棚/屋顶的 inner/outer 老是反的"的根因 —— 我们一直套的是原版规则。
     */
    private final String shapeRule;

    /** 灯闪：每 10 tick 翻转一次 lighting */
    private final boolean blink;
    /** 连接族：NSEW 属性允许与**同族其它方块**互连（null = 只认同一种方块，历史行为） */
    private final String family;

    /**
     * ★完全不参与面剔除★（meta 的 {@code nc:1}）。
     *
     * <p>半透明 / 大量镂空的摆件（蛋糕架、渔网、落叶、玻璃器皿）有个共同点：
     * <b>包围盒远大于真实几何</b>。{@code FaceCull} 拿包围盒做
     * {@code isSideCovered} 判定，会把"其实没被盖住"的面判成盖住 ⇒
     * 两个同款并排/叠放时各缺一面（Aki 2026-10-27：「有一部分变成透明了」）。
     *
     * <p>这类方块本来就不该靠"面重复"省性能，直接退出所有剔除路径：
     * {@link #isSideInvisible} 恒 false、{@link #getCullShape} 返回空。
     */
    private final boolean noCull;

    /** ★垂直堆叠★：抽屉族的 up/down 两个 boolean，按上下相邻同类方块实时重算 */
    private final boolean vstack;
    /** 窗帘/挂帘：vertical 四段按上下相邻同类方块实时重算 */
    private final boolean vertical;

    /** ★没有碰撞体积★（meta 的 {@code nocol:1}）：火/垂花/冰淇淋等装饰，碰撞为空但轮廓保留 */
    private final boolean noCollision;

    /**
     * ★碰撞强制整格★（meta 的 {@code fc:1}，Aki A4）：
     * 斜向玻璃板（{@code *_pane_slant} / {@code *_pane_slant_long}）几何是斜的薄片，
     * 照盒表给碰撞 ⇒ 玩家能从旁边走过去。碰撞改成整格，**轮廓仍用盒表**（选择框还是薄片），
     * 并按玻璃处理透光（见 {@link #seeThroughGlass}）。
     */
    private final boolean fullCollision;

    public ScriptBlock(Settings settings,
                    List<Property<?>> extraProps,
                    Map<Property<?>, Comparable<?>> extraDefaults,
                    boolean waterloggable,
                    int crossSize,
                    String kind) {
        super(settings);
        this.extraProps = extraProps == null ? List.of() : List.copyOf(extraProps);
        this.extraDefaults = extraDefaults == null ? Map.of() : Map.copyOf(extraDefaults);
        this.waterloggable = waterloggable;
        this.crossSize = crossSize;
        this.kind = kind == null ? "" : kind;
        this.sameCull = Boolean.TRUE.equals(PENDING_CULL.get());
        this.nsewSameOnly = Boolean.TRUE.equals(PENDING_NSEW_SAME.get());
        this.boxedId = PENDING_BOX.get();
        this.flipFacing = Boolean.TRUE.equals(PENDING_FLIP.get());
        String pm = PENDING_PLACE_MODE.get();
        this.placeMode = pm == null ? "" : pm;
        Boolean ac = PENDING_AUTOCLICK.get();
        this.autoClick = ac == null || ac;
        String sr = PENDING_SHAPE_RULE.get();
        this.shapeRule = (sr == null || sr.isEmpty()) ? null : sr;
        this.blink = Boolean.TRUE.equals(PENDING_BLINK.get());
        String fm = PENDING_FAMILY.get();
        this.family = (fm == null || fm.isEmpty()) ? null : fm;
        this.noCull = Boolean.TRUE.equals(PENDING_NOCULL.get());
        this.vstack = Boolean.TRUE.equals(PENDING_VSTACK.get());
        this.vertical = Boolean.TRUE.equals(PENDING_VERTICAL.get());
        this.noCollision = Boolean.TRUE.equals(PENDING_NOCOL.get());
        this.fullCollision = Boolean.TRUE.equals(PENDING_FULLCOL.get());

        BlockState state = getDefaultState();
        for (Property<?> p : this.extraProps) {
            Comparable<?> v = this.extraDefaults.get(p);
            if (v == null) v = p.getValues().iterator().next();
            state = withUnchecked(state, p, v);
        }
        setDefaultState(state);
    }

    /**
     * NSEW 连接判定：同一种方块 **或** 同连接族（meta 的 `fam`）。
     *
     * <p>为什么要族：coc 的 6 个彩带（garland_black/cool/cute/happy/natural/pop）和 miniaturia 的
     * 6 个（garland_a..d/xmas）是**同一套 NSEW 几何、只换贴图**。原来判定是 `nb.isOf(this)`，
     * 于是黑彩带接不上彩色带 —— 摆一条彩带要一个颜色摆到底。现在同族互相连，
     * 实心方块那条（`isSideSolidFullSquare`）不变。
     *
     * <p>`fam` 值里带命名空间（`cocricot:garland`），所以 coc 与 minia 的彩带**不会**串在一起。
     */
    private boolean connectsTo(BlockState other) {
        if (other.isOf(this)) return true;
        if (family == null) return false;
        Identifier id = Registries.BLOCK.getId(other.getBlock());
        return id != null && family.equals(BlockMeta.family(id.toString()));
    }

    // ------------------------------------------------------------ 树枝（cocricot:branch_*，C68）

    /**
     * 官方 {@code LogBranch.isLogBlock} 的等价表（javap 逐条抄）：**同一种树枝自己** + 原版 6 种原木
     * + coc 的 6 个 {@code log_all_*}。官方是 `block == this || block == Blocks.OAK_LOG || …`，
     * 所以白桦枝接不上橡木枝（不同 block 实例）—— 这里按 id 集合镜像，语义相同。
     */
    private static final java.util.Set<String> BRANCH_LOG_IDS = java.util.Set.of(
            "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
            "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
            "cocricot:log_all_oak", "cocricot:log_all_spruce", "cocricot:log_all_birch",
            "cocricot:log_all_jungle", "cocricot:log_all_acacia", "cocricot:log_all_darkoak");

    /** 官方 {@code isTrunkBlock}：只认 coc 的 6 个 {@code trunk_*}（决定用 `*_side_trunk` 模型）。 */
    private static final java.util.Set<String> BRANCH_TRUNK_IDS = java.util.Set.of(
            "cocricot:trunk_oak", "cocricot:trunk_spruce", "cocricot:trunk_birch",
            "cocricot:trunk_jungle", "cocricot:trunk_acacia", "cocricot:trunk_darkoak");

    private boolean isBranchMode() {
        return "branch".equals(placeMode);
    }

    private static String idOf(BlockState st) {
        Identifier id = Registries.BLOCK.getId(st.getBlock());
        return id == null ? "" : id.toString();
    }

    /**
     * 重算 8 个属性：`{north,east,south,west}` = 连得上（同种树枝 / 原版原木 / coc log_all / 实心满面），
     * `trunk_*` = 该侧邻居是 coc 的 `trunk_*`（与 connected 独立，官方也是两个属性各自赋值）。
     */
    private BlockState applyBranchState(BlockState state, BlockView world, BlockPos pos) {
        for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST}) {
            BlockPos np = pos.offset(d);
            BlockState nb = world.getBlockState(np);
            String nid = idOf(nb);
            boolean conn = nb.isOf(this) || BRANCH_LOG_IDS.contains(nid)
                    || (!nsewSameOnly && nb.isSideSolidFullSquare(world, np, d.getOpposite()));
            Property<?> cp = propNamed(state, d.getName());
            if (cp instanceof BooleanProperty cbp) state = state.with(cbp, conn);
            Property<?> tp = propNamed(state, "trunk_" + d.getName());
            if (tp instanceof BooleanProperty tbp) state = state.with(tbp, BRANCH_TRUNK_IDS.contains(nid));
        }
        return state;
    }

    // ------------------------------------------------------------ connect 板（facing + fence + shape）

    /** `*_connect` 连接板：facing + fence(bool) + shape(int 0..3) */
    private static boolean isConnectBoard(BlockState st) {
        return st.contains(Properties.HORIZONTAL_FACING)
                && propNamed(st, "fence") instanceof BooleanProperty
                && propNamed(st, "shape") instanceof IntProperty;
    }

    /** 邻居是「同族连接板 + 同 facing」（族由 meta 的 fam 给，结构相同、只差材质的算一族） */
    private boolean isConnectNeighbor(BlockState other, Direction facing) {
        if (family == null) return false;
        if (!other.contains(Properties.HORIZONTAL_FACING)) return false;
        if (other.get(Properties.HORIZONTAL_FACING) != facing) return false;
        Identifier id = Registries.BLOCK.getId(other.getBlock());
        return id != null && family.equals(BlockMeta.family(id.toString()));
    }

    /**
     * shape：0=single 1=left 2=right 3=middle。
     *
     * <p>★左右按模型实测★：`*_connect_left` 的横杆是 `x 0..13`（伸向 -x），
     * `*_right` 是 `x 3..16`（伸向 +x）。⇒ left = facing 的逆时针侧，right = 顺时针侧。
     * （旧搬运代码把 a/b 写反了：它 left 用顺时针。）
     */
    private int connectShapeAt(BlockView world, BlockPos pos, Direction facing) {
        boolean left = isConnectNeighbor(world.getBlockState(pos.offset(facing.rotateYCounterclockwise())), facing);
        boolean right = isConnectNeighbor(world.getBlockState(pos.offset(facing.rotateYClockwise())), facing);
        if (left && right) return 3;
        if (left) return 1;
        if (right) return 2;
        return 0;
    }

    /**
     * fence：下方能当柱子 ⇒ 换成带立柱的那套模型。
     *
     * <p>判据（Aki 2026-10-26 指定）：下方是栅栏（原版 FenceBlock / id 含 fence、handrail、
     * 以 `_connect` 结尾），或下方是**竖直细杆**（`facing=up|down` 或 `axis=y`，且不是满方块）。
     * 满方块排除掉：不然站在原木/石柱上也会误判成"架在柱子上"。
     */
    private static boolean fencedBelow(BlockView world, BlockPos pos) {
        BlockPos belowPos = pos.down();
        BlockState below = world.getBlockState(belowPos);
        if (below.getBlock() instanceof net.minecraft.block.FenceBlock) return true;
        Identifier id = Registries.BLOCK.getId(below.getBlock());
        if (id != null) {
            String p = id.getPath();
            if (p.endsWith("_connect") || p.contains("fence") || p.contains("handrail")) return true;
        }
        if (below.isFullCube(world, belowPos)) return false;
        Property<?> f = propNamed(below, "facing");
        if (f != null) {
            String v = valueName(below, f);
            if ("up".equals(v) || "down".equals(v)) return true;
        }
        Property<?> ax = propNamed(below, "axis");
        return ax != null && "y".equals(valueName(below, ax));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(BlockState st, Property<?> p) {
        return String.valueOf(st.get((Property) p));
    }

    /**
     * 相邻面剔除（玻璃/窗块的"材质连接"）。
     *
     * <p>1.20.1 的钩子不是 {@code skipRendering}（那个名字在该版本不存在，javap 实证），
     * 而是 {@code AbstractBlock.isSideInvisible(state, stateFrom, direction)}：
     * {@code Block.shouldDrawSide} 会先问邻居 {@code other.isSideInvisible(state, side)}，
     * 返回 true 就直接跳过这个面的绘制。
     *
     * <p>★参数次序（1.20.1 字节码实证）★：{@code AbstractBlockState.isSideInvisible(state, dir)}
     * 内部是 {@code getBlock().isSideInvisible(this, state, dir)} ⇒
     * <b>第一个是自己（要画的那一面所属的方块）</b>，<b>第二个是邻居</b>。
     * 早期注释写反了（当时只做对称比较，写成反的也不影响结果）。
     *
     * @param state     自己（这一面所属的方块）
     * @param stateFrom 邻居
     */
    @Override
    public boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
        // ★noCull：一条剔除规则都不走★（半透明/镂空摆件的包围盒不可信，见字段注释）
        if (noCull) return false;
        if (sameCull && state.isOf(this) && stateFrom.isOf(this)) {
            return true;
        }
        // 按几何覆盖剔面：竖台阶（boxed）自己或同族相邻时，被邻居完整盖住的面不画。
        // 例：两块同向竖台阶左右并排 ⇒ 贴合面两边同时剔掉，拼成一整块（Aki 2026-10-26）。
        if (FaceCull.related(state, stateFrom) && FaceCull.covered(state, stateFrom, direction)) {
            return true;
        }
        return super.isSideInvisible(state, stateFrom, direction);
    }

    /**
     * ★满立方玻璃必须和原版 {@code AbstractGlassBlock} 一样「不挡光」★
     *
     * <p>原版：{@code Block#isTransparent(...)} 默认是
     * {@code !isShapeFullCube(outlineShape) && fluidEmpty} ⇒ <b>满立方永远返回 false</b>，
     * 于是 {@code AbstractBlock#getOpacity} 落到 {@code isTransparent ? 0 : 1} = <b>1</b>
     * ⇒ 每格削掉 1 级光照，屋顶/外墙投下"遮光玻璃"式的影子。
     * 原版玻璃靠 {@code AbstractGlassBlock#isTransparent → true} 拿到 opacity 0 才完全透光。
     *
     * <p>判定口径：<b>音效组是 GLASS 且轮廓是满立方</b>，再排除两类「音效被误设成 glass 的实心家具」。
     * 细薄件（窗格 / 竖台阶 / 画）本来就走默认 true，不受影响；门、家具、木构件轮廓不是满立方
     * 或命中排除名单，照旧挡光。
     */
    private static final String[] NOT_GLASS_PREFIX = {"wood_wainscot_panels_", "cabinet_glass_"};

    private boolean seeThroughGlass(BlockState state, BlockView world, BlockPos pos) {
        if (state.getSoundGroup() != net.minecraft.sound.BlockSoundGroup.GLASS) return false;
        String path = boxedId;
        if (path == null) {
            Identifier selfId = Registries.BLOCK.getId(this);
            path = selfId == null ? "" : selfId.getPath();
        }
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        for (String p : NOT_GLASS_PREFIX) {
            if (path.startsWith(p)) return false;
        }
        // ★fc（斜向玻璃板）：轮廓永远是薄片 ⇒ 满立方判据永远 false。按 A4「碰撞整格但仍透光」直接放行★
        if (fullCollision) return true;
        return Block.isShapeFullCube(state.getOutlineShape(world, pos));
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return seeThroughGlass(state, world, pos) || super.isTransparent(state, world, pos);
    }

    /**
     * ★光照遮挡必须直接压到 0★
     *
     * <p>只改 {@link #isTransparent} 不够：{@code AbstractBlock#getOpacity} 先判
     * {@code state.isOpaqueFullCube(...)}（= 设置里的 opaque 标记 && 满立方），
     * meta 没写 {@code occ} 的方块默认 solid ⇒ 直接返回 {@code getMaxLightLevel()=15}
     * ⇒ 玻璃把光全吃掉，比"削 1 级"更黑（Aki：「像遮光玻璃一样的影子」）。
     * 这里先短路成 0，与 opaquetrue 与否无关。
     */
    @Override
    public int getOpacity(BlockState state, BlockView world, BlockPos pos) {
        if (seeThroughGlass(state, world, pos)) return 0;
        return super.getOpacity(state, world, pos);
    }

    /** 同上：原版玻璃不吃环境光遮蔽，面片恒为满亮（{@code AbstractGlassBlock} 返回 1.0F） */
    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        if (seeThroughGlass(state, world, pos)) return 1.0F;
        return super.getAmbientOcclusionLightLevel(state, world, pos);
    }

    /** 第三人称相机不该卡在玻璃里（{@code AbstractGlassBlock#getCameraCollisionShape} 返回空） */
    @Override
    public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (seeThroughGlass(state, world, pos)) return VoxelShapes.empty();
        return super.getCameraCollisionShape(state, world, pos, context);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withUnchecked(BlockState state, Property p, Comparable v) {
        return state.with(p, v);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        List<Property<?>> pending = PENDING_PROPS.get();
        if (pending != null) {
            for (Property<?> p : pending) builder.add(p);
        }
    }

    /** 只有真的带 WATERLOGGED 属性时才走含水逻辑（避免给所有方块塞属性） */
    private boolean hasWater(BlockState state) {
        return waterloggable && state.contains(Properties.WATERLOGGED);
    }

    /**
     * 切换 switch / open / lighting 布尔属性。返回 true 表示「有可切换的属性」。
     * open（窗帘）会沿 Y 轴整列联动，模拟 Let's Do 窗帘的整幅开合。
     *
     * <p>lighting（灯）只在【空手】时才切：官方 coc 的灯是 {@code onUse} 取反，
     * 但那会把「手持方块右键点灯」的放置一起吞掉（KubeJS 的 rightClick 回调更是无条件 SUCCESS）。
     * 我们照 lamp_toggle.js 的取舍：手上有东西就放行，交给原版放置。
     */
    public static boolean toggleFlag(net.minecraft.world.World world, BlockPos pos, BlockState state) {
        return toggleFlag(world, pos, state, true);
    }

    /**
     * layers 堆叠（brick_*_pile 等 252 个 {@code x_f4_ly8} 方块）：
     * 空手右键 {@code layers+1}、潜行右键 {@code layers-1}；到上下限时**吃掉交互但不改状态**。
     *
     * @return 是否由本方法处理了这次右键（true ⇒ 调用方返回 SUCCESS）
     */
    public static boolean cycleLayers(net.minecraft.world.World world, BlockPos pos, BlockState state,
                                      boolean sneak) {
        Property<?> p = propNamed(state, "layers");
        if (!(p instanceof IntProperty ip)) return false;
        if (world.isClient) return true;
        // ★1.20.1 映射下的 IntProperty 没有 getMin/getMax★（编译期找不到符号）⇒ 从取值域现算
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (Comparable<?> v : ip.getValues()) {
            int n = ((Number) v).intValue();
            if (n < lo) lo = n;
            if (n > hi) hi = n;
        }
        int cur = state.get(ip);
        int want = sneak ? cur - 1 : cur + 1;
        if (want < lo || want > hi) return true;   // 下限 1 不再减，也不回绕
        world.setBlockState(pos, state.with(ip, want), Block.NOTIFY_ALL);
        return true;
    }

    public static boolean toggleFlag(net.minecraft.world.World world, BlockPos pos, BlockState state,
                                     boolean emptyHand) {
        Property<?> p = propNamed(state, "switch");
        boolean isCurtain = false;
        if (!(p instanceof BooleanProperty)) {
            // ★open（窗帘/百叶窗）只在空手时切★：手持方块应当是「放置」，
            //   否则点窗板永远只会开关、放不上东西（Aki 2026-10-27 报的"不能点击它们的面来放置方块"）。
            p = emptyHand ? propNamed(state, "open") : null;
            isCurtain = true;
        }
        if (!(p instanceof BooleanProperty) && emptyHand) {
            p = propNamed(state, "lighting");
            isCurtain = false;
        }
        if (!(p instanceof BooleanProperty bp)) return false;
        if (world.isClient) return true;

        boolean nv = !state.get(bp);
        world.setBlockState(pos, state.with(bp, nv), Block.NOTIFY_ALL);
        // ★开关音效★：以前切状态是**完全静音**的（Aki 2026-10-28）。
        //   ★2026-10-28 21:4x 二次更正★：百叶窗/窗帘/扇窗要的是**开关门音效**，
        //   不是方块放置/破坏音（那个是"啪"一声的石头/木头块音，完全不像开合）。
        //   —— open  → 木门/铁门 开·关（按方块自己的 SoundGroup 判断金属）
        //   —— lighting / switch → 拉杆咔哒
        String pn = bp.getName();
        playToggleSound(world, pos, state, pn, nv);
        if (isCurtain) {
            Block self = state.getBlock();
            for (Direction d : new Direction[]{Direction.UP, Direction.DOWN}) {
                BlockPos q = pos.offset(d);
                for (int guard = 0; guard < 24; guard++) {
                    BlockState s2 = world.getBlockState(q);
                    if (!s2.isOf(self)) break;
                    Property<?> p2 = propNamed(s2, "open");
                    if (!(p2 instanceof BooleanProperty bp2)) break;
                    world.setBlockState(q, s2.with(bp2, nv), Block.NOTIFY_ALL);
                    q = q.offset(d);
                }
            }
        }
        return true;
    }

    /**
     * ★变体循环★（Aki 2026-10-28）：手持该方块本身 + 潜行 + 右键 → 原地切指定属性。
     *
     * <p>用途：facade 这类 {@code variant=meta0..4} 的方块，放置时永远落在默认值，
     * 另外 4 档在生存/创造里都进不去。这里做「在已放置的方块上循环」，不涉及物品 NBT。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean cycleNamedProp(net.minecraft.world.World world, BlockPos pos, BlockState state,
                                         String... names) {
        for (String n : names) {
            Property p = propNamed(state, n);
            if (p == null) continue;
            if (!(p instanceof IntProperty) && !(p instanceof net.minecraft.state.property.EnumProperty)) continue;
            List vals = new java.util.ArrayList(p.getValues());
            if (vals.size() < 2) continue;
            int i = vals.indexOf(state.get(p));
            Comparable next = (Comparable) vals.get((i + 1) % vals.size());
            if (world.isClient) return true;
            world.setBlockState(pos, state.with(p, next), Block.NOTIFY_ALL);
            return true;
        }
        return false;
    }

    /**
     * 开关音效：{@code open} → 木门/铁门开合（按方块 SoundGroup 判金属）；
     * {@code lighting} / {@code switch} → 拉杆咔哒。
     *
     * <p>右键切换与红石驱动（{@link #neighborUpdate}）共用，保证两条路径音色一致。
     */
    static void playToggleSound(net.minecraft.world.World world, BlockPos pos, BlockState state,
                                String propName, boolean nowOn) {
        net.minecraft.sound.SoundEvent sound;
        float vol = 1.0F, pitch = 1.0F;
        if ("open".equals(propName)) {
            boolean metal = state.getSoundGroup() == net.minecraft.sound.BlockSoundGroup.METAL
                    || state.getSoundGroup() == net.minecraft.sound.BlockSoundGroup.COPPER;
            sound = metal
                    ? (nowOn ? net.minecraft.sound.SoundEvents.BLOCK_IRON_DOOR_OPEN
                             : net.minecraft.sound.SoundEvents.BLOCK_IRON_DOOR_CLOSE)
                    : (nowOn ? net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_OPEN
                             : net.minecraft.sound.SoundEvents.BLOCK_WOODEN_DOOR_CLOSE);
        } else {
            // lighting / switch：拉杆咔哒（原版音量 0.3，这里略放大一点，灯具多在室外）
            sound = net.minecraft.sound.SoundEvents.BLOCK_LEVER_CLICK;
            vol = 0.45F;
            pitch = nowOn ? 0.6F : 0.5F;
        }
        world.playSound(null, pos, sound, net.minecraft.sound.SoundCategory.BLOCKS, vol, pitch);
    }

    /**
     * ★红石驱动百叶窗★（Aki 2026-10-28：百叶窗全族要 {@code open}/{@code facing}/{@code powered}）。
     *
     * <p>语义照抄原版 {@code TrapdoorBlock}：{@code powered} 跟随红石信号，
     * 信号变化时把 {@code open} 同步过去并播开关门音效。手动右键只翻 {@code open}，
     * {@code powered} 保持 false（所以断电时不会「自己关回去」）。
     */
    @Override
    public void neighborUpdate(BlockState state, net.minecraft.world.World world, BlockPos pos,
                               Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient()) return;
        // ★按名字取属性★：BlockJS 建的类型用 `BooleanProperty.of("powered")` 新建实例，
        //   而 Property 的 equals 是引用相等 ⇒ 只有名字可靠（同 toggleFlag 的做法）。
        if (!(propNamed(state, "powered") instanceof BooleanProperty pbp)) return;
        boolean powered = world.isReceivingRedstonePower(pos);
        if (powered == state.get(pbp)) return;
        BlockState ns = state.with(pbp, powered);
        if (propNamed(state, "open") instanceof BooleanProperty obp && state.get(obp) != powered) {
            ns = ns.with(obp, powered);
            playToggleSound(world, pos, state, "open", powered);
        }
        world.setBlockState(pos, ns, Block.NOTIFY_LISTENERS);
    }

    // ------------------------------------------------------------------ 梯子 / 闪灯

    // ★攀爬不走 Java★：yarn 1.20.1 的 Block 没有 isLadder，LivingEntity 认的是
    //   `minecraft:climbable` 方块标签 ⇒ 用数据包把梯子 id 加进去（见 _y7_ladders.py）。

    /** 闪灯的节拍：放置时开始，之后每 10 tick 翻一次 */
    @Override
    public void onBlockAdded(BlockState state, net.minecraft.world.World world, BlockPos pos,
                             BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (blink && !world.isClient()) {
            world.scheduleBlockTick(pos, this, 10);
        }
    }

    @Override
    public void scheduledTick(BlockState state, net.minecraft.server.world.ServerWorld world,
                              BlockPos pos, net.minecraft.util.math.random.Random random) {
        if (!blink) {
            super.scheduledTick(state, world, pos, random);
            return;
        }
        Property<?> lp = propNamed(state, "lighting");
        if (lp instanceof BooleanProperty lbp) {
            world.setBlockState(pos, state.with(lbp, !state.get(lbp)), Block.NOTIFY_ALL);
        }
        world.scheduleBlockTick(pos, this, 10);
    }

    /** switch 属性：空手右键切换（原版拉杆/开关手感）；lighting 同样只在空手时切 */
    @Override
    public net.minecraft.util.ActionResult onUse(BlockState state, net.minecraft.world.World world,
                                                 BlockPos pos, net.minecraft.entity.player.PlayerEntity player,
                                                 net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hit) {
        boolean emptyHand = player.getStackInHand(hand).isEmpty();
        if (toggleFlag(world, pos, state, emptyHand)) {
            return net.minecraft.util.ActionResult.SUCCESS;
        }
        // ★layers 堆叠（pile 族，A6 2026-10-29）★：空手右键 +1 层、Shift+右键 −1 层、layers=1 不可再减。
        //   只在主手判（副手也会走一次 onUse，不判就一次点击加两层）。
        if (hand == net.minecraft.util.Hand.MAIN_HAND && emptyHand
                && cycleLayers(world, pos, state, player.isSneaking())) {
            return net.minecraft.util.ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hand, hit);
    }

    // ------------------------------------------------------------------ 含水

    @Override
    public FluidState getFluidState(BlockState state) {
        if (hasWater(state) && state.get(Properties.WATERLOGGED)) {
            return Fluids.WATER.getStill(false);
        }
        return super.getFluidState(state);
    }

    @Override
    public boolean canFillWithFluid(BlockView world, BlockPos pos, BlockState state, Fluid fluid) {
        return hasWater(state) && !state.get(Properties.WATERLOGGED) && fluid == Fluids.WATER;
    }

    @Override
    public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState) {
        if (!hasWater(state) || state.get(Properties.WATERLOGGED)) return false;
        if (fluidState.getFluid() != Fluids.WATER) return false;
        if (!world.isClient()) {
            world.setBlockState(pos, state.with(Properties.WATERLOGGED, true), Block.NOTIFY_ALL);
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }
        return true;
    }

    // ------------------------------------------------------------------ 放置

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockState state = getDefaultState();

        // ★rug8（C20 · coc `rug_rustic` 的 facing8）★
        //   官方 8 向语义：贴墙 ⇒ facing = 被点击的那一面（rug 朝外）；放地面/吊顶 ⇒ facing = down_<玩家水平朝向>。
        //   不能用下面的 HORIZONTAL_FACING 分支：那是 vanilla 4 值枚举，装不下 down_*。
        if ("rug8".equals(placeMode)) {
            Property<?> fp = propNamed(state, "facing");
            if (fp != null) {
                Direction side = ctx.getSide();
                String v = side.getAxis().isHorizontal()
                        ? side.getName()
                        : "down_" + ctx.getHorizontalPlayerFacing().getName();
                state = setByName(state, fp, v);
            }
        }

        // 朝向：三种策略，按类型属性区分
        if ("poleshape".equals(placeMode)) {
            // ★roof 斜面 8 向（C33 · `roof_*_hips`）★
            //   官方 `MT_8Position2Click.getStateForPlacement`：poleshape = getHorizontalDirection().getOpposite()，
            //   `$SwitchMap$Direction` 实测 NORTH=1 / EAST=2 / SOUTH=3 / WEST=4 ⇒ n / e / s / w。
            //   该类型没有 HORIZONTAL_FACING（下面那条 if 不成立），所以单独在这一层做；
            //   斜向 4 值（ne/se/sw/nw）官方放置从不给，只由 shape hammer 循环。
            Property<?> polep = propNamed(state, "poleshape");
            if (polep != null) {
                state = setByName(state, polep, ctx.getHorizontalPlayerFacing().getOpposite().getName());
            }
        }
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            boolean hasShape = propNamed(state, "shape") != null;
            boolean hasLayers = propNamed(state, "layers") != null;
            Direction decided;
            if ("vstair".equals(placeMode)) {
                // 竖楼梯：按【点击点所在的象限】定朝向（照抄旧搬运代码 cocVstairFacing）
                decided = vstairFacing(ctx);
            } else if ("carpetstair".equals(placeMode)) {
                // 地毯楼梯：和 vanilla 楼梯一致 —— 朝向 = 玩家朝向（不取反），上半/下半由点击高度决定
                decided = ctx.getHorizontalPlayerFacing();
            } else if ("vconnect".equals(placeMode)) {
                // ★垂直分段家具（minia MT_FacingConnectV）★：facing = 玩家朝向取反，与官方字节码一致。
                //   不能走 facingFromHit —— 那样同一朝向会因点击近/远半差 180°，上下段永远接不上。
                decided = ctx.getHorizontalPlayerFacing().getOpposite();
            } else if ("vconnect_self".equals(placeMode)) {
                // ★C38（第 45 轮）：vertical connect 但 facing **不取反**★
                //   官方 `TypeBarrelHorizontalConnectIDV` 继承 `TypeBarrelHorizontal.getStateForPlacement`，
                //   用的是 `getHorizontalDirection()`（= 玩家朝向本身），不是取反版。
                //   shape 语义与 vconnect 相同（同块 + 同 facing 的上下邻居）⇒ 只差这一行。
                decided = ctx.getHorizontalPlayerFacing();
            } else if ("lrmrow".equals(placeMode)) {
                // ★水平杆（wood_hpole，C111 第 92 轮 · Aki 九宫格实测）★
                //   `facing` = 玩家朝向本身（**不旋转**）。blockstate 实证：
                //     facing=north,type=left → 模型 `hpole_right` = x -3.5..3.5（西侧）
                //     facing=south,type=left → 模型 `hpole_left`  = x 12.5..19.5（东侧）
                //   即「facing 朝谁，type=left 就在该 facing 的左手边」，与 1/4/7 左列 = left 一致。
                //   C108 的 `rotateYClockwise()` 已推翻：那会把 facing 转成 east，hitLane 随即改沿 z 轴取三分位，
                //   Aki 2026-11-04 报「点 1/4/7 得 facing=east + type=L/M/R」正是此链条。
                decided = ctx.getHorizontalPlayerFacing();
            } else if ("mtshape_hit".equals(placeMode)) {
                // ★墙角石（cocricot `mt_quoin_*`，C144c · Aki 2026-11-05）★
                //   Aki 报「放置应贴合目标方块而不是玩家」：墙角石是贴墙的转角护片，
                //   朝向应当由**被点击的那一面**决定（= 它贴着的那堵墙往外），玩家站位无关。
                //   点顶/底面时没有水平面信息 ⇒ 退回「玩家朝向取反」（与官方 BaseFac 一致）。
                Direction side = ctx.getSide();
                decided = side.getAxis().isHorizontal()
                        ? side
                        : ctx.getHorizontalPlayerFacing().getOpposite();
            } else if (isConnectBoard(state)) {
                // ★连接板（*_connect）★ facing = 玩家朝向（旧版 `getHorizontalDirection`，不取反）。
                //   原来落到下面的 hasShape 分支（按点击前后半算朝向）⇒ 同一排的板朝向不一致，
                //   永远连不上 —— 这是"相接时形状不变"的一半原因。
                decided = ctx.getHorizontalPlayerFacing();
            } else if (hasShape) {
                // 楼梯型（天窗 `*_skylight_half` / 斜窗 `window_slanted_*` / 渔网 `net_*`）：
                //   facing = 玩家朝向（**不取反**）—— 与这族 blockstate 的 y 映射配套
                //   （2026-10-27 Aki 实机：取反后整体反了 180°）。
                //   旧行为落进 facingFromHit：同一朝向会因"点击落在近半还是远半"给出相差
                //   180° 的 facing ⇒ computeShapeName 拿错的朝向判前/后邻居 ⇒ 拐角时有时无。
                //   这里只把"随机"改成"确定"，方向沿用实机验证过的那个。
                //   竖台阶（kind=boxed）保持 facingFromHit —— 那族 facing 本来就按点击半边定。
                if ("stairlike".equals(placeMode)) {
                    decided = ctx.getHorizontalPlayerFacing();
                } else {
                    // 竖台阶：看点击落在方块的哪一半（近处 → 面向玩家那一侧；远处 → 另一侧）
                    decided = facingFromHit(state, ctx);
                }
            } else if (hasLayers) {
                // 薄层：贴附在点击的那个面上 ⇒ 朝外 = 点击面的反方向
                Direction side = ctx.getSide();
                decided = side.getAxis().isHorizontal() ? side.getOpposite()
                                                        : ctx.getHorizontalPlayerFacing().getOpposite();
            } else {
                // 竖楼梯等：与玩家朝向相反
                decided = ctx.getHorizontalPlayerFacing().getOpposite();
            }
            if (flipFacing) decided = decided.getOpposite();
            state = state.with(Properties.HORIZONTAL_FACING, decided);
        } else {
            // 六向薄片（sheet）：贴在被点击的那个面 ⇒ 朝外 = 点击面反方向（含 up/down）
            if (state.contains(Properties.FACING)) {
                Direction six = ctx.getSide();
                // ★两类「facing = 贴附面本身」的六向件，不能取反★
                //   1) 挂画（`:painting`）—— Aki 2026-10-28：点南面挂画，画应朝南（朝人），
                //      原来取反成 north ⇒ 画贴到墙里侧、正反颠倒。
                //   2) 雨棚（`:awning_*_plate`）—— Aki 2026-11-04（C111）：同族同语义。
                //      证据链（`awning_blue_plate`）：模型 = `base_plate_all` 的 y 0..3 水平板，
                //      blockstate `facing=up` 无旋转、`facing=north` 给 `x:90`。
                //      vanilla 的 `x:90` 把 +Y 转到 -Z（火把 `facing=north` = x:90 且尖端朝北），
                //      故本族 `facing=north` 时板**就在北面** ⇒ facing = 板所在的面。
                //      旧代码取反 ⇒ 点北面放到南面，正是 Aki 报的「放置反了」。
                //   ⚠ `brick_castlestone_dark_plate` 的 meta flip=1 是水平语义，不受此影响。
                //  3) 贴面薄片族（C107 ②，2026-11-04 第 105 轮）：`*_plate` / `cap_*` / `hat_*` /
                //     `lifebuoys_*` 共 146 个 id —— 官方 `facing=up` variant 无旋转（板躺格底）、
                //     `facing=north` 给 `x:90`（板贴北面）⇒ facing = **被点击面**。
                //     名单在 `assets/blockjs/faceattach.json`（A: boxes 贴底 + B: 官方无旋转，两条都成立），
                //     由 `_c107_plate_place_gen.py` 生成；不按 id 前缀猜，避免卷进反语义的 `*_stick`（210 个）。
                boolean faceLike = boxedId != null
                        && (boxedId.contains(":painting") || boxedId.contains(":awning_")
                            || DynamicPlacement.isFaceAttached(boxedId));
                if (!faceLike) six = six.getOpposite();
                state = state.with(Properties.FACING, six);
            }
        }

        // ★axis（原木/横杆类）：axis = 被点击面所在的轴★
        //   `brick_castlestone_dark_stick` 这类本来用 6 向 facing 表达，Aki 2026-10-27 建议改 axis：
        //   横杆只有"沿哪根轴"三个有效形态，用 facing 会多出 up/down 的重复语义。
        if (state.contains(Properties.AXIS)) {
            state = state.with(Properties.AXIS, ctx.getSide().getAxis());
        }

        // ★click（minia MT_Click* / MT_2ClickVHalfUL）：按点击高度分档★
        //   以前完全没写 ⇒ 恒为默认 0，看起来就是"click 不生效"（Aki 2026-10-27）。
        //   语义：把方块沿 Y 均分成 (max+1) 段，点到第几段就是几（0 在底）。
        Property<?> clickProp = propNamed(state, "click");
        if (clickProp instanceof IntProperty cip && autoClick) {
            int max = cip.getValues().stream().mapToInt(v -> v).max().orElse(0);
            double rel = ctx.getHitPos().y - ctx.getBlockPos().getY();
            int seg = (int) Math.floor(rel * (max + 1));
            if (seg < 0) seg = 0;
            if (seg > max) seg = max;
            state = state.with(cip, seg);
        }

        // ★sides（墙端连接位）：放置时就定好，避免"放下先两端封口、下一帧才变"★
        if (state.contains(SIDES) && state.contains(Properties.HORIZONTAL_FACING)) {
            state = state.with(SIDES, sidesAt(ctx.getWorld(), ctx.getBlockPos(), state,
                    state.get(Properties.HORIZONTAL_FACING)));
            if (state.contains(CAPS)) {
                state = state.with(CAPS, capsAt(ctx.getWorld(), ctx.getBlockPos(), state,
                        state.get(Properties.HORIZONTAL_FACING)));
            }
        }

        // ★type4（沙发/长凳/柜/桌）：按左右邻块算 type=0单/1左/2双/3右★
        //   原来 placement 不写 type ⇒ 恒为默认 0（single），一排摆下去永远不连。
        if ("type4".equals(placeMode)) {
            Property<?> tp0 = propNamed(state, "type");
            int nt0 = sofaTypeAt(ctx.getWorld(), ctx.getBlockPos(),
                    state.get(Properties.HORIZONTAL_FACING));
            if (tp0 instanceof IntProperty tip0) {
                state = state.with(tip0, nt0);
            } else if (tp0 != null) {
                // ★枚举版 type（抽屉族的 slrb：single/left/both/right）★
                state = setByName(state, tp0, sofaTypeName(nt0));
            }
        }

        // ★lrmrow（wood_hpole，C3）：车道按点击在横轴上的三分位；up 默认上沿、潜行按下沿★
        //   旧 position 六值把轴和车道死绑，用户只能沿两条轴、且车道名与朝向无关；
        //   现在 facing 定走向、type 定车道，端头 3.5px 外挑即「端头」。
        if ("lrmrow".equals(placeMode)) {
            Property<?> tpL = propNamed(state, "type");
            if (tpL != null) {
                state = setByName(state, tpL,
                        laneName(hitLane(ctx, state.get(Properties.HORIZONTAL_FACING))));
            }
            Property<?> upL = propNamed(state, "up");
            if (upL instanceof BooleanProperty ubL) {
                // ★C110（第 91 轮 · Aki）★ up 改由**点击位置**决定，不再看潜行。
                //   `ItemPlacementContext#getBlockPos()` 是**放置位**（javap 实证：canReplaceExisting
                //   为假时 = 点击位.offset(side)），所以 `hitY - placeY` 天然落在本格 0..1：
                //     点地面   (side=UP)   ⇒ 0  ⇒ up=false（杆贴下沿，紧贴地面）
                //     点天花板 (side=DOWN) ⇒ 1  ⇒ up=true （杆贴上沿，紧贴天花板）
                //     点侧面              ⇒ 实际高度 ⇒ 点上半格贴上半、下半格贴下半（Aki 明确要求这条）
                double ly = ctx.getHitPos().y - (double) ctx.getBlockPos().getY();
                state = state.with(ubL, ly > 0.5D);
            }
        }

        // ★垂直堆叠（抽屉）★：up/down 两个 boolean 按上下是否同类实时算
        if (vstack) {
            Property<?> upP0 = propNamed(state, "up"), dnP0 = propNamed(state, "down");
            if (upP0 instanceof BooleanProperty ub0) {
                state = state.with(ub0, ctx.getWorld().getBlockState(ctx.getBlockPos().up()).isOf(this));
            }
            if (dnP0 instanceof BooleanProperty db0) {
                state = state.with(db0, ctx.getWorld().getBlockState(ctx.getBlockPos().down()).isOf(this));
            }
        }

        // ★mtshape（墙角石）：按点击落在面内的左/右半边取★        //   官方是 nextInt(3) 随机，固定 0 会永远只有一种半片 —— 看着就像整体转了 180°。
        if ("mtshape".equals(placeMode) || "mtshape_hit".equals(placeMode)) {
            Property<?> mp0 = propNamed(state, "mtshape");
            if (mp0 instanceof IntProperty mip0) {
                state = state.with(mip0, hitHalf(ctx, state.get(Properties.HORIZONTAL_FACING)));
            }
        }

        // connect 板：fence（下方能否当柱子）+ shape（左右邻居）
        if (isConnectBoard(state)) {
            Property<?> feP = propNamed(state, "fence");
            Property<?> shP = propNamed(state, "shape");
            if (feP instanceof BooleanProperty fbp) {
                state = state.with(fbp, fencedBelow(ctx.getWorld(), ctx.getBlockPos()));
            }
            if (shP instanceof IntProperty sip) {
                state = state.with(sip, connectShapeAt(ctx.getWorld(), ctx.getBlockPos(),
                        state.get(Properties.HORIZONTAL_FACING)));
            }
        }

        // ★half：天窗/半砖必须按点击位置算上下★
        //   之前完全没处理 ⇒ 保持 BlockHalf 的枚举首值（TOP），所以天窗只能放出 top。
        //   ★2026-10-27 Aki：点顶面/点底面两个分支反了，互换★
        //   判据同原版台阶：点「顶面」时新方块生成在被点方块**上方**，模型要**坐**在那面方块上
        //   ⇒ 取下半格（BOTTOM）；点「底面」时新方块生成在下方、模型吊在下面 ⇒ 取上半格（TOP）。
        //   侧面按命中高度：>0.5 取上半格。
        if (state.contains(Properties.BLOCK_HALF)) {
            Direction side = ctx.getSide();
            boolean top;
            if (side == Direction.UP) top = false;        // 点顶面 ⇒ 落上半格之上 ⇒ BOTTOM
            else if (side == Direction.DOWN) top = true;  // 点底面 ⇒ 挂在下半格 ⇒ TOP
            // ★C71c/C71d minia `MT_2ClickFacingHUD`：侧面恒 TOP（不看命中高度）★
            else if ("mt2hud".equals(placeMode)) top = true;
            else top = ctx.getHitPos().y - ctx.getBlockPos().getY() > 0.5;
            state = state.with(Properties.BLOCK_HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
        }

        // ★自定义 half 枚举（minia 的 half=upper|lower）★：与 BLOCK_HALF 同判据
        //   原来只处理 Properties.BLOCK_HALF ⇒ 这族永远停在枚举首值，"放不出 TOP"。
        if (!state.contains(Properties.BLOCK_HALF)) {
            Property<?> hp = propNamed(state, "half");
            if (hp instanceof net.minecraft.state.property.EnumProperty<?> ep) {
                java.util.Set<String> names = new java.util.HashSet<>();
                for (Object o : ep.getValues()) {
                    names.add(String.valueOf(o).toLowerCase());
                }
                if (names.contains("upper") && names.contains("lower")) {
                    Direction side = ctx.getSide();
                    boolean up;
                    if (side == Direction.UP) up = false;
                    else if (side == Direction.DOWN) up = true;
                    else up = ctx.getHitPos().y - ctx.getBlockPos().getY() > 0.5;
                    state = setByName(state, hp, up ? "upper" : "lower");
                }
            }
        }

        // on（coc 特有）：下方是楼梯 ⇒ true
        Property<?> onProp = propNamed(state, "on");
        if (onProp instanceof BooleanProperty onBp) {
            BlockState below = ctx.getWorld().getBlockState(ctx.getBlockPos().down());
            state = state.with(onBp, below.getBlock() instanceof net.minecraft.block.StairsBlock);
        }

        // NSEW（玻璃板/栏杆）：按邻居是否同种或实心
        if (state.contains(Properties.NORTH)) {
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH,
                    Direction.WEST, Direction.EAST}) {
                Property<?> p = propNamed(state, d.getName());
                if (p instanceof BooleanProperty bp) {
                    BlockState nb = ctx.getWorld().getBlockState(ctx.getBlockPos().offset(d));
                    boolean on = connectsTo(nb)
                            || (!nsewSameOnly && nb.isSideSolidFullSquare(ctx.getWorld(), ctx.getBlockPos().offset(d), d.getOpposite()));
                    state = state.with(bp, on);
                }
            }
        }

        // C68 树枝：8 个属性一次性按邻居定（上面那圈只认「同种/同族 + 实心」，接不上原木）
        if (isBranchMode()) {
            state = applyBranchState(state, ctx.getWorld(), ctx.getBlockPos());
        }

        // axis（x/y/z）：点上下 ⇒ y，点侧面 ⇒ 水平轴
        if (state.contains(Properties.AXIS)) {
            state = state.with(Properties.AXIS, ctx.getSide().getAxis());
        }

        // type（top / bottom / double）：按点击面与命中高度决定
        state = applyTypeForPlacement(state, ctx);

        // layers（可堆叠）：同格已存在同种方块则层数 +1
        state = applyLayersForPlacement(state, ctx);

        // shape（竖台阶的内/外角）：按相邻同种方块朝向算，放置时就定形
        Property<?> shapeProp = propNamed(state, "shape");
        if (shapeProp != null) {
            state = setByName(state, shapeProp, computeShapeName(state, ctx.getWorld(), ctx.getBlockPos()));
        }

        // 窗帘/挂帘在 shape/click 定稳后再判定上下同一列。
        if (vertical) {
            state = setVerticalForPlacement(state, ctx.getWorld(), ctx.getBlockPos());
        }

        // 含水：放置点本来就是水 ⇒ 直接 true（否则要等流体更新，会出现一帧干块）
        if (hasWater(state)) {
            FluidState fs = ctx.getWorld().getFluidState(ctx.getBlockPos());
            state = state.with(Properties.WATERLOGGED, fs.getFluid() == Fluids.WATER);
        }
        return state;
    }


    // ------------------------------------------------------------ type / layers

    /** 按名字找属性（不假定是 SlabType 枚举，兼容脚本自定义 EnumProperty） */
    private static Property<?> propNamed(BlockState state, String name) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) return p;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setByName(BlockState state, Property<?> prop, String valueName) {
        if (prop == null) return state;
        for (Comparable<?> v : prop.getValues()) {
            if (String.valueOf(v).equalsIgnoreCase(valueName)) {
                return state.with((Property) prop, (Comparable) v);
            }
        }
        return state;
    }

    private static String nameOf(BlockState state, Property<?> prop) {
        if (prop == null) return "";
        return String.valueOf(state.get(prop)).toLowerCase();
    }

    /**
     * type 放置判定：
     * <ul>
     *   <li>点上面 → bottom；点下面 → top；</li>
     *   <li>点侧面 → 命中点 y 在格内下半 → bottom，上半 → top（与半砖手感一致）。</li>
     * </ul>
     * 已有的另一半相邻时由 {@link #getStateForNeighborUpdate} 合成 double。
     */
    private BlockState applyTypeForPlacement(BlockState state, ItemPlacementContext ctx) {
        Property<?> typeProp = propNamed(state, "type");
        if (typeProp == null) return state;

        // ★原版台阶逻辑★ 落点已经是同种方块且不是 double ⇒ 直接合成 double
        //   这条覆盖"对着侧面放"：目标格已有 top 或 bottom，就并成 double
        // ★只有真 SlabType 才有 double★：type=0..3 的家具（沙发/桌/柜）走到这里时
        //   setByName(...,"double") 本来就会原样返回，等于把手里那格白白消耗掉。
        if (typeProp == Properties.SLAB_TYPE) {
            BlockState existingHere = ctx.getWorld().getBlockState(ctx.getBlockPos());
            if (existingHere.isOf(this)
                    && existingHere.get(Properties.SLAB_TYPE) != net.minecraft.block.enums.SlabType.DOUBLE) {
                return existingHere.with(Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.DOUBLE);
            }
        }

        Direction side = ctx.getSide();
        String target;
        if (side == Direction.UP) {
            target = "bottom";
        } else if (side == Direction.DOWN) {
            target = "top";
        } else {
            double hitY = ctx.getHitPos().y - ctx.getBlockPos().getY();
            target = hitY > 0.5 ? "top" : "bottom";
        }
        return setByName(state, typeProp, target);
    }

    /** 同格叠加：点到已有同种方块且带 layers → 层数 +1（到顶就保持） */
    private BlockState applyLayersForPlacement(BlockState state, ItemPlacementContext ctx) {
        Property<?> layersProp = propNamed(state, "layers");
        if (!(layersProp instanceof IntProperty ip)) return state;

        // ★ 潜行 = 不叠层，放新方块（原版半砖同手感）★
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) return state;

        BlockState existing = ctx.getWorld().getBlockState(ctx.getBlockPos());
        if (!existing.isOf(this)) return state;
        int cur = existing.get(ip);
        int max = ip.getValues().stream().mapToInt(v -> v).max().orElse(cur);
        if (cur >= max) return existing;
        return existing.with(ip, Math.min(cur + 1, max));
    }

    // ------------------------------------------------------------ 相邻合并 double

    /**
     * top + bottom 相邻（同一 y 不能同时存在两种，所以只能上下叠）时合成 double。
     * 只处理「本格是 top 且下方是 bottom 同种方块」这一种情况，避免过度推断。
     */
    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (hasWater(state)) {
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        }

        // on 重算（下方换了方块）
        Property<?> onProp2 = propNamed(state, "on");
        if (onProp2 instanceof BooleanProperty onBp2) {
            BlockState below = world.getBlockState(pos.down());
            state = state.with(onBp2, below.getBlock() instanceof net.minecraft.block.StairsBlock);
        }

        // NSEW 重算：★只重算方向 == direction 的那一面★
        //   旧写法拿同一个 neighborState 把四向全刷一遍 —— 于是"旁边放了一块石头"
        //   会让 north/south/east/west 全部变 true（Aki 2026-10-26 报的"四向都变 true"就是这个）。
        if (state.contains(Properties.NORTH) && direction.getAxis().isHorizontal()) {
            Property<?> p = propNamed(state, direction.getName());
            if (p instanceof BooleanProperty bp) {
                boolean on = connectsTo(neighborState)
                        || (!nsewSameOnly && neighborState.isSideSolidFullSquare(world, neighborPos, direction.getOpposite()));
                state = state.with(bp, on);
            }
        }

        // C68 树枝：官方只在「上下邻居变了」时重算（javap：非竖直轴直接 return），本地放宽成任意邻居变化都重算
        // —— 拆掉旁边的原木要能立刻断开，否则要等下一次上下变化才更新。
        if (isBranchMode()) {
            state = applyBranchState(state, world, pos);
        }

        // type4：左右邻居变了要重算（否则拆掉中间一节，两端还保持 left/right）
        if ("type4".equals(placeMode) && direction.getAxis().isHorizontal()
                && state.contains(Properties.HORIZONTAL_FACING)) {
            Property<?> tp2 = propNamed(state, "type");
            int nt = sofaTypeAt(world, pos, state.get(Properties.HORIZONTAL_FACING));
            if (tp2 instanceof IntProperty tip2) {
                if (state.get(tip2) != nt) state = state.with(tip2, nt);
            } else if (tp2 != null) {
                String nm = sofaTypeName(nt);
                if (!nameOf(state, tp2).equals(nm)) state = setByName(state, tp2, nm);
            }
        }

        // ★垂直堆叠（抽屉）★：上下邻居变了就重算 up/down
        if (vstack && direction.getAxis().isVertical()) {
            Property<?> upP2 = propNamed(state, "up"), dnP2 = propNamed(state, "down");
            if (upP2 instanceof BooleanProperty ub2) {
                state = state.with(ub2, world.getBlockState(pos.up()).isOf(this));
            }
            if (dnP2 instanceof BooleanProperty db2) {
                state = state.with(db2, world.getBlockState(pos.down()).isOf(this));
            }
        }

        // connect 板：fence 看下方、shape 看左右邻居（同族 + 同 facing）
        if (isConnectBoard(state)) {
            Property<?> feP2 = propNamed(state, "fence");
            Property<?> shP2 = propNamed(state, "shape");
            if (feP2 instanceof BooleanProperty fbp2 && direction == Direction.DOWN) {
                state = state.with(fbp2, fencedBelow(world, pos));
            }
            if (shP2 instanceof IntProperty sip2 && direction.getAxis().isHorizontal()) {
                state = state.with(sip2, connectShapeAt(world, pos, state.get(Properties.HORIZONTAL_FACING)));
            }
        }

        // shape 重算（邻居朝向变了 / 邻居被拆）
        Property<?> shapeProp = propNamed(state, "shape");
        if (shapeProp != null) {
            state = setByName(state, shapeProp, computeShapeName(state, world, pos));
        }

        Property<?> neighborShape = propNamed(state, "shape");
        if (neighborShape != null) {
            state = setByName(state, neighborShape, computeShapeName(state, world, pos));
        }

        // vertical 可能因 click/shape 改变而变化，所以任何邻居更新都在 shape 后重算。
        if (vertical) {
            state = setVerticalForPlacement(state, world, pos);
        }

        Property<?> typeProp = propNamed(state, "type");
        if (typeProp != null && direction == Direction.DOWN
                && neighborState.isOf(this) && nameOf(neighborState, typeProp).equals("bottom")
                && nameOf(state, typeProp).equals("top")) {
            return setByName(state, typeProp, "double");
        }
        // ★sides（墙端连接位）：只有带这个属性的族（window_arch 的 vslab）才算★
        //   原来完全没有 ⇒ 顶面永远用「左右都连着」的图，墙的断头处看不出封口。
        //   Aki 2026-10-27 20:26 报「straight 的左右端没有使用 03_top_left / 04_top_right」。
        if (state.contains(SIDES)
                && state.contains(Properties.HORIZONTAL_FACING)) {
            state = state.with(SIDES, sidesAt(world, pos, state,
                    state.get(Properties.HORIZONTAL_FACING)));
            if (state.contains(CAPS)) {
                state = state.with(CAPS, capsAt(world, pos, state,
                        state.get(Properties.HORIZONTAL_FACING)));
            }
        }

        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    /** 墙端连接位：bit0 = 模型 +x 侧连着，bit1 = 模型 -x 侧连着（见 {@link #SIDES}） */
    private int sidesAt(BlockView world, BlockPos pos, BlockState state, Direction facing) {
        // 端头方向随 shape 变：
        //   straight          → 左/右（模型 ±x）
        //   inner_*/outer_*   → 视觉正面(facing 反方向) + 视觉左/右（拐角的两条腿）
        // 注意：facing 属性和模型视觉朝向差 180°（blockstate 里 facing=south 时 y=180、视觉朝北），
        // 所以「视觉正面」= facing.getOpposite()。直段那两个方向是左右对称的，不受这个 180° 影响。
        Property<?> sp = propNamed(state, "shape");
        String sh = sp == null ? "straight" : nameOf(state, sp);
        Direction a;
        Direction b;
        if (sh.startsWith("inner") || sh.startsWith("outer")) {
            a = facing.getOpposite();
            b = sh.endsWith("_right") ? facing.rotateYCounterclockwise()
                                      : facing.rotateYClockwise();
        } else {
            a = facing.rotateYClockwise();
            b = facing.rotateYCounterclockwise();
        }
        boolean pa = joinsWallSide(world.getBlockState(pos.offset(a)), state);
        boolean pb = joinsWallSide(world.getBlockState(pos.offset(b)), state);
        // 竖直方向（Aki 2026-10-28 07:12）：上下堆叠也要能去掉横框
        //   bit2 = 上方有同族、bit3 = 下方有同族
        boolean pu = joinsWallSide(world.getBlockState(pos.up()), state);
        boolean pd = joinsWallSide(world.getBlockState(pos.down()), state);
        return (pa ? 1 : 0) | (pb ? 2 : 0) | (pu ? 4 : 0) | (pd ? 8 : 0);
    }

    /** 侧向邻居算不算「墙继续」：同一种方块（朝向不限，拐角也算继续）或同连接族 */
    /** 侧向邻居算不算「墙继续」
     *
     * <p>凸角（outer_left / outer_right）**不算**：它是墙的端头帽，墙在那里改方向，
     * 不该把邻接直段的竖框抹掉（Aki 2026-10-28 06:26：贴图只和 straight/inner 连，不和 outer 连）。
     * 拐角 inward（inner_*）算继续 —— 内拐角的两条腿本来就是墙的一部分。
     */
    private boolean joinsWallSide(BlockState nb, BlockState self) {
        if (!nb.contains(Properties.HORIZONTAL_FACING)) return false;
        if (!nb.isOf(self.getBlock()) && !connectsTo(nb)) return false;
        Property<?> sp = propNamed(nb, "shape");
        return sp == null || !nameOf(nb, sp).startsWith("outer");
    }

    /** 端头位：bit0 = +x 侧邻块是凸角端头，bit1 = -x 侧（见 {@link #CAPS}） */
    private int capsAt(BlockView world, BlockPos pos, BlockState state, Direction facing) {
        boolean plus = isEndCap(world.getBlockState(pos.offset(facing.rotateYClockwise())), state);
        boolean minus = isEndCap(world.getBlockState(pos.offset(facing.rotateYCounterclockwise())), state);
        return (plus ? 1 : 0) | (minus ? 2 : 0);
    }

    /** 邻块是不是「墙端头」：同一种方块且 shape = outer_left / outer_right
     *
     * <p>inner 暂时**不算**：内角那半到底取左 1/3 还是右 1/3 还没定（Aki 正在比 4 种组合），
     * 定了再把 inner 加进来，并把自动值对准那一版，否则会算出与美术相反的 caps。 */
    private boolean isEndCap(BlockState nb, BlockState self) {
        if (!nb.isOf(self.getBlock())) return false;
        Property<?> sp = propNamed(nb, "shape");
        if (sp == null) return false;
        String n = nameOf(nb, sp);
        return "outer_left".equals(n) || "outer_right".equals(n);
    }


    // ------------------------------------------------------------ shape 连接（抄 StairsBlock 的算法）

    /**
     * 按原版 {@code StairsBlock#getStairShape} 的规则算 shape：
     * <pre>
     * 前面是同种、朝向垂直且「可成角」 ⇒ INNER_LEFT / INNER_RIGHT
     * 后面同上                          ⇒ OUTER_LEFT / OUTER_RIGHT
     * 否则                              ⇒ STRAIGHT
     * </pre>
     * 返回属性值名字（与 blockstate 里的 straight / inner_left / … 一致）。
     */
    /** 同族/同一方块的沙发类邻块判定（还要求 facing 相同，否则 L 形摆放会乱连） */
    private boolean joinsSofa(BlockState nb, Direction facing) {
        if (!nb.contains(Properties.HORIZONTAL_FACING)) return false;
        if (nb.get(Properties.HORIZONTAL_FACING) != facing) return false;
        return nb.isOf(this) || connectsTo(nb);
    }

    /**
     * 沙发/长凳/柜/桌的 {@code type}：0=单 1=左 2=双 3=右。
     *
     * <p>左右方向实测自模型（{@code sofa_vintage_black_left} 的扶手在 +x 侧，
     * 以 facing=north 为基准）：扶手在"朝向的逆时针侧"= left，顺时针侧 = right。
     */
    /** sofaTypeAt 的 int → 枚举名（单/左/双/右） */
    private static String sofaTypeName(int t) {
        return t == 1 ? "left" : (t == 2 ? "both" : (t == 3 ? "right" : "single"));
    }

    private int sofaTypeAt(BlockView world, BlockPos pos, Direction facing) {
        boolean l = joinsSofa(world.getBlockState(pos.offset(facing.rotateYCounterclockwise())), facing);
        boolean r = joinsSofa(world.getBlockState(pos.offset(facing.rotateYClockwise())), facing);
        if (l && r) return 2;
        if (l) return 1;
        if (r) return 3;
        return 0;
    }

    /**
     * 墙角石 {@code mtshape}：0 / 1 —— 按命中点在面内「垂直于朝向」的轴上的位置取。
     * facing 沿 z 轴时看 x，沿 x 轴时看 z。
     */
    private int hitHalf(net.minecraft.item.ItemPlacementContext ctx, Direction f) {
        net.minecraft.util.math.Vec3d h = ctx.getHitPos();
        BlockPos p = ctx.getBlockPos();
        double local = (f.getAxis() == Direction.Axis.Z) ? h.x - p.getX() : h.z - p.getZ();
        return local > 0.5 ? 1 : 0;
    }

    /**
     * 水平杆 {@code lrmrow}（wood_hpole）：车道三分位。站在 facing 方向看过去的左/中/右。
     * facing 沿 z 轴时看 x（facing=south 镜像），沿 x 轴时看 z（facing=west 镜像）。
     */
    private int hitLane(net.minecraft.item.ItemPlacementContext ctx, Direction f) {
        net.minecraft.util.math.Vec3d h = ctx.getHitPos();
        BlockPos p = ctx.getBlockPos();
        double local = (f.getAxis() == Direction.Axis.Z) ? h.x - p.getX() : h.z - p.getZ();
        if (f == Direction.SOUTH || f == Direction.WEST) local = 1.0D - local;
        if (local < 1.0D / 3.0D) return 0;                 // left
        if (local > 2.0D / 3.0D) return 2;                 // right
        return 1;                                          // middle
    }

    private static String laneName(int lane) {
        return lane == 0 ? "left" : (lane == 2 ? "right" : "middle");
    }

    private String computeShapeName(BlockState state, BlockView world, BlockPos pos) {
        Property<?> facingProp = propNamed(state, "facing");
        if (facingProp == null) return "straight";
        Direction facing = state.get((net.minecraft.state.property.DirectionProperty) facingProp);
        // ★货架/床架的 16 值段位要先判★（它也用 placeMode=vconnect 定朝向，但 shape 语义不是 vsec）
        if ("shelf".equals(shapeRule)) return computeShelfShape(state, world, pos, facing, facingProp);
        if ("vconnect".equals(placeMode) || "vconnect_self".equals(placeMode))
            return computeVerticalShape(state, world, pos, facing, facingProp);
        if ("mt".equals(shapeRule)) return computeShapeNameMT(state, world, pos, facing, facingProp);
        // ★C15-2 第 16 组（第 42 轮）：沙发（DynamicEnums.SOFA8）没有 `straight`，只有 `m_straight`/`single`
        //   —— mt 规则产出 `straight` 直接 setByName 会写坏属性 ⇒ 映射成 `single`（直排单片，与本地旧观感一致）。
        if ("sofa".equals(shapeRule)) {
            String mt = computeShapeNameMT(state, world, pos, facing, facingProp);
            return "straight".equals(mt) ? "single" : mt;
        }
        // ★C101（第 92 轮）：minia `roof_*_stairs_*` 用的 8 值 shape 是「有 m_straight、没有 straight」
        //   （官方 51 个 `mt_roof_*_stairs_*.json` 逐份实证：128 键 = click2×facing4×half2×shape8，
        //    shape ∈ {single,l_straight,m_straight,r_straight,4 角}）⇒ mt 规则产出的 `straight`
        //    必须落成 `m_straight`（中段），否则 setByName 写坏属性。
        if ("mt8".equals(shapeRule)) {
            String mt = computeShapeNameMT(state, world, pos, facing, facingProp);
            return "straight".equals(mt) ? "m_straight" : mt;
        }
        if ("slrm".equals(shapeRule)) {
            // 4 值（single/left/right/middle）：与连接板同一套左右判定（左 = 朝向逆时针侧）
            return new String[]{"single", "left", "right", "middle"}[connectShapeAt(world, pos, facing)];
        }

        // ★竖台阶（placeMode="vslab"）：前邻居→OUTER、后邻居→INNER★
        //
        // ★这是按「模型几何」定的，不是按原版楼梯★（闸门 `_w16_gate_vslab_shape.py` 的约定表）：
        //     inner_left  = 北条 + **西南柱**（x0..8, z8..16）⇒ 角柱在**后**侧偏西
        //     outer_left  = **西北 1/4**（x0..8, z0..8）      ⇒ 角块在**前**侧偏西
        //   所以：前邻居（pos.offset(facing)）朝西 ⇒ outer_left；后邻居朝西 ⇒ inner_left。
        //
        // 2026-10-27 02:4x 那一版把两者对调过，但当时**只换了返回的字符串、没换邻居判定**，
        // 结果"应该是 outer 的变成 inner" ⇒ 又被改了回去，闸门的约定却一直是 front→outer。
        // Aki 2026-10-27 16:29「几乎所有拐角都有问题」就是这段与闸门脱节的后果。
        // ★原版 getStairShape 的两个前置条件都要有★：邻居必须**同 half** 才成角。
        //   原来只判了「isOf(this) + 轴不同」，缺 half 相等 ⇒
        //   上段件与下段件（half=top / bottom）相接时也会被算成拐角 ⇒ 拐角朝向错。
        BlockState front = world.getBlockState(pos.offset(facing));
        if (front.isOf(this) && front.contains((net.minecraft.state.property.DirectionProperty) facingProp)
                && sameShapeHalf(state, front)) {
            Direction frontFacing = front.get((net.minecraft.state.property.DirectionProperty) facingProp);
            if (frontFacing.getAxis() != facing.getAxis()
                    && canTakeShape(state, world, pos, frontFacing, facingProp)) {
                boolean left = frontFacing == facing.rotateYCounterclockwise();
                return left ? "outer_left" : "outer_right";
            }
        }

        BlockState back = world.getBlockState(pos.offset(facing.getOpposite()));
        if (back.isOf(this) && back.contains((net.minecraft.state.property.DirectionProperty) facingProp)
                && sameShapeHalf(state, back)) {
            Direction backFacing = back.get((net.minecraft.state.property.DirectionProperty) facingProp);
            if (backFacing.getAxis() != facing.getAxis()
                    && canTakeShape(state, world, pos, backFacing, facingProp)) {
                boolean left = backFacing == facing.rotateYCounterclockwise();
                return left ? "inner_left" : "inner_right";
            }
        }
        return "straight";
    }

    /**
     * 垂直分段（minia {@code MT_FacingConnectV#updateShape} 的等价实现）。
     *
     * <p>javap 原文分支：
     * <pre>
     *   above = canConnectTo(pos.above());  below = canConnectTo(pos.below());
     *   above &amp;&amp; below  → MIDDLE
     *   above &amp;&amp; !below → BOTTOM
     *   !above &amp;&amp; below → TOP
     *   否则            → SINGLE
     * </pre>
     * {@code canConnectTo} 要求：同一个方块类 + 同一个 descriptionId + <b>facing 相同</b>。
     */
    private String computeVerticalShape(BlockState state, BlockView world, BlockPos pos,
                                        Direction facing, Property<?> facingProp) {
        boolean up = connectsVertical(world.getBlockState(pos.up()), state, facingProp);
        boolean down = connectsVertical(world.getBlockState(pos.down()), state, facingProp);
        if (up && down) return "middle";
        if (up) return "bottom";
        if (down) return "top";
        return "single";
    }

    private boolean connectsVertical(BlockState nb, BlockState self, Property<?> facingProp) {
        if (!nb.isOf(self.getBlock())) return false;
        if (facingProp instanceof net.minecraft.state.property.DirectionProperty dp) {
            return nb.contains(dp) && nb.get(dp) == self.get(dp);
        }
        return true;
    }

    /**
     * 货架 / 床架（minia {@code MT_FacingShape} 系列）的 16 值段位。
     *
     * <p>取值 = 水平段（无 / l / m / r）× 垂直段（s / b / m / t）：
     * 无水平邻块时是 {@code single|bottom|middle|top}，有则拼成 {@code lb|l m|ls|lt} 这类。
     * 左 = facing 的逆时针侧（与连接板/楼梯同一套约定）。
     */
    private String computeShelfShape(BlockState state, BlockView world, BlockPos pos,
                                     Direction facing, Property<?> facingProp) {
        boolean up = connectsVertical(world.getBlockState(pos.up()), state, facingProp);
        boolean down = connectsVertical(world.getBlockState(pos.down()), state, facingProp);
        char v = (up && down) ? 'm' : (up ? 'b' : (down ? 't' : 's'));
        boolean left = connectsVertical(world.getBlockState(pos.offset(facing.rotateYCounterclockwise())), state, facingProp);
        boolean right = connectsVertical(world.getBlockState(pos.offset(facing.rotateYClockwise())), state, facingProp);
        if (!left && !right) {
            return v == 'b' ? "bottom" : v == 'm' ? "middle" : v == 't' ? "top" : "single";
        }
        char hp = (left && right) ? 'm' : (left ? 'l' : 'r');
        return String.valueOf(hp) + v;
    }

    /**
     * 自动 vertical 分段。数值/枚举名对应 single, upper, middle, lower。
     *
     * <p>方块类相同 + facing 相同是基础条件；click（同款帘头）与 shape（横向帘段）
     * 也必须相同，否则不同纹理/造型的帘会被错误接成一整列。
     */
    private BlockState setVerticalForPlacement(BlockState state, BlockView world, BlockPos pos) {
        Property<?> vp = propNamed(state, "vertical");
        if (vp == null) return state;
        Property<?> fp = propNamed(state, "facing");
        Direction facing = fp instanceof DirectionProperty dp && state.contains(dp) ? state.get(dp) : null;

        boolean up = connectsAutoVertical(world.getBlockState(pos.up()), state, facing);
        boolean down = connectsAutoVertical(world.getBlockState(pos.down()), state, facing);
        int value = up && down ? 2 : (down ? 1 : (up ? 3 : 0));
        if (vp instanceof IntProperty ip) {
            return state.with(ip, value);
        }
        return setByName(state, vp, new String[]{"single", "upper", "middle", "lower"}[value]);
    }

    private boolean connectsAutoVertical(BlockState nb, BlockState self, Direction facing) {
        if (!nb.isOf(self.getBlock())) return false;
        Property<?> fp = propNamed(self, "facing");
        if (fp instanceof DirectionProperty dp && (!nb.contains(dp) || nb.get(dp) != facing)) return false;
        for (String key : new String[]{"click", "shape"}) {
            Property<?> sp = propNamed(self, key);
            Property<?> np = propNamed(nb, key);
            if (sp == null && np == null) continue;
            if (sp == null || np == null || !nameOf(self, sp).equals(nameOf(nb, np))) return false;
        }
        return true;
    }

    /**
     * miniaturia {@code MT_VHalf#getStairsShape} / {@code MT_2ClickVHalfUL#getClickShape} 的等价实现。
     *
     * <p>javap 逐条对照（两个类的首分支都是 OUTER、次分支都是 INNER）：
     * <pre>
     *   front = pos.relative(facing)
     *   if sameName(front) && front.axis != facing.axis && differentStairs(pos, frontFacing.opposite)
     *        → frontFacing == facing.counterClockWise ? outer_left : outer_right
     *   back  = pos.relative(facing.opposite)
     *   if sameName(back)  && back.axis  != facing.axis && differentStairs(pos, backFacing)
     *        → backFacing  == facing.counterClockWise ? inner_left : inner_right
     *   else straight
     * </pre>
     * {@code sameName} 官方比的是 block 的 descriptionId + click；我们不看 click（已从属性里去掉），
     * 所以退化成"同一个方块"。
     */
    private String computeShapeNameMT(BlockState state, BlockView world, BlockPos pos,
                                      Direction facing, Property<?> facingProp) {
        BlockState front = world.getBlockState(pos.offset(facing));
        if (front.isOf(this) && front.contains((net.minecraft.state.property.DirectionProperty) facingProp)) {
            Direction ff = front.get((net.minecraft.state.property.DirectionProperty) facingProp);
            if (ff.getAxis() != facing.getAxis() && differentStairs(state, world, pos, ff.getOpposite())) {
                return ff == facing.rotateYCounterclockwise() ? "outer_left" : "outer_right";
            }
        }
        BlockState back = world.getBlockState(pos.offset(facing.getOpposite()));
        if (back.isOf(this) && back.contains((net.minecraft.state.property.DirectionProperty) facingProp)) {
            Direction bf = back.get((net.minecraft.state.property.DirectionProperty) facingProp);
            if (bf.getAxis() != facing.getAxis() && differentStairs(state, world, pos, bf)) {
                return bf == facing.rotateYCounterclockwise() ? "inner_left" : "inner_right";
            }
        }
        return "straight";
    }

    /** 官方 isDifferentStairs：沿 dir 的那块"不是同种同朝向"才允许成角 */
    private boolean differentStairs(BlockState state, BlockView world, BlockPos pos, Direction dir) {
        BlockState nb = world.getBlockState(pos.offset(dir));
        if (!nb.isOf(this)) return true;
        Property<?> fp = propNamed(state, "facing");
        if (!(fp instanceof net.minecraft.state.property.DirectionProperty)) return true;
        return nb.get((net.minecraft.state.property.DirectionProperty) fp)
                != state.get((net.minecraft.state.property.DirectionProperty) fp);
    }

    /**
     * 邻居与本块是否同一个 {@code half}（原版 {@code getStairShape} 的前置条件之一）。
     *
     * <p>没有 half 属性的族（纯朝向件）恒为 true ⇒ 行为不变。
     */
    private boolean sameShapeHalf(BlockState self, BlockState nb) {
        Property<?> hp = propNamed(self, "half");
        if (hp == null || !nb.contains(hp)) return true;
        return java.util.Objects.equals(nb.get(hp), self.get(hp));
    }

    /** 原版 canTakeShape：目标方向的方块不是同种，或朝向不同，才允许成角 */
    private boolean canTakeShape(BlockState state, BlockView world, BlockPos pos, Direction face,
                                 Property<?> facingProp) {
        BlockState other = world.getBlockState(pos.offset(face));
        if (!other.isOf(this)) return true;
        // ★与 vanilla `StairsBlock#canTakeShape` 一致：只有「邻居朝向我」才算面对面、不能成角。
        //   原来写的是 `邻居.facing != 我.facing`（方向**相同**就 false）⇒
        //   拼好的角再往旁边续一块同向的，角立刻被打回 straight
        //   （Aki 2026-10-27 16:38「再加一块之后就变成了 straight 而不是 outer」）。
        return other.get((net.minecraft.state.property.DirectionProperty) facingProp)
                != state.get((net.minecraft.state.property.DirectionProperty) facingProp).getOpposite();
    }

    // ------------------------------------------------------------ layers 厚度

    /** layers 属性 → 高度比例（1..max）。没有该属性返回 -1。 */
    private float layersRatio(BlockState state) {
        Property<?> p = propNamed(state, "layers");
        if (!(p instanceof IntProperty ip)) return -1f;
        int cur = state.get(ip);
        int max = ip.getValues().stream().mapToInt(v -> v).max().orElse(cur);
        return max <= 0 ? -1f : (float) cur / max;
    }

    /** 十字植被（kind=cross）：无碰撞，轮廓给一个中心小盒（与花卉一致，保证能选中/敲掉） */
    private static final VoxelShape CROSS_OUTLINE =
            VoxelShapes.cuboid(0.3, 0.0, 0.3, 0.7, 0.8, 0.7);

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if ("cross".equals(kind)) return CROSS_OUTLINE;
        VoxelShape s = computedShape(state);
        return s != null ? s : super.getOutlineShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (noCollision) return VoxelShapes.empty();
        if ("cross".equals(kind)) return VoxelShapes.empty();
        // ★fc：碰撞整格，轮廓（getOutlineShape）仍走盒表★
        if (fullCollision) return VoxelShapes.fullCube();
        // ★C139：碰撞覆盖通道★ 官方轮廓与碰撞是两套方法（`m_5909_/m_5940_` vs `m_5939_`），
        // 单条 boxes.json 表达不了「某状态碰撞空但轮廓非空」⇒ 先查可选覆盖表，命中即用（含空表项）。
        // null = 本表没管 ⇒ 继续走原来的 computedShape，对既有 id 零影响。
        if ("boxed".equals(kind)) {
            VoxelShape c = BoxTable.queryCollision(boxedId, state);
            if (c != null) return c;
        }
        VoxelShape s = computedShape(state);
        return s != null ? s : super.getCollisionShape(state, world, pos, context);
    }

    /**
     * 按属性算几何盒（只有带对应属性的类型才会命中）：
     * <ul>
     *   <li>{@code layers} → 高度 = layers / max；</li>
     *   <li>{@code type}   → bottom 下半 / top 上半 / double 整块；</li>
     *   <li>{@code facing} → 竖类：沿朝向轴占一半（配合 shape 再补角）。</li>
     * </ul>
     */
    private VoxelShape computedShape(BlockState state) {
        VoxelShape base0 = computedShapeRaw(state);
        if (base0 != null) {
            Property<?> onProp = propNamed(state, "on");
            if (onProp instanceof BooleanProperty bp && state.get(bp)) {
                return base0.offset(0, -0.5, 0);   // 放在台阶上 ⇒ 整体下沉半格
            }
        }
        return base0;
    }

    private VoxelShape computedShapeRaw(BlockState state) {
        Direction hf = state.contains(Properties.HORIZONTAL_FACING)
                ? state.get(Properties.HORIZONTAL_FACING) : Direction.NORTH;

        // ---------------- 0) kind 指定的特殊几何 ----------------
        // ★boxed：直接吃旧版 BLOCKJS_RULES 的真值盒★（vslab / stair_v / vstairs / skylight_half / slanted）
        // 查不到再回落到下面按 facing/shape 自算——绝不静默丢盒。
        if ("boxed".equals(kind)) {
            VoxelShape boxed = BoxTable.query(boxedId, state);
            if (boxed != null) return boxed;
        } else if (!kind.isEmpty()) {
            switch (kind) {
                case "slope": {
                    VoxelShape s0 = VoxelShapes.empty();
                    for (int i = 0; i < 4; i++) {
                        double d0 = i / 4.0, d1 = (i + 1) / 4.0, h = (i + 1) / 4.0;
                        s0 = VoxelShapes.union(s0, switch (hf) {
                            case NORTH -> VoxelShapes.cuboid(0, 0, 1 - d1, 1, h, 1 - d0);
                            case SOUTH -> VoxelShapes.cuboid(0, 0, d0, 1, h, d1);
                            case WEST  -> VoxelShapes.cuboid(1 - d1, 0, 0, 1 - d0, h, 1);
                            default    -> VoxelShapes.cuboid(d0, 0, 0, d1, h, 1);
                        });
                    }
                    return s0;
                }
                case "cylinder": {
                    double r = 0.42, k = 0.30;
                    return VoxelShapes.union(
                            VoxelShapes.cuboid(0.5 - r, 0, 0.5 - r, 0.5 + r, 1, 0.5 + r),
                            VoxelShapes.union(
                                    VoxelShapes.cuboid(0.5 - k, 0, 0.5 - r - 0.06, 0.5 + k, 1, 0.5 + r + 0.06),
                                    VoxelShapes.cuboid(0.5 - r - 0.06, 0, 0.5 - k, 0.5 + r + 0.06, 1, 0.5 + k)));
                }
                case "plate":      return decorShift(state, plateShape(hf, 2));
                case "plate4":     return decorShift(state, plateShape(hf, 4));
                case "block":      return VoxelShapes.fullCube();
                // C68 树枝：官方 `LogBranch.m_5940_`/`m_5939_` **同一个常量**
                // `Shapes.cuboid(0.25, 0, 0.25, 0.75, 1, 0.75)`（4×16×4 中柱），与 8 个属性无关。
                case "branchcol":  return VoxelShapes.cuboid(0.25, 0, 0.25, 0.75, 1, 0.75);
                // 半块：天窗 / 斜窗（skylight_half、window_slanted）。
                // 真值来自旧版 BLOCKJS_RULES：half=bottom → [0,0,0,16,8,16]，half=top → [0,8,0,16,16,16]，
                // 与 facing/shape 无关。用楼梯类会得到阶梯形，是错的。
                case "halfslab": {
                    boolean top = false;
                    Property<?> hp = propNamed(state, "half");
                    if (hp != null) {
                        Comparable<?> v = state.get(hp);
                        top = v != null && "top".equals(v.toString());
                    }
                    return top ? VoxelShapes.cuboid(0, 0.5, 0, 1, 1, 1)
                               : VoxelShapes.cuboid(0, 0, 0, 1, 0.5, 1);
                }
                case "pole":       return poleShape(hf, state);
                case "layered":    return layersShape(state, false, hf);
                case "layeredface":return layersShape(state, true, hf);
                case "curtain":    return decorShift(state, curtainShape(hf, state));
                case "rug":        return VoxelShapes.cuboid(0, 0, 0, 1, 1.0 / 16, 1);
                case "seat":       return decorShift(state, seatShape(hf));
                case "table":      return decorShift(state, tableShape(hf));
                case "lamp":       return decorShift(state, lampShape(hf));
                case "frame":      return decorShift(state, frameShape(hf));
                case "arch":       return decorShift(state, archShape(hf));
                case "arch2":      return arch2Shape(hf, state);
                // C121：minia 官方两套拱（TypeArch / TypeArchSingle）—— 与 coc 的 arch2 语义不同，单独实现
                //   archmt  = TypeArch（facing×half×shape5，40 状态）
                //   stairmt = TypeArchSingle ← TypeStairs（vanilla 直梯，facing×half）
                case "archmt":     return archmtShape(hf, state);
                case "stairmt":    return stairmtShape(hf, state);
                case "ladder":     return plateShape(hf.getOpposite(), 3.2);
                case "flower":     return decorShift(state, flowerShape(hf));
                case "sofa":       return decorShift(state, sofaShape(hf));
                case "doubleside": return decorShift(state, doubleShape(hf));
                case "arm":        return decorShift(state, armShape(hf, state));
                case "strip": {
                    Property<?> sp = propNamed(state, "shape");
                    String sh = sp == null ? "straight" : nameOf(state, sp);
                    return stripBar(hf, crossSize <= 0 ? 8 : crossSize, sh);
                }
                default:
                    break;
            }
        }

        // ---------------- 1) 轴向条 / 杆（axis + crossSize） ----------------
        if (crossSize > 0 && state.contains(Properties.AXIS)) {
            float h = crossSize / 32f;
            float lo = 0.5f - h, hi = 0.5f + h;
            return switch (state.get(Properties.AXIS)) {
                case X -> VoxelShapes.cuboid(0, lo, lo, 1, hi, hi);
                case Y -> VoxelShapes.cuboid(lo, 0, lo, hi, 1, hi);
                case Z -> VoxelShapes.cuboid(lo, lo, 0, hi, hi, 1);
            };
        }

        // ---------------- 2) NSEW 板类（pane / connect / panel / lattice） ----------------
        if (state.contains(Properties.NORTH)) {
            double lo = 0.5 - 2.0 / 32.0, hi = 0.5 + 2.0 / 32.0;
            boolean n = state.get(Properties.NORTH), sS = state.get(Properties.SOUTH);
            boolean w = state.get(Properties.WEST), e = state.get(Properties.EAST);
            VoxelShape shape = VoxelShapes.cuboid(lo, 0, lo, hi, 1, hi);
            if (n) shape = VoxelShapes.union(shape, VoxelShapes.cuboid(lo, 0, 0, hi, 1, hi));
            if (sS) shape = VoxelShapes.union(shape, VoxelShapes.cuboid(lo, 0, lo, hi, 1, 1));
            if (w) shape = VoxelShapes.union(shape, VoxelShapes.cuboid(0, 0, lo, hi, 1, hi));
            if (e) shape = VoxelShapes.union(shape, VoxelShapes.cuboid(lo, 0, lo, 1, 1, hi));
            return shape;
        }

        // ---------------- 3) 梁柱 / 梁（Segment 型 type：left/middle/right） ----------------
        Property<?> segProp = propNamed(state, "type");
        boolean segTyped = segProp != null && segProp.getValues().stream()
                .anyMatch(v -> "left".equalsIgnoreCase(String.valueOf(v)));
        Property<?> halfProp = propNamed(state, "half");
        if (segTyped) {
            if (halfProp != null) {
                // 梁：8px 见方截面，沿「垂直于 facing 的水平轴」全长，type 取左/中/右 1/3 段
                boolean top = "top".equalsIgnoreCase(nameOf(state, halfProp));
                double y0 = top ? 0.5 : 0, y1 = top ? 1 : 0.5;
                String seg = nameOf(state, segProp);
                double a = 0, b = 1.0 / 3, c = 2.0 / 3, d = 1;
                double s0 = "left".equals(seg) ? a : ("middle".equals(seg) ? b : c);
                double s1 = "left".equals(seg) ? b : ("middle".equals(seg) ? c : d);
                return switch (hf) {
                    case NORTH -> VoxelShapes.cuboid(s0, y0, 0, s1, y1, 1);   // 沿 X 方向跑
                    case SOUTH -> VoxelShapes.cuboid(s0, y0, 0, s1, y1, 1);
                    case WEST  -> VoxelShapes.cuboid(0, y0, s0, 1, y1, s1);   // 沿 Z 方向跑
                    case EAST  -> VoxelShapes.cuboid(0, y0, s0, 1, y1, s1);
                    default    -> VoxelShapes.fullCube();
                };
            }
            // 梁柱：8×8 通高柱
            return VoxelShapes.cuboid(0.25, 0, 0.25, 0.75, 1, 0.75);
        }

        // ---------------- 4) 层数型（layers） ----------------
        float r = layersRatio(state);
        if (r >= 0) {
            return VoxelShapes.cuboid(0, 0, 0, 1, Math.max(1f / 16f, r), 1);
        }

        // ---------------- 5) 六向薄片（sheet：FACING 六向） ----------------
        if (state.contains(Properties.FACING) && !state.contains(Properties.HORIZONTAL_FACING)) {
            Direction f6 = state.get(Properties.FACING);
            return switch (f6) {
                case DOWN  -> VoxelShapes.cuboid(0, 0, 0, 1, 0.125, 1);
                case UP    -> VoxelShapes.cuboid(0, 0.875, 0, 1, 1, 1);
                case NORTH -> VoxelShapes.cuboid(0, 0, 0, 1, 1, 0.125);
                case SOUTH -> VoxelShapes.cuboid(0, 0, 0.875, 1, 1, 1);
                case WEST  -> VoxelShapes.cuboid(0, 0, 0, 0.125, 1, 1);
                case EAST  -> VoxelShapes.cuboid(0.875, 0, 0, 1, 1, 1);
            };
        }

        // ---------------- 6) 竖类（facing [+ shape]） ----------------
        Property<?> facingProp = propNamed(state, "facing");
        if (facingProp instanceof net.minecraft.state.property.DirectionProperty dp) {
            Direction f = state.get(dp);
            VoxelShape base = switch (f) {
                case NORTH -> VoxelShapes.cuboid(0, 0, 0, 1, 1, 0.5);
                case SOUTH -> VoxelShapes.cuboid(0, 0, 0.5, 1, 1, 1);
                case WEST  -> VoxelShapes.cuboid(0, 0, 0, 0.5, 1, 1);
                case EAST  -> VoxelShapes.cuboid(0.5, 0, 0, 1, 1, 1);
                default    -> VoxelShapes.fullCube();
            };
            boolean hasShapeProp = shapePropOf(state) != null;
            if (!hasShapeProp) {
                // 竖楼梯：竖板（贴 facing 侧、通高）+ 底部踏面（向外伸出一半、下半高）
                return switch (f) {
                    case NORTH -> VoxelShapes.union(base, VoxelShapes.cuboid(0, 0, 0.5, 1, 0.5, 1));
                    case SOUTH -> VoxelShapes.union(base, VoxelShapes.cuboid(0, 0, 0, 1, 0.5, 0.5));
                    case WEST  -> VoxelShapes.union(base, VoxelShapes.cuboid(0.5, 0, 0, 1, 0.5, 1));
                    case EAST  -> VoxelShapes.union(base, VoxelShapes.cuboid(0, 0, 0, 0.5, 0.5, 1));
                    default -> base;
                };
            }
            // 竖台阶：半格 + inner/outer 补一块成 L 形
            Property<?> shapeProp = propNamed(state, "shape");
            String sh = shapeProp == null ? "straight" : nameOf(state, shapeProp);
            if (sh.contains("inner") || sh.contains("outer")) {
                boolean left = sh.endsWith("left");
                Direction side = left ? f.rotateYCounterclockwise() : f.rotateYClockwise();
                VoxelShape extra = switch (side) {
                    case NORTH -> VoxelShapes.cuboid(0, 0, 0, 1, 1, 0.5);
                    case SOUTH -> VoxelShapes.cuboid(0, 0, 0.5, 1, 1, 1);
                    case WEST  -> VoxelShapes.cuboid(0, 0, 0, 0.5, 1, 1);
                    case EAST  -> VoxelShapes.cuboid(0.5, 0, 0, 1, 1, 1);
                    default    -> base;
                };
                return VoxelShapes.union(base, extra);
            }
            return base;
        }

        // ---------------- 7) 通用 type（bottom / top / double） ----------------
        Property<?> typeProp = propNamed(state, "type");
        if (typeProp != null) {
            String t = nameOf(state, typeProp);
            if ("bottom".equals(t)) return VoxelShapes.cuboid(0, 0, 0, 1, 0.5, 1);
            if ("top".equals(t))    return VoxelShapes.cuboid(0, 0.5, 0, 1, 1, 1);
            return VoxelShapes.fullCube();
        }
        return null;
    }


    // ------------------------------------------------------------------ 各类型的基础几何

    /** 取第一个「整数型」装饰属性（type/variant/vertical/click/water/sofa/vshift/position/corner） */
    private static int decorIndex(BlockState st) {
        for (String n : new String[]{"type", "variant", "vertical", "click", "water",
                "sofa", "vshift", "position", "corner"}) {
            Property<?> p = propNamed(st, n);
            if (p instanceof IntProperty ip) return st.get(ip);
        }
        return 0;
    }

    /** 装饰属性 → 几何可见差异：按索引整体上抬 0.5px/级 */
    private static VoxelShape decorShift(BlockState st, VoxelShape s) {
        int i = decorIndex(st);
        return i <= 0 ? s : s.offset(0, i * 0.5 / 16.0, 0);
    }

    /** 贴在被点击面一侧的薄板（t = 厚度像素） */
    private static VoxelShape plateShape(Direction f, double px) {
        double t = px / 16.0;
        return switch (f) {
            case NORTH -> VoxelShapes.cuboid(0, 0, 0, 1, 1, t);
            case SOUTH -> VoxelShapes.cuboid(0, 0, 1 - t, 1, 1, 1);
            case WEST  -> VoxelShapes.cuboid(0, 0, 0, t, 1, 1);
            case EAST  -> VoxelShapes.cuboid(1 - t, 0, 0, 1, 1, 1);
            default    -> VoxelShapes.fullCube();
        };
    }

    /** 竖杆：4×4 截面，position(0..8) 决定沿横向的偏移 */
    private static VoxelShape poleShape(Direction f, BlockState st) {
        Property<?> p = propNamed(st, "position");
        int pos = p instanceof IntProperty ip ? st.get(ip) : 0;
        double off = 0.5 + Math.min(8, pos) * 1.5 / 16.0;
        double lo = 0.5 - 2.0 / 16, hi = 0.5 + 2.0 / 16;
        double a = Math.max(0, Math.min(1 - (hi - lo), off - (hi - lo) / 2));
        double b = a + (hi - lo);
        return switch (f.getAxis()) {
            case X -> VoxelShapes.cuboid(a, 0, lo, b, 1, hi);
            default -> VoxelShapes.cuboid(lo, 0, a, hi, 1, b);
        };
    }

    /** 薄层：高度 = layers/max；face=true 时在 facing 侧加 2px 立边（让 facing 可见） */
    private static VoxelShape layersShape(BlockState st, boolean face, Direction f) {
        Property<?> p = propNamed(st, "layers");
        double h = 0.25;
        if (p instanceof IntProperty ip) {
            int cur = st.get(ip);
            int max = ip.getValues().stream().mapToInt(v -> v).max().orElse(cur);
            h = Math.max(1.0 / 16, (double) cur / max);
        }
        VoxelShape base = VoxelShapes.cuboid(0, 0, 0, 1, h, 1);
        if (!face) return base;
        double t = 2.0 / 16;
        VoxelShape lip = switch (f) {
            case NORTH -> VoxelShapes.cuboid(0, 0, 0, 1, h, t);
            case SOUTH -> VoxelShapes.cuboid(0, 0, 1 - t, 1, h, 1);
            case WEST  -> VoxelShapes.cuboid(0, 0, 0, t, h, 1);
            default    -> VoxelShapes.cuboid(1 - t, 0, 0, 1, h, 1);
        };
        return VoxelShapes.union(base, lip);
    }

    /** 窗帘：2px 薄板，type(bottom/top/double) 决定占下半/上半/整高 */
    private static VoxelShape curtainShape(Direction f0, BlockState st) {
        Direction f = f0.getOpposite();          // 窗帘贴墙侧（与 facing 相反）
        Property<?> p = propNamed(st, "type");
        String t = p == null ? "bottom" : nameOf(st, p);
        double y0 = "top".equals(t) ? 0.5 : 0;
        double y1 = "bottom".equals(t) ? 0.5 : 1;
        double tk = 2.0 / 16;
        Property<?> op = propNamed(st, "open");
        if (op instanceof BooleanProperty ob && st.get(ob)) {
            y0 = 0.875;                            // 收起：只保留顶部 2px
            y1 = 1;
        }
        return switch (f) {
            case NORTH -> VoxelShapes.cuboid(0, y0, 0, 1, y1, tk);
            case SOUTH -> VoxelShapes.cuboid(0, y0, 1 - tk, 1, y1, 1);
            case WEST  -> VoxelShapes.cuboid(0, y0, 0, tk, y1, 1);
            default    -> VoxelShapes.cuboid(1 - tk, y0, 0, 1, y1, 1);
        };
    }

    /** 座椅：坐垫 + 靠背（靠背在 facing 反侧） */
    private static VoxelShape seatShape(Direction f) {
        VoxelShape pad = VoxelShapes.cuboid(0.125, 0, 0.125, 0.875, 0.5, 0.875);
        VoxelShape back = switch (f) {
            case NORTH -> VoxelShapes.cuboid(0.125, 0.5, 0.75, 0.875, 1, 0.9375);
            case SOUTH -> VoxelShapes.cuboid(0.125, 0.5, 0.0625, 0.875, 1, 0.25);
            case WEST  -> VoxelShapes.cuboid(0.75, 0.5, 0.125, 0.9375, 1, 0.875);
            default    -> VoxelShapes.cuboid(0.0625, 0.5, 0.125, 0.25, 1, 0.875);
        };
        return VoxelShapes.union(pad, back);
    }

    /** 茶几：台面 + 中心柱 */
    private static VoxelShape tableShape(Direction f) {
        return VoxelShapes.union(
                VoxelShapes.cuboid(0, 0.5, 0, 1, 0.625, 1),
                VoxelShapes.cuboid(0.375, 0, 0.375, 0.625, 0.5, 0.625));
    }

    /** 灯具：细柱 + 顶部灯罩 */
    private static VoxelShape lampShape(Direction f) {
        return VoxelShapes.union(
                VoxelShapes.cuboid(0.4375, 0, 0.4375, 0.5625, 0.75, 0.5625),
                VoxelShapes.cuboid(0.25, 0.75, 0.25, 0.75, 1, 0.75));
    }

    /** 窗框：四边细条围成的框 */
    private static VoxelShape frameShape(Direction f) {
        double t = 2.0 / 16, y0 = 0, y1 = 1;
        double lo = 0, hi = 1;
        return switch (f) {
            case NORTH, SOUTH -> {
                double z0 = f == Direction.NORTH ? 0 : 1 - t;
                yield VoxelShapes.union(
                        VoxelShapes.cuboid(0, y0, z0, 1, y0 + t, z0 + t),
                        VoxelShapes.cuboid(0, y1 - t, z0, 1, y1, z0 + t),
                        VoxelShapes.cuboid(0, 0, z0, t, 1, z0 + t),
                        VoxelShapes.cuboid(1 - t, 0, z0, 1, 1, z0 + t));
            }
            default -> {
                double x0 = f == Direction.WEST ? 0 : 1 - t;
                yield VoxelShapes.union(
                        VoxelShapes.cuboid(x0, 0, 0, x0 + t, t, 1),
                        VoxelShapes.cuboid(x0, 1 - t, 0, x0 + t, 1, 1),
                        VoxelShapes.cuboid(x0, 0, 0, x0 + t, 1, t),
                        VoxelShapes.cuboid(x0, 0, 1 - t, x0 + t, 1, 1));
            }
        };
    }

    /** 拱形框：两侧立柱 + 顶部横梁 */
    private static VoxelShape archShape(Direction f) {
        double t = 2.0 / 16;
        return switch (f) {
            case NORTH, SOUTH -> {
                double z0 = f == Direction.NORTH ? 0 : 1 - t;
                yield VoxelShapes.union(
                        VoxelShapes.union(
                                VoxelShapes.cuboid(0, 0, z0, t, 1, z0 + t),
                                VoxelShapes.cuboid(1 - t, 0, z0, 1, 1, z0 + t)),
                        VoxelShapes.cuboid(0, 1 - t, z0, 1, 1, z0 + t));
            }
            default -> {
                double x0 = f == Direction.WEST ? 0 : 1 - t;
                yield VoxelShapes.union(
                        VoxelShapes.union(
                                VoxelShapes.cuboid(x0, 0, 0, x0 + t, 1, t),
                                VoxelShapes.cuboid(x0, 0, 1 - t, x0 + t, 1, 1)),
                        VoxelShapes.cuboid(x0, 1 - t, 0, x0 + t, 1, 1));
            }
        };
    }

    /**
     * coc arch2 / minia arch 的碰撞模板。
     *
     * <p>基准层占半高全格；拱形层占另一半高度的朝向半格。inner 在拱形层
     * 并入转弯侧半格（3/4 平面），outer 则取背向半格与转弯侧半格的交集
     * （1/4 平面）。facing/half 已覆盖模型旋转，waterlogged 不改变几何。
     */
    private static VoxelShape arch2Shape(Direction f, BlockState st) {
        Property<?> hp = propNamed(st, "half");
        boolean top = hp != null && "top".equalsIgnoreCase(nameOf(st, hp));
        double baseY0 = top ? 0.5 : 0.0;
        double baseY1 = top ? 1.0 : 0.5;
        double wallY0 = top ? 0.0 : 0.5;
        double wallY1 = top ? 0.5 : 1.0;

        VoxelShape out = VoxelShapes.cuboid(0, baseY0, 0, 1, baseY1, 1);
        Property<?> sp = propNamed(st, "shape");
        String shape = sp == null ? "straight" : nameOf(st, sp);
        Direction side = f;
        if (shape.endsWith("_left")) side = f.rotateYCounterclockwise();
        else if (shape.endsWith("_right")) side = f.rotateYClockwise();

        if (shape.startsWith("inner")) {
            out = VoxelShapes.union(out, halfLayer(f, wallY0, wallY1));
            out = VoxelShapes.union(out, halfLayer(side, wallY0, wallY1));
        } else if (shape.startsWith("outer")) {
            out = VoxelShapes.union(out, quarterLayer(f.getOpposite(), side, wallY0, wallY1));
        } else {
            out = VoxelShapes.union(out, halfLayer(f, wallY0, wallY1));
        }
        return out;
    }

    /**
     * minia `TypeArch`（`MT_2ClickStairs` 家族的拱）—— C121 按官方字节码重写。
     *
     * <pre>
     * getCollisionShape = HALF(half) + [shape∈{straight,inner_left,inner_right} ? QTR(facing) : ∅]
     *                                + [shape≠straight ? OCT(dir(shape)) : ∅]
     * ★取的是「另一半」的盒★：half=bottom 时 QTR/ OCT 用 TOP 表，half=top 时用 BOT 表。
     * ★OCT 的四个 Direction→角 不是顺时针排布★（UtilAABB 常量原样）：N=NW W=SW E=NE S=SE。
     * </pre>
     */
    private static VoxelShape archmtShape(Direction f, BlockState st) {
        Property<?> hp = propNamed(st, "half");
        boolean top = hp != null && "top".equalsIgnoreCase(nameOf(st, hp));
        double baseY0 = top ? 0.5 : 0.0, baseY1 = top ? 1.0 : 0.5;
        double layY0 = top ? 0.0 : 0.5, layY1 = top ? 0.5 : 1.0;   // ★另一半★

        VoxelShape out = VoxelShapes.cuboid(0, baseY0, 0, 1, baseY1, 1);
        Property<?> sp = propNamed(st, "shape");
        String shape = sp == null ? "straight" : nameOf(st, sp);
        if (shape == null || shape.isEmpty()) shape = "straight";

        if ("straight".equals(shape) || "inner_left".equals(shape) || "inner_right".equals(shape)) {
            out = VoxelShapes.union(out, quarterCol(f, layY0, layY1));
        }
        if (!"straight".equals(shape)) {
            Direction d = switch (shape) {
                case "inner_left"  -> f.rotateYCounterclockwise();
                case "inner_right" -> f.getOpposite();
                case "outer_right" -> f.rotateYClockwise();
                default            -> f;   // outer_left / 未知值
            };
            out = VoxelShapes.union(out, octant(d, layY0, layY1));
        }
        return out;
    }

    /**
     * minia `TypeArchSingle`（← vanilla `StairBlock`）—— 直梯：半层整格 + facing 侧另一层的四分之一。
     * half=bottom 取上层四分之一，half=top 取下层四分之一（与 `archmt` 相反，逐个官方类确认过）。
     */
    private static VoxelShape stairmtShape(Direction f, BlockState st) {
        Property<?> hp = propNamed(st, "half");
        boolean top = hp != null && "top".equalsIgnoreCase(nameOf(st, hp));
        VoxelShape out = top ? VoxelShapes.cuboid(0, 0.5, 0, 1, 1, 1)
                             : VoxelShapes.cuboid(0, 0, 0, 1, 0.5, 1);
        double y0 = top ? 0.0 : 0.5, y1 = top ? 0.5 : 1.0;
        return VoxelShapes.union(out, quarterCol(f, y0, y1));
    }

    /** 贴某方向的四分之一柱（该侧半格 × 给定 y 区间），供 archmt / stairmt 用。 */
    private static VoxelShape quarterCol(Direction d, double y0, double y1) {
        return switch (d) {
            case NORTH -> VoxelShapes.cuboid(0, y0, 0, 1, y1, 0.5);
            case SOUTH -> VoxelShapes.cuboid(0, y0, 0.5, 1, y1, 1);
            case WEST  -> VoxelShapes.cuboid(0, y0, 0, 0.5, y1, 1);
            case EAST  -> VoxelShapes.cuboid(0.5, y0, 0, 1, y1, 1);
            default    -> VoxelShapes.cuboid(0, y0, 0, 1, y1, 1);
        };
    }

    /**
     * 八分之一角（2×2 像素网格中的一格），Direction→角 直接照抄 `UtilAABB` 常量：
     * north=NW(0..8,0..8)｜west=SW(0..8,8..16)｜east=NE(8..16,0..8)｜south=SE(8..16,8..16)。
     */
    private static VoxelShape octant(Direction d, double y0, double y1) {
        return switch (d) {
            case NORTH -> VoxelShapes.cuboid(0, y0, 0, 0.5, y1, 0.5);
            case WEST  -> VoxelShapes.cuboid(0, y0, 0.5, 0.5, y1, 1);
            case EAST  -> VoxelShapes.cuboid(0.5, y0, 0, 1, y1, 0.5);
            case SOUTH -> VoxelShapes.cuboid(0.5, y0, 0.5, 1, y1, 1);
            default    -> VoxelShapes.cuboid(0, y0, 0, 1, y1, 1);
        };
    }

    /** 朝 direction 一侧的半格（y 区间由调用方给），用于 arch2 的拱形层。 */
    private static VoxelShape halfLayer(Direction d, double y0, double y1) {
        return switch (d) {
            case NORTH -> VoxelShapes.cuboid(0, y0, 0, 1, y1, 0.5);
            case SOUTH -> VoxelShapes.cuboid(0, y0, 0.5, 1, y1, 1);
            case WEST  -> VoxelShapes.cuboid(0, y0, 0, 0.5, y1, 1);
            case EAST  -> VoxelShapes.cuboid(0.5, y0, 0, 1, y1, 1);
            default    -> VoxelShapes.cuboid(0, y0, 0, 1, y1, 1);
        };
    }

    /** 两个水平方向的半格交集（1/4 底面），供 arch2 outer 端头使用。 */
    private static VoxelShape quarterLayer(Direction a, Direction b, double y0, double y1) {
        double x0 = 0, x1 = 1, z0 = 0, z1 = 1;
        for (Direction d : new Direction[]{a, b}) {
            switch (d) {
                case WEST  -> x1 = 0.5;
                case EAST  -> x0 = 0.5;
                case NORTH -> z1 = 0.5;
                case SOUTH -> z0 = 0.5;
                default -> { }
            }
        }
        return VoxelShapes.cuboid(x0, y0, z0, x1, y1, z1);
    }

    /** 花盆：中心小方块 */
    private static VoxelShape flowerShape(Direction f) {
        return VoxelShapes.cuboid(0.3125, 0, 0.3125, 0.6875, 0.375, 0.6875);
    }

    /** 沙发：坐垫 + 靠背 + 两侧扶手 */
    private static VoxelShape sofaShape(Direction f) {
        VoxelShape seat = VoxelShapes.cuboid(0, 0, 0, 1, 0.5, 1);
        VoxelShape back = plateShape(f.getOpposite(), 3);
        VoxelShape b2 = VoxelShapes.cuboid(back.getBoundingBox().minX, 0.5, back.getBoundingBox().minZ,
                back.getBoundingBox().maxX, 1, back.getBoundingBox().maxZ);
        return VoxelShapes.union(seat, b2);
    }

    /** 双面件：正反两片薄板 */
    private static VoxelShape doubleShape(Direction f) {
        return VoxelShapes.union(plateShape(f, 2), plateShape(f.getOpposite(), 2));
    }

    /** 带臂家具：薄板 + 两侧翼（按 arm_left / arm_right） */
    private static VoxelShape armShape(Direction f, BlockState st) {
        VoxelShape out = plateShape(f, 3);
        Property<?> l = propNamed(st, "arm_left");
        Property<?> r = propNamed(st, "arm_right");
        boolean bl = l instanceof BooleanProperty blp && st.get(blp);
        boolean br = r instanceof BooleanProperty brp && st.get(brp);
        if (bl) out = VoxelShapes.union(out, plateShape(f.rotateYCounterclockwise(), 3));
        if (br) out = VoxelShapes.union(out, plateShape(f.rotateYClockwise(), 3));
        return out;
    }

    /**
     * 条：贴地横放的长条（长 16、截面 px×px），体积 = 16·px²。
     *
     * <pre>
     * px = 8  → 16×8×8  = 1/4 方块（= 半个竖台阶）
     * px = 4/3/2 → 更细的条
     * straight     贴着 facing 一侧的单条
     * inner_left   单条 + 逆时针端头再加一条 ⇒ L 形
     * inner_right  单条 + 顺时针端头再加一条 ⇒ L 形
     * outer_left   同 inner_*，但基准条贴 facing 的反侧
     * outer_right  同 inner_*，但基准条贴 facing 的反侧
     * </pre>
     */
    private static VoxelShape stripBar(Direction f, int px, String shape) {
        double t = px / 16.0;
        String sh = shape == null ? "straight" : shape;
        boolean outer = sh.startsWith("outer");
        boolean left = sh.endsWith("left");
        Direction base = outer ? f.getOpposite() : f;
        VoxelShape out = barAlong(base, t);
        if (!"straight".equals(sh)) {
            Direction side = left ? base.rotateYClockwise() : base.rotateYCounterclockwise();
            out = VoxelShapes.union(out, barAlong(side, t));
        }
        return out;
    }

    /** 贴地（y 0..t）、贴着方向 d 的一侧、沿另一水平轴全长的条 */
    private static VoxelShape barAlong(Direction d, double t) {
        return switch (d) {
            case NORTH -> VoxelShapes.cuboid(0, 0, 0, 1, t, t);
            case SOUTH -> VoxelShapes.cuboid(0, 0, 1 - t, 1, t, 1);
            case WEST  -> VoxelShapes.cuboid(0, 0, 0, t, t, 1);
            default    -> VoxelShapes.cuboid(1 - t, 0, 0, 1, t, 1);
        };
    }

    /** 按点击位置决定竖台阶的 facing（近半 / 远半） */
    /**
     * 竖楼梯（coc `*_stair_v` / minia `*_vstairs*`）的朝向：**按点击点所在的象限**判定。
     *
     * <p>照抄旧搬运代码 `cocVstairFacing`（cocblock.js:8608）。原来那版用的是
     * `getHorizontalPlayerFacing().getOpposite()`，只能分出「前后」，分不出「左右」
     * ⇒ 同一朝向摆一片时四分之一的方向永远拿不到。
     *
     * <p>规则（面 → 两个半区）：
     * <pre>
     *   点北面：右半(south) / 左半(west)
     *   点南面：右半(east)  / 左半(north)      ← 玩家朝北、点前面那块就是这一档
     *   点东面：上半(north) / 下半(west)
     *   点西面：上半(east)  / 下半(south)
     *   点顶/底面：四象限 → 东/南/西/北
     * </pre>
     * Aki 的例子「朝北时，朝方向的左前方放是 north、右前方是 east」正是「点南面」这一档。
     */
    private static Direction vstairFacing(ItemPlacementContext ctx) {
        // ★原点只能用【放置格】★：yarn 这版 ItemPlacementContext 没有 getClickedPos()，
        //   放置格 = 被点击格 + 法线方向。点击坐标相对放置格会落在 [-1,0] 或 [1,2]，
        //   所以照旧搬运代码的做法，沿点击面把原点挪回被点击格，再钳到 [0,1]。
        BlockPos cp = ctx.getBlockPos();
        net.minecraft.util.math.Vec3d h = ctx.getHitPos();
        double dx = h.x - cp.getX();
        double dz = h.z - cp.getZ();
        Direction face = ctx.getSide();
        switch (face) {
            case EAST:  dx += 1; break;
            case WEST:  dx -= 1; break;
            case SOUTH: dz += 1; break;
            case NORTH: dz -= 1; break;
            default: break;              // 顶/底：水平两轴本来就在 0..1
        }
        dx = clamp01(dx);
        dz = clamp01(dz);
        switch (face) {
            case NORTH:
                if (dz <= 0.5) return dx >= 0.5 ? Direction.SOUTH : Direction.WEST;
                break;
            case SOUTH:
                if (dz >= 0.5) return dx >= 0.5 ? Direction.EAST : Direction.NORTH;
                break;
            case EAST:
                if (dx >= 0.5) return dz <= 0.5 ? Direction.NORTH : Direction.WEST;
                break;
            case WEST:
                if (dx <= 0.5) return dz >= 0.5 ? Direction.SOUTH : Direction.EAST;
                break;
            default:   // 顶 / 底：四象限
                if (dx >= 0.5) return dz <= 0.5 ? Direction.EAST : Direction.SOUTH;
                else return dz >= 0.5 ? Direction.WEST : Direction.NORTH;
        }
        // 兜底：旧代码里 pos 的初值是 "ne"（= east），分支没命中时就是这个
        return Direction.EAST;
    }

    private static double clamp01(double v) {
        if (!(v >= 0)) return 0;
        return v > 1 ? 1 : v;
    }

    private static Direction facingFromHit(BlockState state, ItemPlacementContext ctx) {
        // ★ 只沿玩家朝向的那一根轴取近/远 ★
        //   面朝 north/south ⇒ 只可能 north 或 south；面朝 east/west ⇒ 只可能 east 或 west
        Direction look = ctx.getHorizontalPlayerFacing();
        double cx = ctx.getBlockPos().getX() + 0.5;
        double cz = ctx.getBlockPos().getZ() + 0.5;
        if (look.getAxis() == Direction.Axis.Z) {
            return ctx.getHitPos().z >= cz ? Direction.SOUTH : Direction.NORTH;
        }
        return ctx.getHitPos().x >= cx ? Direction.EAST : Direction.WEST;
    }

    private static Property<?> shapePropOf(BlockState state) {
        return propNamed(state, "shape");
    }

    /**
     * 「原地合并/叠层」只对**同一个方块自己的物品**开放。
     *
     * <p>★2026-10-27 用户实机：`desk_simple_black` / `cabinet_stainless` 右键直接被替换掉★
     * 根因是旧代码里有一条为半砖写的 `type` 分支：
     * <pre>
     *   if (typeProp != null &amp;&amp; at.isOf(this) &amp;&amp; !"double".equals(nameOf(at, typeProp))) return true;
     * </pre>
     * ① 半砖走的是 {@link ScriptSlabBlock}（extends SlabBlock），**根本不到这里**；
     * ② 这条实际只命中了 `type = 0..3` 的家具（沙发/桌/柜/凳/水槽，共 170 个 id），
     *    `nameOf()` 永远不等于 "double" ⇒ 恒 true；
     * ③ 而且没有"手里拿的是不是这个方块"的判断 ⇒ **拿任何方块右键都会把家具整块替换掉**。
     * `layers` 那条也有同样的漏洞（拿泥土右键 `*_pile` 会顶掉一层）。
     */
    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext ctx) {
        // ★★先看手持物：不是本方块的物品，一律**不允许**原地替换★★
        //
        // 这里以前写的是 `return super.canReplace(state, ctx)` —— 而 vanilla
        // `Block#canReplace` 的默认实现**恒返回 true**，等于"拿任何方块右键都能盖掉"。
        // Aki 2026-10-27：「会被其他方块右键替换，右键替换是个大问题，复查所有方块」。
        // 本 mod 的方块全是家具/装饰，没有"原地被替换"的语义（草皮/雪那种才需要），
        // 所以这里直接 false。同种方块的叠加（layers / 半砖 double）在下面单独放行。
        if (ctx.getStack().isEmpty() || !ctx.getStack().isOf(this.asItem())) {
            return false;
        }

        Property<?> p = propNamed(state, "layers");
        if (p instanceof IntProperty ip) {
            if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) return false;
            int max = ip.getValues().stream().mapToInt(v -> v).max().orElse(0);
            if (state.get(ip) < max) return true;     // 同种方块叠一层
        }

        // 半砖（真 SlabType）才允许叠成 double；type=0..3 的家具不在此列
        if (propNamed(state, "type") == Properties.SLAB_TYPE
                && state.get(Properties.SLAB_TYPE) != net.minecraft.block.enums.SlabType.DOUBLE) {
            return true;
        }

        return super.canReplace(state, ctx);
    }

    // ------------------------------------------------------------------ 工具

    /** 给脚本/调试用：本方块是否支持含水 */
    public boolean isWaterloggable() {
        return waterloggable;
    }

    /** 常用属性快捷引用（脚本里可通过 Java.loadClass 拿到） */
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    public static final DirectionProperty HORIZONTAL_FACING = Properties.HORIZONTAL_FACING;
}
