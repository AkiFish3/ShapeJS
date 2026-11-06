package com.akira.blockjs;

import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.StringIdentifiable;

/**
 * BlockJS · 自定义枚举属性目录。
 *
 * <p>为什么需要：KubeJS 脚本（Rhino）**无法定义 Java 枚举**，而 blockstate 里键的字符串
 * 来自 {@code StringIdentifiable.asString()} —— 所以 `type=0/1/2` 想变成
 * `type=left/right/middle` 只能由 mod 提供真实的枚举类。
 *
 * <p>用法（KubeJS）：
 * <pre>
 *   let DE = Java.loadClass("com.akira.blockjs.DynamicEnums");
 *   let P = DE.get("slrb", "type");          // EnumProperty&lt;SLRB&gt;，名字是 type
 *   b.property(P);
 *   cb.set(P, "left");                       // KubeJS 有 set(EnumProperty, String) 重载
 *   let cur = ("" + st.getValue(P)).toLowerCase();   // "left"
 * </pre>
 *
 * <p>目录（kind → 值集合，顺序即 blockstate 默认值顺序）：
 * <ul>
 *   <li>{@code lrm}  : left, right, middle</li>
 *   <li>{@code slrm} : single, left, right, middle</li>
 *   <li>{@code slrb} : single, left, right, both</li>
 *   <li>{@code suml} : single, upper, middle, lower</li>
 *   <li>{@code ulm}  : upper, lower, middle</li>
 * </ul>
 */
public final class DynamicEnums {

	public enum LRM implements StringIdentifiable {
		LEFT("left"), RIGHT("right"), MIDDLE("middle");

		private final String id;
		LRM(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	public enum SLRM implements StringIdentifiable {
		SINGLE("single"), LEFT("left"), RIGHT("right"), MIDDLE("middle");

		private final String id;
		SLRM(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	public enum SLRB implements StringIdentifiable {
		SINGLE("single"), LEFT("left"), RIGHT("right"), BOTH("both");

		private final String id;
		SLRB(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	public enum SUML implements StringIdentifiable {
		SINGLE("single"), UPPER("upper"), MIDDLE("middle"), LOWER("lower");

		private final String id;
		SUML(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	public enum ULM implements StringIdentifiable {
		UPPER("upper"), LOWER("lower"), MIDDLE("middle");

		private final String id;
		ULM(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/** 只有上下两态（coc 的长镜 `half=lower|upper`）：★不能复用 ULM★，多一个 middle 会让
	 *  blockstate 缺 `half=middle` 的键 ⇒ 游戏报 missing model for variant */
	public enum UL implements StringIdentifiable {
		UPPER("upper"), LOWER("lower");

		private final String id;
		UL(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/**
	 * 垂直分段（minia {@code MT_FacingConnectV} 的 {@code shape}）：single / bottom / middle / top。
	 *
	 * <p>★不要和楼梯的 STAIR_SHAPE 混用★：柱子、竖梯、灯柱、货架这些方块的 {@code shape}
	 * 表示"竖直方向的第几段"，不是内外角。2026-10-27 全库回归发现 30 个方块被投影成了
	 * 楼梯的 5 值 shape ⇒ 永远只有 bottom 模型、且上段（top）画不出来。
	 */
	public enum VSEC implements StringIdentifiable {
		SINGLE("single"), BOTTOM("bottom"), MIDDLE("middle"), TOP("top");

		private final String id;
		VSEC(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/**
	 * miniaturia 货架 / 床架的 16 值段位（官方 {@code MT_FacingShape} 系列）。
	 *
	 * <p>取值 = 水平位置（无 / l / m / r，无前缀表示这一列只有自己）× 垂直段（s / b / m / t）：
	 * {@code single/bottom/middle/top}（无水平邻块）+ {@code lb/lm/ls/lt}、{@code mb/mm/ms/mt}、
	 * {@code rb/rm/rs/rt}。
	 *
	 * <p>★不能复用 VSEC（4 值）★：货架/床架同时要表达"左右哪一段"和"上下哪一段"，
	 * 官方 256 变体 = click4 × facing4 × shape16。
	 */
	public enum SHELF implements StringIdentifiable {
		SINGLE("single"), BOTTOM("bottom"), MIDDLE("middle"), TOP("top"),
		LB("lb"), LM("lm"), LS("ls"), LT("lt"),
		MB("mb"), MM("mm"), MS("ms"), MT("mt"),
		RB("rb"), RM("rm"), RS("rs"), RT("rt");

		private final String id;
		SHELF(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/** minia `MT_Meta5set` 的 5 个 metavariant（facade 外墙板等）：meta0..meta4 */
	public enum META5 implements StringIdentifiable {
		META0("meta0"), META1("meta1"), META2("meta2"), META3("meta3"), META4("meta4");

		private final String id;
		META5(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/**
	 * minia 屋顶瓦楼梯 / 线脚板的 8 值 {@code shape}（{@code MT_RoofStair} 系列）。
	 *
	 * <p>取值 = 原版楼梯 5 形（{@code straight/inner_left/inner_right/outer_left/outer_right}）
	 * + 半格瓦的 3 形（{@code single} 单片、{@code l_straight} 左半、{@code r_straight} 右半）。
	 *
	 * <p>★不能复用 {@link net.minecraft.state.property.Properties#STAIR_SHAPE}★：它是 vanilla 5 值枚举，
	 * 写不进 {@code l_straight}/{@code r_straight}/{@code single}；而官方 blockstate 是 click2×facing4×half2×shape8
	 * = 128 键，缺这 3 个值就是 missing model。默认值取 {@code straight}（与官方 variants 首键一致）。
	 */
	public enum ROOF8 implements StringIdentifiable {
		STRAIGHT("straight"), SINGLE("single"),
		L_STRAIGHT("l_straight"), R_STRAIGHT("r_straight"),
		INNER_LEFT("inner_left"), INNER_RIGHT("inner_right"),
		OUTER_LEFT("outer_left"), OUTER_RIGHT("outer_right");

		private final String id;
		ROOF8(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/**
	 * minia 沙发（{@code TypeSofa.EnumShape}）的 8 值 {@code shape}。
	 *
	 * <p>★与 {@link ROOF8} 长得像但**不能复用**★：官方有 {@code m_straight}（中段）而**没有** {@code straight}；
	 * ROOF8 反过来有 {@code straight} 没有 {@code m_straight}（#292：同父只保证属性集，取值集合要单独比）。
	 * 官方 blockstate 是 click2×facing4×shape8 = 64 键，缺值就是 missing model。默认值取 {@code single}。
	 *
	 * <p>★也用于 minia {@code roof_*_stairs_*}（C101，51 个 id）★：那族的官方 shape 取值与此**逐字相同**
	 * （同样是 8 值、同样只有 {@code m_straight} 没有 {@code straight}），所以直接复用本枚举，
	 * 用 {@code shapeRule("mt8")} 把 mt 规则产出的 {@code straight} 落成 {@code m_straight}。
	 */
	public enum SOFA8 implements StringIdentifiable {
		SINGLE("single"), L_STRAIGHT("l_straight"), M_STRAIGHT("m_straight"), R_STRAIGHT("r_straight"),
		INNER_LEFT("inner_left"), INNER_RIGHT("inner_right"), OUTER_LEFT("outer_left"), OUTER_RIGHT("outer_right");

		private final String id;
		SOFA8(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/** 只有左右两态（minia 的 car_light 等 `shape=left|right`） */
	public enum LR implements StringIdentifiable {
		LEFT("left"), RIGHT("right");

		private final String id;
		LR(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/** 横杆/扶手（wood_hpole）：position 六个取值，默认 z_m（沿 z 的中间段） */
	public enum HPOS implements StringIdentifiable {
		Z_M("z_m"), Z_E("z_e"), Z_W("z_w"), X_N("x_n"), X_M("x_m"), X_S("x_s");

		private final String id;
		HPOS(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/**
	 * 八向 facing（coc 的 `rug_rustic`，官方 {@code facing8}）：四个贴墙方向 + 四个 `down_*`（平铺/吊顶）。
	 *
	 * <p>★不能复用 {@link net.minecraft.state.property.Properties#HORIZONTAL_FACING}（4 值）★：
	 * 官方 blockstate 是 `facing=north|east|south|west|down_north|down_east|down_south|down_west`
	 * × `shift` = 16 键；压成 4 向会把「墙面」或「地面」整族丢掉（C20）。
	 * 默认值取 {@code north}（与官方 variants 首键一致）。
	 */
	public enum FACE8 implements StringIdentifiable {
		NORTH("north"), EAST("east"), SOUTH("south"), WEST("west"),
		DOWN_NORTH("down_north"), DOWN_EAST("down_east"), DOWN_SOUTH("down_south"), DOWN_WEST("down_west");

		private final String id;
		FACE8(String id) { this.id = id; }
		@Override public String asString() { return id; }

		/** 找不到就退回 NORTH（放置时方向名来自 Direction#getName，只会是 8 个之一） */
		public static FACE8 byName(String name) {
			for (FACE8 v : values()) {
				if (v.id.equals(name)) return v;
			}
			return NORTH;
		}
	}

	/**
	 * 屋顶斜面 8 向（minia {@code MT_8Position$EnumPoleShape}，`roof_*_hips` 系列）：n/e/s/w/ne/se/sw/nw。
	 *
	 * <p>★不能复用 {@code hpos}（6 值 x_m/x_n/x_s/z_e/z_m/z_w）★：名字和取值集合都不同（C33）。
	 * 官方 blockstate 是 `click2 × poleshape8 = 16` 键；斜向 4 值由 shape hammer 循环，放置只给 4 正向。
	 */
	public enum POLE8 implements StringIdentifiable {
		N("n"), E("e"), S("s"), W("w"),
		NE("ne"), SE("se"), SW("sw"), NW("nw");

		private final String id;
		POLE8(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	/** 荧光灯竖直款：single / up / down / both */
	public enum SUBD implements StringIdentifiable {
		SINGLE("single"), UP("up"), DOWN("down"), BOTH("both");

		private final String id;
		SUBD(String id) { this.id = id; }
		@Override public String asString() { return id; }
	}

	private DynamicEnums() {
	}

	/** kind ∈ {lrm, slrm, slrb, suml, ulm}；name = blockstate 里的属性名（如 "type"） */
	@SuppressWarnings("rawtypes")
	public static Property<?> get(String kind, String name) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("property name 不能为空");
		}
		switch (kind) {
			case "lrm": return EnumProperty.of(name, LRM.class);
			case "slrm": return EnumProperty.of(name, SLRM.class);
			case "slrb": return EnumProperty.of(name, SLRB.class);
			case "suml": return EnumProperty.of(name, SUML.class);
			case "ulm": return EnumProperty.of(name, ULM.class);
			case "subd": return EnumProperty.of(name, SUBD.class);
			case "ul": return EnumProperty.of(name, UL.class);
			case "hpos": return EnumProperty.of(name, HPOS.class);
			case "lr": return EnumProperty.of(name, LR.class);
			case "vsec": return EnumProperty.of(name, VSEC.class);
			case "shelf": return EnumProperty.of(name, SHELF.class);
			case "meta5": return EnumProperty.of(name, META5.class);
			case "roof8": return EnumProperty.of(name, ROOF8.class);
			case "sofa8": return EnumProperty.of(name, SOFA8.class);
			case "face8": return EnumProperty.of(name, FACE8.class);
			case "pole8": return EnumProperty.of(name, POLE8.class);
			default: throw new IllegalArgumentException("未知枚举目录: " + kind);
		}
	}

	/** 供 JS 取常量（一般用不到：cb.set(prop, "left") 就能传字符串） */
	public static Object constant(String kind, String value) {
		for (Object o : values(kind)) {
			if (o instanceof StringIdentifiable si && si.asString().equals(value)) {
				return o;
			}
		}
		throw new IllegalArgumentException("目录 " + kind + " 里没有值: " + value);
	}

	/** 值名列表（JS 侧做合法性校验用） */
	public static String[] names(String kind) {
		Object[] vs = values(kind);
		String[] out = new String[vs.length];
		for (int i = 0; i < vs.length; i++) {
			out[i] = ((StringIdentifiable) vs[i]).asString();
		}
		return out;
	}

	private static Object[] values(String kind) {
		switch (kind) {
			case "lrm": return LRM.values();
			case "slrm": return SLRM.values();
			case "slrb": return SLRB.values();
			case "suml": return SUML.values();
			case "ulm": return ULM.values();
			case "subd": return SUBD.values();
			case "ul": return UL.values();
			case "hpos": return HPOS.values();
			case "lr": return LR.values();
			case "vsec": return VSEC.values();
			case "shelf": return SHELF.values();
			case "meta5": return META5.values();
			case "roof8": return ROOF8.values();
			case "sofa8": return SOFA8.values();
			case "face8": return FACE8.values();
			case "pole8": return POLE8.values();
			default: throw new IllegalArgumentException("未知枚举目录: " + kind);
		}
	}
}
