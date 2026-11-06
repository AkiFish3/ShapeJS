package com.akira.blockjs;

import dev.latvian.mods.kubejs.block.custom.StairBlockBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code cocstair} 类型的构造器：在 KubeJS 楼梯构造器之上允许<b>追加自定义属性</b>。
 *
 * <p>KubeJS 自带 {@code stairs} 类型已经能做 facing/half/shape/waterlogged，
 * 但属性表写死在 {@code StairBlock} 里，无法加 {@code mtshape} 这类自有属性。
 * 本类把额外的 {@link Property} 一并交给 {@link ScriptStairBlock}，由它在
 * {@code appendProperties} 里并进 StateManager。
 */
public class ScriptStairBlockBuilder extends StairBlockBuilder {

    private final List<Property<?>> extras = new ArrayList<>();
    private final Map<Property<?>, Comparable<?>> defaults = new HashMap<>();


    /**
     * ★禁止 KubeJS 生成资源★
     *
     * BlockBuilder/SlabBlockBuilder/StairBlockBuilder/WallBlockBuilder/FenceBlockBuilder 都会
     * 在 {@code generateAssetJsons} 里用**碰撞盒**拼一套 blockstate + 模型并打进虚拟资源包，
     * 优先级高于 {@code kubejs/assets} 里的手写资源 ⇒ 方块模型被换成 cube_all。
     * 2026-10-28 实证：{@code cocricot:awning_green_dslab}（parent=slab）在游戏里渲染成整块。
     *
     * 我们的 blockstate / 模型 / item 模型 / lang 全部手写且已核对齐全，这里直接关掉生成器。
     */
    @Override
    public void generateAssetJsons(dev.latvian.mods.kubejs.generator.AssetJsonGenerator generator) {
        // no-op：用磁盘资源
    }

    public ScriptStairBlockBuilder(Identifier id) {
        super(id);
    }

    /** 同 ScriptBlockBuilder#sound：短名 → BlockSoundGroup，同时改采掘/放置/脚步声 */
    public ScriptStairBlockBuilder sound(String name) {
        net.minecraft.sound.BlockSoundGroup g = SoundPresets.of(name);
        if (g != null) soundType(g);
        return this;
    }

    /** 与 ScriptBlockBuilder 保持同一套 API 名（脚本侧不用记两个名字） */
    public ScriptStairBlockBuilder extraProp(Property<?> prop) {
        return extra(prop);
    }

    /** 楼梯本身就含水，这里只为 API 对齐留一个空实现 */
    public ScriptStairBlockBuilder waterloggable() {
        return this;
    }

    /** 追加一个属性（同时登记进 KubeJS，保证状态表与生成器一致） */
    public ScriptStairBlockBuilder extra(Property<?> prop) {
        if (prop != null && !extras.contains(prop)) {
            property(prop);
            extras.add(prop);
        }
        return this;
    }

    /** 指定追加属性的默认值（参数用 Object，理由同 ScriptBlockBuilder） */
    @SuppressWarnings("rawtypes")
    public ScriptStairBlockBuilder extraDefault(Property prop, Object value) {
        if (prop != null) {
            Comparable c = ScriptBlockBuilder.coerce(prop, value);
            if (c != null) defaults.put(prop, c);
        }
        return this;
    }

    @Override
    public Block createObject() {
        BlockMeta.apply(this, id == null ? null : id.toString());
        // Block 构造期会回调 appendProperties，属性表必须提前挂在 ThreadLocal 上
        ScriptStairBlock.prepare(extras, defaults);
        try {
            return new ScriptStairBlock(Blocks.STONE.getDefaultState(), createProperties(), extras, defaults);
        } finally {
            ScriptStairBlock.clear();
        }
    }
}
