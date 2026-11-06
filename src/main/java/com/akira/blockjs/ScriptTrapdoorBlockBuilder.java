package com.akira.blockjs;

import dev.latvian.mods.kubejs.block.BlockBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** 活板门类型构造器。 */
public class ScriptTrapdoorBlockBuilder extends BlockBuilder {

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

    public ScriptTrapdoorBlockBuilder(Identifier id) {
        super(id);
    }

    public ScriptTrapdoorBlockBuilder blockSetType(BlockSetType type) {
        if (type != null) this.blockSetType = type;
        return this;
    }

    public ScriptTrapdoorBlockBuilder extraProp(Property<?> prop) {
        if (prop != null && !extras.contains(prop)) {
            property(prop);
            extras.add(prop);
        }
        return this;
    }

    @Override
    public Block createObject() {
        // 同门：活板门以前也没走 BlockMeta（`window_trapdoor_*` 同样按 solid 层画）
        BlockMeta.apply(this, id == null ? null : id.toString());
        // ★金属活板门（meta red=1）⇒ BlockSetType.IRON★：canOpenByHand=false（要红石）、
        // 开合音效也是铁质的；木/石走 OAK（可手动）。原来一律 OAK，所以钢活板门能空手开。
        if (BlockMeta.needsRedstone(id == null ? null : id.toString())) {
            this.blockSetType = BlockSetType.IRON;
        }
        ScriptTrapdoorBlock.prepare(extras);
        try {
            return new ScriptTrapdoorBlock(createProperties(), blockSetType);
        } finally {
            ScriptTrapdoorBlock.clear();
        }
    }
}
