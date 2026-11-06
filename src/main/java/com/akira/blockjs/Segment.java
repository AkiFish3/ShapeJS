package com.akira.blockjs;

import net.minecraft.util.StringIdentifiable;

/**
 * 梁 / 梁柱的「左中右」段位。
 *
 * <p>用它替代旧数据里的 {@code type=0/1/2}：可读、可自解释，blockstate 里写成
 * {@code type=left} 而不是 {@code type=0}。
 */
public enum Segment implements StringIdentifiable {
    LEFT("left"),
    MIDDLE("middle"),
    RIGHT("right");

    private final String name;

    Segment(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
