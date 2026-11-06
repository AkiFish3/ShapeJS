package com.akira.blockjs;

import dev.latvian.mods.kubejs.block.BlockBuilder;
import net.minecraft.block.Block;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 台阶（原版 SlabBlock） 类型构造器。 */
public class ScriptSlabBlockBuilder extends BlockBuilder {

    private final List<Property<?>> extras = new ArrayList<>();
    private final Map<Property<?>, Comparable<?>> defaults = new HashMap<>();
    private boolean sameCull = false;


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

    public ScriptSlabBlockBuilder(Identifier id) {
        super(id);
    }

    public ScriptSlabBlockBuilder extraProp(Property<?> prop) {
        if (prop != null && !extras.contains(prop)) {
            property(prop);
            extras.add(prop);
        }
        return this;
    }

    public ScriptSlabBlockBuilder extraDefault(Property prop, Object value) {
        if (prop != null && value instanceof Comparable c) defaults.put(prop, c);
        return this;
    }

    public ScriptSlabBlockBuilder sound(String name) {
        net.minecraft.sound.BlockSoundGroup g = SoundPresets.of(name);
        if (g != null) soundType(g);
        return this;
    }

    public ScriptSlabBlockBuilder sameCull() {
        this.sameCull = true;
        return this;
    }

    @Override
    public Block createObject() {
        BlockMeta.apply(this, id == null ? null : id.toString());
        BlockMeta.Meta _m = BlockMeta.of(id == null ? null : id.toString());
        if (_m != null && _m.cull()) sameCull = true;
        ScriptSlabBlock.prepare(extras);
        ScriptSlabBlock.prepareDefaults(defaults);
        ScriptSlabBlock.prepareCull(sameCull);
        // 盒表 id：shift 面板等需要按模型取真实形状（无条目时 BoxTable 返回 null，回落原版半砖形状）
        ScriptSlabBlock.prepareBox(id == null ? null : id.toString());
        try {
            return new ScriptSlabBlock(createProperties());
        } finally {
            ScriptSlabBlock.clear();
        }
    }
}
