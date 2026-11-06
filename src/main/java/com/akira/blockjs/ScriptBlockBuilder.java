package com.akira.blockjs;

import dev.latvian.mods.kubejs.block.BlockBuilder;
import net.minecraft.block.Block;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用脚本方块构造器：所有"形状族"（vslab / half / bar / pane / layered / connect / sheet）
 * 共用这一个 builder，用 {@link #waterloggable()}、{@link #extraProp(Property)} 声明差异。
 *
 * <p>形状本身不进状态表算，交给 {@link DynamicShapes}（按 blockstate 键查表），
 * 所以新增一种形状**只需要新属性组合 + 模型**，不需要新的 Java 类。
 */
public class ScriptBlockBuilder extends BlockBuilder {

    private final List<Property<?>> extras = new ArrayList<>();
    private final Map<Property<?>, Comparable<?>> defaults = new HashMap<>();
    private boolean waterloggable = false;
    /** 几何提示：slope / cylinder / step3 / step6（空 = 按属性推断） */
    private String kind = "";

    public ScriptBlockBuilder kind(String k) {
        this.kind = k == null ? "" : k;
        return this;
    }

    /** 截面像素尺寸（0 = 不用）；粗条 4、细杆 2 */
    private int crossSize = 0;

    public ScriptBlockBuilder crossSize(int px) {
        this.crossSize = px;
        return this;
    }

    public ScriptBlockBuilder(Identifier id) {
        super(id);
    }

    /** 材质连接：同种方块相邻时剔除公共面（玻璃/透明块） */
    private boolean sameCull = false;

    /** NSEW 只认同种（不粘实心墙）。★默认关闭★：Aki 确认铁栏/围栏应像原版栅栏一样粘实心方块，
     *  所以 5 个 nsew_* 预设都不启用它；留着这个开关是给将来"纯自家连自家"的族用。 */
    private boolean nsewSameOnly = false;

    private boolean flipFacing = false;

    /** 放置算法：缺省 / "vstair"（按点击象限定朝向，照抄旧搬运代码） */
    private String placeMode = null;
    /** 放置时按点击高度自动写 click（minia 恒 click=0 的族要关掉） */
    private boolean autoClick = true;
    private String shapeRule = null;
    private boolean blink = false;
    /** 垂直堆叠：up/down 两个 boolean 按相邻同类方块实时算（抽屉族） */
    private boolean vstack = false;
    /** 窗帘/挂帘：vertical=single|upper|middle|lower 按上下邻块实时算 */
    private boolean vertical = false;

    public ScriptBlockBuilder placeMode(String m) { this.placeMode = m; return this; }

    /**
     * 放置时是否按点击高度自动写 {@code click}（默认 true）。
     *
     * <p>★关掉它的理由★：minia `MT_3ClickFacing` / `MT_2ClickFacingHUD` 的
     * {@code getStateForPlacement} 恒 {@code iconst_0}，点在上半格也还是 0。
     * 我们的自动分段会让这些方块放下就带 click=1/2，位移通道一开就整体漂 0.5~1 格。
     */
    public ScriptBlockBuilder autoClick(boolean v) { this.autoClick = v; return this; }

    /** shape 计算规则：null/"stairs"=原版楼梯（前→inner）；"mt"=miniaturia（前→outer） */
    public ScriptBlockBuilder shapeRule(String r) { this.shapeRule = r; return this; }

    /** 灯会闪：每 10 tick 翻转 lighting（配合 lightsFrom 使用） */
    public ScriptBlockBuilder blink() { this.blink = true; return this; }

    public ScriptBlockBuilder vstack() { this.vstack = true; return this; }

    /**
     * 自动计算竖直分段状态 {@code vertical}。
     *
     * <p>只有同种方块、同 facing（以及双方都有的 click/shape 相同）才视为同一列：
     * 上下都接 = middle；仅下方接 = upper；仅上方接 = lower；否则 single。
     */
    public ScriptBlockBuilder vertical() { this.vertical = true; return this; }

    public ScriptBlockBuilder flipFacing() { this.flipFacing = true; return this; }

    public ScriptBlockBuilder sameCull() {
        this.sameCull = true;
        return this;
    }

    /** NSEW 只认同种方块（不粘墙） */
    public ScriptBlockBuilder nsewSameOnly() {
        this.nsewSameOnly = true;
        return this;
    }

    /** 音效预设短名（wood/stone/deepslate/metal/glass/wool/grass/gravel/sand/snow/lodestone/copper…）。
     *  SoundType 同时决定脚步声，所以这一句就把采掘+放置+踩踏一起改了。 */
    public ScriptBlockBuilder sound(String name) {
        net.minecraft.sound.BlockSoundGroup g = SoundPresets.of(name);
        if (g != null) soundType(g);
        return this;
    }

    /** 亮度来源属性 + 开/关两级亮度（0..15）；null = 不发光 */
    private BooleanProperty lightProp = null;
    private int lightOn = 15;
    private int lightOff = 0;

    /**
     * 让亮度跟着某个布尔属性走（coc 官方灯：{@code lighting ? 15 : 0}）。
     *
     * <p>★为什么不用 KubeJS 的 {@code lightLevel}★：那个是【常量比例】，
     * javap 实证 {@code lombok/lambda$createProperties$X = (int)(lightLevel * 15.0f)}，
     * 所有状态一个值 —— 而且传 15 会算成 225 级，光照更新直接失效（"灯不会亮"）。
     * 这里走 {@code Settings.luminance(ToIntFunction<BlockState>)}，逐状态求值，
     * 与官方 {@code LIGHT_LEVEL = state -> lighting ? 15 : 0} 同构。
     */
    public ScriptBlockBuilder lightsFrom(BooleanProperty prop, int on, int off) {
        this.lightProp = prop;
        this.lightOn = on;
        this.lightOff = off;
        return this;
    }

    /** 声明本方块可含水（会自动加入 WATERLOGGED 属性并实现真流体逻辑） */
    public ScriptBlockBuilder waterloggable() {
        return waterloggable(true);
    }

    /**
     * 含水开关。类型预设会默认打开，脚本可以关掉：
     * <pre>
     * e.create('coc:xxx', 'vslab').waterloggable(false)   // 要竖台阶但不要含水
     * </pre>
     * 注意：关掉只影响「新注册时的属性集」，已经加入的 WATERLOGGED 不会被移除
     * （属性集在 StateManager 构建时就固定了）。
     */
    public ScriptBlockBuilder waterloggable(boolean enabled) {
        if (enabled && !waterloggable) {
            waterloggable = true;
            extraProp(Properties.WATERLOGGED);
            extraDefault(Properties.WATERLOGGED, Boolean.FALSE);
        } else if (!enabled) {
            waterloggable = false;
            extras.remove(Properties.WATERLOGGED);
            defaults.remove(Properties.WATERLOGGED);
        }
        return this;
    }

    /**
     * 追加 coc 的 {@code on} 属性（"放在台阶上"开关）：
     * 默认 false；放置时若下方是楼梯则自动 true，几何整体下沉半格。
     */
    public ScriptBlockBuilder onProperty() {
        net.minecraft.state.property.BooleanProperty on =
                net.minecraft.state.property.BooleanProperty.of("on");
        extraProp(on);
        extraDefault(on, Boolean.FALSE);
        return this;
    }

    /**
     * 墙端连接位（{@code sides} 0..3）—— 2026-10-28 03:30 恢复，**只服务顶面封口**。
     *
     * <p>为什么顶面必须靠状态、不能靠 CTM：
     * <ul>
     *   <li>CTM 的连接判定轴写死在世界轴上（horizontal 只看 E/W，vertical 只看 U/D），
     *       而 vslab 的连接方向随 facing 在「世界 ±x」与「世界 ±z」之间切换 ⇒ 一套 tile 只能对一半朝向。</li>
     *   <li>顶面 quad 的 uv 会随 blockstate 的 {@code y} 旋转，CTM 的 tile 却永远「北向上」
     *       ⇒ 判定对了图案也会转 90°。</li>
     *   <li>顶面只有半格（16×8 单位），47 状态里大半组合在半格上不可能出现。</li>
     * </ul>
     *
     * <p>侧面（05 的竖直分段）与拱形面继续走纯 CTM，这里不注入 {@code caps}。
     */
    public ScriptBlockBuilder sides() {
        extraProp(ScriptBlock.SIDES);
        return this;
    }

    /** 追加一个自定义属性（如 mtshape / layers / NSEW） */
    public ScriptBlockBuilder extraProp(Property<?> prop) {
        if (prop != null && !extras.contains(prop)) {
            property(prop);
            extras.add(prop);
        }
        return this;
    }

    /**
     * 指定追加属性的默认值。
     * 参数用 Object 而不是 Comparable：Rhino 对 (Property, Comparable) 的绑定会失败
     * （报 "Can't find method ...(class_2758,number)"），放宽成 Object 后由 Java 侧转型。
     */
    @SuppressWarnings("rawtypes")
    public ScriptBlockBuilder extraDefault(Property prop, Object value) {
        if (prop != null) {
            Comparable c = coerce(prop, value);
            if (c != null) defaults.put(prop, c);
        }
        return this;
    }

    /**
     * ★Rhino 数值坑★ JS 里的 `1` 到这里是 Double，而 IntProperty 只接受 Integer；
     * ferritecore 的 IntIndexer 会直接 ClassCastException。这里统一收口。
     */
    @SuppressWarnings("rawtypes")
    static Comparable coerce(Property prop, Object value) {
        if (value == null) return null;
        if (prop instanceof net.minecraft.state.property.IntProperty && value instanceof Number n) {
            return n.intValue();
        }
        if (prop instanceof net.minecraft.state.property.BooleanProperty && value instanceof Boolean b) {
            return b;
        }
        return value instanceof Comparable c ? c : null;
    }

    /**
     * ★覆写为「不生成」★：KubeJS 默认会用碰撞盒生成 cube_all 模型并覆盖磁盘资源
     * （{@code BlockBuilder#generateBlockModelJsons} 的字节码里完全没有检查 model 字段），
     * 导致六面贴图不同的方块（窗、顶底异色的砖）顶面被 `all` 覆盖。
     * 我们的方块资源全部手写在 {@code kubejs/assets}，所以这里直接关掉生成器。
     */
    @Override
    protected void generateBlockModelJsons(dev.latvian.mods.kubejs.generator.AssetJsonGenerator generator) {
        // no-op：用磁盘模型
    }

    /** 同上：blockstate 也全部手写，禁止 KubeJS 生成单变体覆盖（多变体方块会直接紫黑）。 */
    @Override
    protected void generateBlockStateJson(dev.latvian.mods.kubejs.client.VariantBlockStateGenerator generator) {
        // no-op：用磁盘 blockstate
    }

    /** 同上：入口级一把关掉（子生成器已分别 no-op，这里保证不会漏任何一项）。 */
    @Override
    public void generateAssetJsons(dev.latvian.mods.kubejs.generator.AssetJsonGenerator generator) {
        // no-op：用磁盘资源
    }

    @Override
    public Block createObject() {
        // ★按 id 查表应用音效/渲染层/剔面★（脚本层调 builder 子类方法实测无效，见 BlockMeta 注释）
        BlockMeta.apply(this, id == null ? null : id.toString());
        // ★KubeJS 会用碰撞盒生成 cube_all 模型并覆盖磁盘模型★（`generateBlockModelJsons`），
        //   对 window_arch 这类「六面贴图不同」的方块，顶面就被 `all` 覆盖成侧面图。
        //   显式设置 model 后 KubeJS 不再自动生成，改用磁盘上的真实模型。
        if (this.id != null) {
            String _pid = this.id.toString();
            if (_pid.startsWith("cocricot:window_arch_")
                    && !_pid.contains("_top") && !_pid.contains("_pane")) {
                if (this.model == null) {
                    this.model = "cocricot:block/" + _pid.substring("cocricot:".length());
                    if (_pid.endsWith("window_arch_cyan") || _pid.endsWith("window_arch_cyan_vslab")) {
                        DynamicShapes.LOG.info("[probe] " + _pid + " model=" + this.model
                                + " blockstateJson=" + this.blockstateJson + " modelJson=" + this.modelJson);
                    }
                }
            }
        }
        BlockMeta.Meta _m = BlockMeta.of(id == null ? null : id.toString());
        if (_m != null) {
            if (_m.cull()) sameCull = true;
            if (_m.flip()) flipFacing = true;
            // ★NSEW 只认同款★（meta 的 ns:1）：地毯/餐桌这类家具不该跟邻居"长"在一起
            if (_m.nsame()) nsewSameOnly = true;
        }
        ScriptBlock.prepare(extras);
        ScriptBlock.prepareCull(sameCull);
        ScriptBlock.prepareNsewSameOnly(nsewSameOnly);
        ScriptBlock.prepareBox(id == null ? null : id.toString());
        ScriptBlock.prepareFlip(flipFacing);
        ScriptBlock.preparePlaceMode(placeMode);
        ScriptBlock.prepareAutoClick(autoClick);
        ScriptBlock.prepareShapeRule(shapeRule);
        ScriptBlock.prepareBlink(blink);
        ScriptBlock.prepareVStack(vstack);
        ScriptBlock.prepareVertical(vertical);
        // ★连接族★：同族不同变体的 NSEW 互连（garland_black ↔ garland_pop），值来自 meta 的 fam
        ScriptBlock.prepareFamily(BlockMeta.family(id == null ? null : id.toString()));
        // ★完全不做面剔除★（半透明/镂空摆件，meta 的 nc:1）
        ScriptBlock.prepareNoCull(_m != null && _m.nocull());
        // ★没有碰撞体积★（火/垂花/冰淇淋，meta 的 nocol:1）
        ScriptBlock.prepareNoCollision(_m != null && _m.nocol());
        // ★碰撞整格、轮廓仍薄片★（斜向玻璃板，meta 的 fc:1，Aki A4）
        ScriptBlock.prepareFullCollision(_m != null && _m.fullCollision());
        try {
            net.minecraft.block.AbstractBlock.Settings settings = createProperties();
            if (lightProp != null) {
                final BooleanProperty lp = lightProp;
                final int on = lightOn, off = lightOff;
                settings = settings.luminance(state ->
                        Boolean.TRUE.equals(state.contains(lp) ? state.get(lp) : null) ? on : off);
            }
            return new ScriptBlock(settings, extras, defaults, waterloggable, crossSize, kind);
        } finally {
            ScriptBlock.clear();
        }
    }

    // 便于脚本直接拿到属性对象
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
}
