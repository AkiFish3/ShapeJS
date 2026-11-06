package com.akira.blockjs;

import net.minecraft.sound.BlockSoundGroup;

/**
 * 音效预设：把脚本里的字符串映射到 {@link BlockSoundGroup}。
 *
 * <p>为什么需要这一层：KubeJS 的 {@code soundType(...)} 只接受 {@code BlockSoundGroup} 对象，
 * 脚本里直接写 {@code Java.loadClass('net.minecraft.sound.BlockSoundGroup').METAL} 一旦映射名变动就静默失败。
 * 这里在 Java 侧做白名单，脚本只传短名，拼错也只是回落到默认值，不会崩。
 *
 * <p>★注意：{@code BlockSoundGroup} 同时决定 采掘 / 放置 / <b>踩踏（脚步声）</b> / 落地 四类音效★
 * —— 所以换 SoundType 就等于换 footsteps，不存在"只改挖掘声不改脚步声"的写法。
 */
public final class SoundPresets {

    private SoundPresets() {}

    /** 返回 null 表示不认识，调用方应保持默认（KubeJS 默认 STONE） */
    public static BlockSoundGroup of(String name) {
        if (name == null) return null;
        return switch (name) {
            case "wood"       -> BlockSoundGroup.WOOD;
            case "stone"      -> BlockSoundGroup.STONE;
            case "deepslate"  -> BlockSoundGroup.DEEPSLATE;       // Aki 指定：brick 用深板岩
            case "deepslate_bricks" -> BlockSoundGroup.DEEPSLATE_BRICKS;
            case "deepslate_tiles"  -> BlockSoundGroup.DEEPSLATE_TILES;
            case "polished_deepslate" -> BlockSoundGroup.POLISHED_DEEPSLATE;
            case "metal"      -> BlockSoundGroup.METAL;           // 铁活板门 / 钢
            case "copper"     -> BlockSoundGroup.COPPER;
            case "netherite"  -> BlockSoundGroup.NETHERITE;
            case "glass"      -> BlockSoundGroup.GLASS;
            case "wool"       -> BlockSoundGroup.WOOL;
            case "grass"      -> BlockSoundGroup.GRASS;
            case "gravel"     -> BlockSoundGroup.GRAVEL;
            case "sand"       -> BlockSoundGroup.SAND;
            case "snow"       -> BlockSoundGroup.SNOW;
            case "lodestone"  -> BlockSoundGroup.LODESTONE;       // 磁石
            case "amethyst"   -> BlockSoundGroup.AMETHYST_BLOCK;
            case "tuff"       -> BlockSoundGroup.TUFF;
            case "calcite"    -> BlockSoundGroup.CALCITE;
            case "basalt"     -> BlockSoundGroup.BASALT;
            case "mud_bricks" -> BlockSoundGroup.MUD_BRICKS;
            case "bamboo"     -> BlockSoundGroup.BAMBOO;
            case "cherry"     -> BlockSoundGroup.CHERRY_WOOD;
            case "bamboo_wood" -> BlockSoundGroup.BAMBOO_WOOD;
            case "nether_wood" -> BlockSoundGroup.NETHER_WOOD;
            case "crop"       -> BlockSoundGroup.CROP;
            case "vine"       -> BlockSoundGroup.VINE;
            case "ladder"     -> BlockSoundGroup.LADDER;
            case "lantern"    -> BlockSoundGroup.LANTERN;
            case "chain"      -> BlockSoundGroup.CHAIN;
            case "anvil"      -> BlockSoundGroup.ANVIL;
            case "candle"     -> BlockSoundGroup.CANDLE;
            case "bone"       -> BlockSoundGroup.BONE;
            case "mud"        -> BlockSoundGroup.MUD;
            case "sculk"      -> BlockSoundGroup.SCULK;
            default -> null;
        };
    }

    /**
     * 挖掘硬度（C10，Aki A9）：按材质音效组给一个「接近官方同材质」的基准值。
     *
     * <p>口径：全部方块空手可挖（{@code requiresTool(false)}），工具只加速不设门槛，
     * 所以硬度只决定「徒手要敲多久」，不会出现「必须拿镐」的锁。
     * 玻璃按原版 0.3（Aki：保持无工具加速但可掉落，所以也不进 mineable 标签）。
     *
     * @return 硬度值；不认识的名字回落 KubeJS 默认 1.5
     */
    public static float hardnessOf(String name) {
        if (name == null) return 1.5F;
        return switch (name) {
            case "glass" -> 0.3F;
            case "wool" -> 0.8F;
            case "grass" -> 0.6F;          // 树叶/植被为主，原版树叶 0.2 偏嫩，取 0.6 折中
            case "crop" -> 0.0F;           // 花草：徒手即碎（对齐原版）
            case "vine" -> 0.2F;
            case "snow" -> 0.1F;
            case "mud" -> 0.5F;
            case "sand" -> 0.5F;
            case "gravel" -> 0.6F;
            case "ladder" -> 0.4F;
            case "bamboo" -> 1.0F;
            case "candle" -> 0.1F;
            case "calcite" -> 0.75F;
            case "basalt" -> 1.3F;
            case "amethyst" -> 1.5F;
            case "tuff" -> 1.5F;
            case "mud_bricks" -> 1.5F;
            case "bone" -> 2.0F;
            case "wood", "bamboo_wood", "cherry", "nether_wood" -> 2.0F;
            case "deepslate", "deepslate_bricks", "deepslate_tiles", "polished_deepslate" -> 3.0F;
            case "copper" -> 3.0F;
            case "lantern" -> 3.5F;
            case "lodestone" -> 3.5F;
            case "metal", "netherite", "chain", "anvil" -> 5.0F;
            case "sculk" -> 0.2F;
            default -> 1.5F;               // stone 及未列出的
        };
    }
}
