package com.akira.blockjs;

import dev.latvian.mods.kubejs.block.BlockBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** 门类型构造器：默认木门，可换 BlockSetType。 */
public class ScriptDoorBlockBuilder extends BlockBuilder {

    private final List<Property<?>> extras = new ArrayList<>();
    private BlockSetType blockSetType = BlockSetType.OAK;


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

    public ScriptDoorBlockBuilder(Identifier id) {
        super(id);
    }

    public ScriptDoorBlockBuilder blockSetType(BlockSetType type) {
        if (type != null) this.blockSetType = type;
        return this;
    }

    public ScriptDoorBlockBuilder extraProp(Property<?> prop) {
        if (prop != null && !extras.contains(prop)) {
            property(prop);
            extras.add(prop);
        }
        return this;
    }

    @Override
    public Block createObject() {
        // ★门以前没走 BlockMeta★：渲染层/音效/遮挡全丢 ⇒ 玻璃门（shoji_glass_*）按 solid 层画，
        // 半透明部分变成不透明块。这里补上，和 ScriptBlockBuilder 同一套。
        BlockMeta.apply(this, id == null ? null : id.toString());
        ScriptDoorBlock.prepare(extras);
        ScriptDoorBlock.prepareBox(id == null ? null : id.toString());
        try {
            return new ScriptDoorBlock(createProperties(), blockSetType);
        } finally {
            ScriptDoorBlock.clear();
        }
    }
}
