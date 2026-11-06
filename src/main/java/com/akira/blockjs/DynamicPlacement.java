package com.akira.blockjs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * BlockJS · 六向薄片的放置朝向规则（C107 ②）。
 *
 * <p>coc 的 146 个「贴面薄片」族（`*_plate` / `awning_*_plate` / `cap_*` / `hat_*` / `lifebuoys_*`）
 * 的 {@code facing} 语义是**被点击的那一面**，不是 vanilla 薄片的「朝外法线」：
 * <pre>
 *   brick_castlestone_plate  facing=up  -> 无旋转（板躺在本格 y0..4，即贴在地面）
 *                            facing=north -> x:90（板贴在北面）
 * </pre>
 * ⇒ 点地面（{@code ctx.getSide()==UP}）就该得到 {@code facing=up}；点北面就该得到 {@code facing=north}。
 * 旧代码一律取反（{@code side.getOpposite()}）⇒ 整体差一格：点地面得到 {@code facing=down}（板吊在格顶）。
 *
 * <p>数据文件 {@code assets/blockjs/faceattach.json}，由 {@code _c107_plate_place_gen.py} 生成，
 * 收录条件 = ① boxes.json 的 {@code facing=up} 盒全部 maxY≤4（贴底）**且**
 * ② 官方 jar 该 id 的 {@code facing=up} variant 无 x/y 旋转。两条缺一不进（挂画的 up 是 x:270 ⇒ 不收）。
 */
public final class DynamicPlacement {

	private DynamicPlacement() {
	}

	private static Set<String> IDS;
	private static boolean failed = false;

	private static synchronized Set<String> data() {
		if (IDS != null || failed) return IDS;
		IDS = new HashSet<>();
		try (InputStreamReader r = new InputStreamReader(
				DynamicPlacement.class.getResourceAsStream("/assets/blockjs/faceattach.json"), StandardCharsets.UTF_8)) {
			if (r == null) {
				failed = true;
				IDS = null;
				DynamicShapes.LOG.info("[BlockJS] faceattach.json 不存在，贴面放置规则不启用");
				return null;
			}
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			JsonElement arr = root.get("ids");
			if (arr != null && arr.isJsonArray()) {
				JsonArray a = arr.getAsJsonArray();
				for (JsonElement e : a) {
					if (e.isJsonPrimitive()) IDS.add(e.getAsString());
				}
			}
			DynamicShapes.LOG.info("[BlockJS] 贴面放置表已加载 {} 个方块", IDS.size());
		} catch (Throwable t) {
			failed = true;
			IDS = null;
			DynamicShapes.LOG.info("[BlockJS] 贴面放置表加载失败（走取反旧行为）: " + t);
		}
		return IDS;
	}

	/**
	 * @return true = 该 id 的 {@code facing} 应等于被点击面本身（不取反）。
	 */
	public static boolean isFaceAttached(String id) {
		if (id == null) return false;
		Set<String> s = data();
		return s != null && s.contains(id);
	}
}
