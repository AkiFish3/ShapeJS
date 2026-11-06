package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;

import java.util.List;
import java.util.Map;

/**
 * 脚本可注册的「楼梯系」方块类型（类型 id：{@code cocstair}）。
 *
 * <p>为什么需要它：原版 {@link StairsBlock} 已经自带
 * {@code facing / half / shape / waterlogged} 四件套，且 <b>放置时就把 shape 算好</b>
 * （{@code getPlacementState} → {@code getStairShape}），所以不会出现「放下先直、下一帧才变拐角」
 * 的闪烁；水也是真含水（实现 {@code SimpleWaterloggedBlock}）。
 *
 * <p>与直接 {@code e.create(id, "stairs")} 的差别：本类型允许<b>追加自定义属性</b>
 * （例如 coc 天窗的 {@code mtshape}），因为属性表在构造时就并进 StateManager。
 *
 * <p>脚本侧用法（类型注册后）：
 * <pre>
 * e.create('coc:glass_black_skylight', 'cocstair')
 *   .extra('mtshape', 0, 1)   // 追加 IntProperty（可选，可多次调用）
 * </pre>
 */
public class ScriptStairBlock extends StairsBlock {

    /**
     * ★初始化顺序坑★ Block 的构造函数会回调 {@code appendProperties}，
     * 那时子类字段还没赋值（都是 null）。所以属性表必须放在 ThreadLocal 里，
     * 由 builder 在 new 之前 set、new 之后 clear。
     */
    private static final ThreadLocal<List<Property<?>>> PENDING_PROPS = new ThreadLocal<>();
    private static final ThreadLocal<Map<Property<?>, Comparable<?>>> PENDING_DEFAULTS = new ThreadLocal<>();

    public static void prepare(List<Property<?>> props, Map<Property<?>, Comparable<?>> defaults) {
        PENDING_PROPS.set(props == null ? List.of() : props);
        PENDING_DEFAULTS.set(defaults == null ? Map.of() : defaults);
    }

    public static void clear() {
        PENDING_PROPS.remove();
        PENDING_DEFAULTS.remove();
    }

    /** 追加属性（在 facing/half/shape/waterlogged 之后） */
    private final List<Property<?>> extraProps;
    /** 追加属性的默认值（未指定则取该属性自身 defaultValue） */
    private final Map<Property<?>, Comparable<?>> extraDefaults;

    public ScriptStairBlock(BlockState baseState,
                         Settings settings,
                         List<Property<?>> extraProps,
                         Map<Property<?>, Comparable<?>> extraDefaults) {
        super(baseState, settings);
        this.extraProps = extraProps == null ? List.of() : List.copyOf(extraProps);
        this.extraDefaults = extraDefaults == null ? Map.of() : Map.copyOf(extraDefaults);
        if (!this.extraProps.isEmpty()) {
            BlockState state = getDefaultState();
            for (Property<?> p : this.extraProps) {
                Comparable<?> v = this.extraDefaults.get(p);
                if (v == null) v = p.getValues().iterator().next();
                state = withUnchecked(state, p, v);
            }
            setDefaultState(state);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withUnchecked(BlockState state, Property p, Comparable v) {
        return state.with(p, v);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        List<Property<?>> pending = PENDING_PROPS.get();
        if (pending != null) {
            for (Property<?> p : pending) builder.add(p);
        }
    }
}
