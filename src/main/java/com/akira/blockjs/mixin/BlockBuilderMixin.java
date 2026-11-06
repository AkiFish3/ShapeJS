package com.akira.blockjs.mixin;

import com.akira.blockjs.DynamicLights;
import com.akira.blockjs.DynamicProps;
import com.akira.blockjs.DynamicShapes;
import com.akira.blockjs.Multiblocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 KubeJS 的 BlockBuilder 追加动态形状 API（脚本里直接可用）：
 *
 * <pre>
 * // ① 任意盒（同一个键写多条 = 取并集 ⇒ L 形/不规则形）
 * b.dynShape('shape=inner_left', 0, 0, 0, 16, 16, 8)
 * b.dynShape('shape=inner_left', 8, 0, 8, 16, 16, 16)
 *
 * // ② 实心斜坡（楼梯式碰撞）
 * b.dynSlope('facing=north', 'south', 4)                       // 整格、4 级、向南升高
 * b.dynSlope('facing=north', 'south', 4, 0,0,0, 16,16,16)      // 指定范围
 *
 * // ③ 薄板斜面（斜玻璃/斜板：一条对角带）
 * b.dynSlopePanel('facing=north', 'south', 8, 2)               // 整格、8 级、板厚 2 像素
 *
 * // ④ 清空
 * b.dynShapeClear()
 * </pre>
 *
 * 说明：
 * <ul>
 *   <li>目标是 KubeJS 的类，但注入方式是「新增方法」而非 @Inject，所以【不需要】把 KubeJS 当编译依赖；
 *       运行时 Rhino 通过反射能看到这个新增的 public 方法。</li>
 *   <li>返回值声明成 Object（编译期拿不到 BlockBuilder 类型），但运行时返回的就是 this，
 *       Rhino 是动态类型，所以 b.dynShape(...).box(...) 这种链式写法照样能用。</li>
 *   <li>所有坐标都是【像素】（0..16），内部统一 /16 转成方块内 0..1。</li>
 * </ul>
 */
@Mixin(targets = "dev.latvian.mods.kubejs.block.BlockBuilder")
public abstract class BlockBuilderMixin {

	/** 追加一条「状态键 → 碰撞/形状盒」（像素坐标，六个数） */
	public Object dynShape(String key, double x1, double y1, double z1, double x2, double y2, double z2) {
		DynamicShapes.addRule(this, key, x1, y1, z1, x2, y2, z2);
		return this;
	}

	/** 一次性登记多条（keys 与 boxes 一一对应；boxes 为长度 6 的数组）——供批量/生成脚本使用 */
	public Object dynShapeList(String[] keys, double[][] boxes) {
		if (keys == null || boxes == null) return this;
		int n = Math.min(keys.length, boxes == null ? 0 : boxes.length);
		for (int i = 0; i < n; i++) {
			double[] b = boxes[i];
			if (b == null || b.length < 6) continue;
			DynamicShapes.addRule(this, keys[i], b[0], b[1], b[2], b[3], b[4], b[5]);
		}
		return this;
	}

	// ---------------------------------------------------------------- 斜面
	/**
	 * 实心斜坡（楼梯式碰撞）：在给定范围内生成 {@code steps} 级台阶，
	 * 从 {@code dir} 的【反侧】(低) 升到 {@code dir} 侧(高)。
	 *
	 * @param key   blockstate 变体键，如 "facing=north"
	 * @param dir   升高方向：north(-z) / south(+z) / east(+x) / west(-x)
	 * @param steps 台阶数 1..32
	 */
	public Object dynSlope(String key, String dir, int steps,
			double x1, double y1, double z1, double x2, double y2, double z2) {
		int n = clampSteps(steps);
		if (n <= 0) return this;
		boolean alongZ = dir.equals("north") || dir.equals("south");
		boolean asc = dir.equals("south") || dir.equals("east");   // 是否向正方向升高
		double lo = alongZ ? z1 : x1;
		double hi = alongZ ? z2 : x2;
		double span = (hi - lo) / n;
		for (int i = 0; i < n; i++) {
			// i=0 是最低那级；asc 时从 lo 端起，否则从 hi 端起
			double a = asc ? lo + i * span : hi - (i + 1) * span;
			double b = asc ? lo + (i + 1) * span : hi - i * span;
			double top = y1 + (i + 1) * (y2 - y1) / n;
			if (alongZ) {
				DynamicShapes.addRule(this, key, x1, y1, a, x2, top, b);
			} else {
				DynamicShapes.addRule(this, key, a, y1, z1, b, top, z2);
			}
		}
		return this;
	}

	/** 整格实心斜坡快捷：范围固定 0..16 */
	public Object dynSlope(String key, String dir, int steps) {
		return dynSlope(key, dir, steps, 0, 0, 0, 16, 16, 16);
	}

	/**
	 * 薄板斜面：一条厚 {@code thickness} 像素的对角带（适合斜玻璃、斜面板）。
	 * 与 {@link #dynSlope} 的区别：每一级不是"从底垒到顶"，而是"厚度固定的斜置板"。
	 */
	public Object dynSlopePanel(String key, String dir, int steps, double thickness,
			double x1, double y1, double z1, double x2, double y2, double z2) {
		int n = clampSteps(steps);
		if (n <= 0) return this;
		boolean alongZ = dir.equals("north") || dir.equals("south");
		boolean asc = dir.equals("south") || dir.equals("east");
		double lo = alongZ ? z1 : x1;
		double hi = alongZ ? z2 : x2;
		double span = (hi - lo) / n;
		for (int i = 0; i < n; i++) {
			double a = asc ? lo + i * span : hi - (i + 1) * span;
			double b = asc ? lo + (i + 1) * span : hi - i * span;
			double top = y1 + (i + 1) * (y2 - y1) / n;
			double bot = Math.max(y1, top - thickness);
			if (alongZ) {
				DynamicShapes.addRule(this, key, x1, bot, a, x2, top, b);
			} else {
				DynamicShapes.addRule(this, key, a, bot, z1, b, top, z2);
			}
		}
		return this;
	}

	/** 整格薄板斜面快捷：范围固定 0..16 */
	public Object dynSlopePanel(String key, String dir, int steps, double thickness) {
		return dynSlopePanel(key, dir, steps, thickness, 0, 0, 0, 16, 16, 16);
	}

	private static int clampSteps(int steps) {
		if (steps < 1) return 1;
		return Math.min(steps, 32);
	}

	/** 清空 */
	public Object dynShapeClear() {
		DynamicShapes.clearRules(this);
		return this;
	}

	// ---------------------------------------------------------------- 动态亮度
	/**
	 * 追加一条「状态键 → 亮度 0..15」（超出范围自动钳制）。
	 * 例：{@code b.dynLight('lighting=true', 15); b.dynLight('lighting=false', 0);}
	 * 同键重复登记 = 覆盖；无命中回落 {@code lightLevel()} 常量。
	 */
	public Object dynLight(String key, int level) {
		DynamicLights.addRule(this, key, level);
		return this;
	}

	/** 一次性登记多条亮度（keys 与 levels 一一对应）——供批量/生成脚本使用 */
	public Object dynLightList(String[] keys, int[] levels) {
		if (keys == null || levels == null) return this;
		int n = Math.min(keys.length, levels.length);
		for (int i = 0; i < n; i++) {
			DynamicLights.addRule(this, keys[i], levels[i]);
		}
		return this;
	}

	/** 清空亮度规则 */
	public Object dynLightClear() {
		DynamicLights.clearRules(this);
		return this;
	}

	// ---------------------------------------------------------------- 遮光 / 红石 / 活塞
	/** 动态遮光率 0..15（影响光照传播：窗帘开/关、百叶窗等）。无命中回落方块原遮光 */
	public Object dynOpacity(String key, int level) {
		DynamicProps.add(DynamicProps.Chan.OPACITY, this, key, level);
		return this;
	}

	/** 弱红石功率 0..15（有功率规则的方块自动 emitsRedstonePower=true） */
	public Object dynWeakPower(String key, int level) {
		DynamicProps.add(DynamicProps.Chan.WEAK, this, key, level);
		return this;
	}

	/** 强红石功率 0..15（能充能相邻方块） */
	public Object dynStrongPower(String key, int level) {
		DynamicProps.add(DynamicProps.Chan.STRONG, this, key, level);
		return this;
	}

	/** 强弱一起设 */
	public Object dynPower(String key, int level) {
		DynamicProps.add(DynamicProps.Chan.WEAK, this, key, level);
		DynamicProps.add(DynamicProps.Chan.STRONG, this, key, level);
		return this;
	}

	/** 活塞行为：normal / destroy / block / ignore / push_only */
	public Object dynPiston(String key, String behavior) {
		DynamicProps.addPiston(this, key, behavior);
		return this;
	}

	// ---------------------------------------------------------------- 多方块足迹
	/**
	 * 声明多方块足迹：以放置格为锚点，xz 偏移闭区间 [x1..x2]×[z1..z2]（含 0,0；
	 * 声明系 = facing=north，放置时按锚点 facing 旋转）。范围钳制在 ±2（5×5 上限）。
	 *
	 * <pre>
	 * b.dynFootprint(-1, -1, 1, 1)   // 3×3×1，锚点在中心
	 * </pre>
	 *
	 * 放置时周围格自动填隐形 extension（被占则整体弹出）；拆任意一格 → 整个结构拆除，
	 * 掉落物在锚点。锚点的 dynShape 盒可跨格写（像素坐标允许 <0 / >16），
	 * extension 格会把锚点形状平移过来当自己的碰撞/高亮/选取盒。
	 */
	public Object dynFootprint(int x1, int z1, int x2, int z2) {
		Multiblocks.addFootprint(this, x1, z1, x2, z2);
		return this;
	}

	/** 清除足迹 */
	public Object dynFootprintClear() {
		Multiblocks.clearFootprint(this);
		return this;
	}
}
