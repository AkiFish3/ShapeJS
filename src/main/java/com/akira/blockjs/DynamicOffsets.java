package com.akira.blockjs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * BlockJS · 渲染期位移（C114 建通道 / C158 扩成通用 IMTOffset 等价物）。
 *
 * <p>官方 minia 用 {@code IMTOffset.mtGetOffset} + {@code Properties.offsetFunction}
 * （1.21 API）在渲染期平移模型。1.20.1 没有 {@code offsetFunction}，所以本 fork 走
 * {@code AbstractBlockState#getModelOffset} 的 HEAD 拦截（与原版草/花的偏移同一条路径，
 * {@code BlockModelRenderer} 会把它加到模型平移上）。
 *
 * <p>数据文件：{@code assets/blockjs/offsets.json}。两种写法都支持：
 * <pre>
 * "miniaturia:barrel":  [0, 0.5, 0]                        // 旧式：只在 half=top 生效
 * "miniaturia:tv": {
 *     "facing=north,half=bottom": [0, 0.2, 0.2],           // 条件命中即用（按书写顺序取第一条）
 *     "":                        [0, 0, 0]                 // 空条件 = 任意状态
 * }
 * </pre>
 * 条件串 = 逗号分隔的 {@code 属性=取值}，全部满足才算命中；属性名/取值都按 blockstate 字面写。
 * 旧式（数组）保留 {@code half=top} 门槛 —— 那是 C114 对 47 个 minia 方块的既定语义，
 * {@code half=bottom} 与 vanilla 楼梯（靠 {@code "x":180} 翻转，不是平移）都不受影响。
 *
 * <p>缓存：BlockState → Vec3d（IdentityHashMap），{@link Vec3d#ZERO} 作「查过、无位移」哨兵。
 */
public final class DynamicOffsets {

	private DynamicOffsets() {
	}

	/** cond == null 表示旧式（仅 half=top 生效）；cond 为空串表示无条件。 */
	private record Rule(@Nullable String cond, Vec3d v) {
	}

	private static Map<String, List<Rule>> DATA;
	private static boolean failed = false;
	private static final Map<BlockState, Vec3d> CACHE = new IdentityHashMap<>();

	@Nullable
	private static Vec3d vec(JsonElement e) {
		if (!e.isJsonArray()) return null;
		JsonArray a = e.getAsJsonArray();
		if (a.size() < 3) return null;
		return new Vec3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
	}

	private static synchronized Map<String, List<Rule>> data() {
		if (DATA != null || failed) return DATA;
		DATA = new HashMap<>();
		int oldStyle = 0;
		int conds = 0;
		try (InputStreamReader r = new InputStreamReader(
				DynamicOffsets.class.getResourceAsStream("/assets/blockjs/offsets.json"), StandardCharsets.UTF_8)) {
			if (r == null) {
				failed = true;
				DATA = null;
				DynamicShapes.LOG.info("[BlockJS] offsets.json 不存在，渲染位移不启用");
				return null;
			}
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			for (var e : root.entrySet()) {
				JsonElement v = e.getValue();
				List<Rule> rules = new ArrayList<>();
				if (v.isJsonArray()) {
					Vec3d p = vec(v);
					if (p != null) {
						rules.add(new Rule(null, p));
						oldStyle++;
					}
				} else if (v.isJsonObject()) {
					for (var c : v.getAsJsonObject().entrySet()) {
						Vec3d p = vec(c.getValue());
						if (p != null) {
							rules.add(new Rule(c.getKey(), p));
							conds++;
						}
					}
				}
				if (!rules.isEmpty()) DATA.put(e.getKey(), rules);
			}
			DynamicShapes.LOG.info("[BlockJS] 位移表已加载 {} 个方块（旧式 half=top {} / 条件 {}）",
					DATA.size(), oldStyle, conds);
		} catch (Throwable t) {
			failed = true;
			DATA = null;
			DynamicShapes.LOG.info("[BlockJS] 位移表加载失败（渲染位移不启用）: " + t);
		}
		return DATA;
	}

	/** 条件串 = 逗号分隔的 {@code 属性=取值}，全部满足才命中。 */
	private static boolean matches(BlockState state, String cond) {
		if (cond.isEmpty()) return true;
		for (String part : cond.split(",")) {
			int i = part.indexOf('=');
			if (i <= 0) return false;
			String key = part.substring(0, i).trim();
			String val = part.substring(i + 1).trim();
			boolean found = false;
			for (Property<?> p : state.getProperties()) {
				if (!p.getName().equals(key)) continue;
				found = true;
				if (!nameOf(state, p).equals(val)) return false;
				break;
			}
			if (!found) return false;
		}
		return true;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String nameOf(BlockState state, Property p) {
		return p.name((Comparable) state.get(p));
	}

	/**
	 * @return 该状态的渲染位移；{@code null} = 不接管（交回原版 {@code getModelOffset}）。
	 */
	@Nullable
	public static Vec3d offsetFor(BlockState state) {
		Vec3d cached = CACHE.get(state);
		if (cached != null) return cached == Vec3d.ZERO ? null : cached;

		Map<String, List<Rule>> table = data();
		if (table == null || table.isEmpty()) {
			CACHE.put(state, Vec3d.ZERO);
			return null;
		}
		String id;
		try {
			id = Registries.BLOCK.getId(state.getBlock()).toString();
		} catch (Throwable t) {
			return null;
		}
		List<Rule> rules = table.get(id);
		if (rules == null) {
			CACHE.put(state, Vec3d.ZERO);
			return null;
		}
		boolean top = state.contains(Properties.BLOCK_HALF)
				&& state.get(Properties.BLOCK_HALF) == BlockHalf.TOP;
		for (Rule r : rules) {
			if (r.cond == null) {
				if (!top) continue;
			} else if (!matches(state, r.cond)) {
				continue;
			}
			CACHE.put(state, r.v);
			return r.v == Vec3d.ZERO ? null : r.v;
		}
		CACHE.put(state, Vec3d.ZERO);
		return null;
	}
}
