package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * BlockJS · 动态杂项属性：遮光率 / 红石功率 / 活塞行为，全部按 blockstate 分派。
 *
 * <p>与 DynamicShapes/DynamicLights 同一套模式：builder 规则表 → Block 解析缓存 →
 * BlockState 结果缓存；键 = 变体键子集匹配，多命中取最具体。
 *
 * <ul>
 *   <li>{@code dynOpacity(key, 0..15)}   → {@code AbstractBlock.getOpacity}（遮光，影响光照传播）</li>
 *   <li>{@code dynWeakPower/dynStrongPower/dynPower(key, 0..15)} → 弱/强红石功率
 *       （有功率规则的方块会自动让 {@code emitsRedstonePower} 返回 true）</li>
 *   <li>{@code dynPiston(key, "normal|destroy|block|ignore|push_only")} → {@code getPistonBehavior}</li>
 * </ul>
 */
public final class DynamicProps {

	public enum Chan { OPACITY, WEAK, STRONG, PISTON }

	private DynamicProps() {
	}

	public static final class Rule {
		public final Map<String, String> key;
		public int num;                       // OPACITY/WEAK/STRONG 用
		public PistonBehavior piston;         // PISTON 用

		Rule(Map<String, String> key) {
			this.key = key;
		}
	}

	private static final Map<Chan, Map<Object, List<Rule>>> BY_BUILDER = new EnumMap<>(Chan.class);
	private static final Map<Chan, Map<Block, List<Rule>>> BY_BLOCK = new EnumMap<>(Chan.class);
	private static final Map<Chan, Set<Block>> RESOLVED_EMPTY = new EnumMap<>(Chan.class);
	@SuppressWarnings("unchecked")
	private static final Map<BlockState, Object>[] STATE_CACHE = new Map[Chan.values().length];
	private static final Object NONE = new Object();

	static {
		for (Chan c : Chan.values()) {
			BY_BUILDER.put(c, Collections.synchronizedMap(new WeakHashMap<>()));
			BY_BLOCK.put(c, new IdentityHashMap<>());
			RESOLVED_EMPTY.put(c, Collections.newSetFromMap(new IdentityHashMap<>()));
			STATE_CACHE[c.ordinal()] = new IdentityHashMap<>();
		}
	}

	// ---------------------------------------------------------------- 脚本侧写入
	public static void add(Chan chan, Object builder, String key, int num) {
		Rule r = rule(chan, builder, key);
		if (r != null) r.num = num < 0 ? 0 : Math.min(num, 15);
	}

	public static void addPiston(Object builder, String key, String behavior) {
		Rule r = rule(Chan.PISTON, builder, key);
		if (r == null) return;
		try {
			r.piston = PistonBehavior.valueOf(behavior.trim().toUpperCase());
		} catch (Exception e) {
			DynamicShapes.LOG.warn("[BlockJS] 未知活塞行为 '{}', 可选 normal/destroy/block/ignore/push_only", behavior);
		}
	}

	@Nullable
	private static Rule rule(Chan chan, Object builder, String key) {
		if (builder == null || key == null || key.isEmpty()) return null;
		Map<String, String> parsed = DynamicShapes.parseKey(key);
		if (parsed.isEmpty()) return null;
		List<Rule> rules = BY_BUILDER.get(chan).computeIfAbsent(builder, k -> new ArrayList<>(2));
		for (Rule r : rules) {
			if (r.key.equals(parsed)) return r;   // 同键覆盖
		}
		Rule r = new Rule(parsed);
		rules.add(r);
		return r;
	}

	public static void clearRules(Chan chan, Object builder) {
		if (builder != null) BY_BUILDER.get(chan).remove(builder);
	}

	// ---------------------------------------------------------------- 运行时改写（server 脚本期）
	private static final Map<Chan, Map<Block, List<Rule>>> RUNTIME = new EnumMap<>(Chan.class);
	static {
		for (Chan c : Chan.values()) RUNTIME.put(c, new IdentityHashMap<>());
	}

	public static void runtimeOpacity(String blockId, String key, int level) {
		runtimeAdd(Chan.OPACITY, blockId, key, level);
	}

	public static void runtimeWeakPower(String blockId, String key, int level) {
		runtimeAdd(Chan.WEAK, blockId, key, level);
	}

	public static void runtimeStrongPower(String blockId, String key, int level) {
		runtimeAdd(Chan.STRONG, blockId, key, level);
	}

	public static void runtimePiston(String blockId, String key, String behavior) {
		Block block = DynamicShapes.blockById(blockId);
		if (block == null) return;
		PistonBehavior pb;
		try {
			pb = PistonBehavior.valueOf(behavior.trim().toUpperCase());
		} catch (Exception e) {
			DynamicShapes.LOG.warn("[BlockJS] 未知活塞行为 '{}'", behavior);
			return;
		}
		Map<String, String> parsed = DynamicShapes.parseKey(key);
		if (parsed.isEmpty()) return;
		synchronized (DynamicProps.class) {
			List<Rule> rules = RUNTIME.get(Chan.PISTON).computeIfAbsent(block, k -> new ArrayList<>(2));
			for (Rule r : rules) {
				if (r.key.equals(parsed)) { r.piston = pb; invalidateCache(); return; }
			}
			Rule r = new Rule(parsed);
			r.piston = pb;
			rules.add(r);
		}
		invalidateCache();
	}

	private static void runtimeAdd(Chan chan, String blockId, String key, int num) {
		Block block = DynamicShapes.blockById(blockId);
		if (block == null) return;
		Map<String, String> parsed = DynamicShapes.parseKey(key);
		if (parsed.isEmpty()) return;
		int v = num < 0 ? 0 : Math.min(num, 15);
		synchronized (DynamicProps.class) {
			List<Rule> rules = RUNTIME.get(chan).computeIfAbsent(block, k -> new ArrayList<>(2));
			for (Rule r : rules) {
				if (r.key.equals(parsed)) { r.num = v; invalidateCache(); return; }
			}
			Rule r = new Rule(parsed);
			r.num = v;
			rules.add(r);
		}
		invalidateCache();
	}

	/** 清运行时杂项规则：blockId 为 null/空 = 清全部 */
	public static void runtimeClear(@Nullable String blockId) {
		synchronized (DynamicProps.class) {
			for (Chan c : Chan.values()) {
				if (blockId == null || blockId.isEmpty()) {
					RUNTIME.get(c).clear();
				} else {
					Block b = DynamicShapes.blockById(blockId);
					if (b != null) RUNTIME.get(c).remove(b);
				}
			}
		}
		invalidateCache();
	}

	public static void invalidateCache() {
		synchronized (DynamicProps.class) {
			for (Chan c : Chan.values()) {
				BY_BLOCK.get(c).clear();
				RESOLVED_EMPTY.get(c).clear();
				STATE_CACHE[c.ordinal()].clear();
			}
		}
	}

	// ---------------------------------------------------------------- 运行时查询
	@Nullable
	private static Rule match(Chan chan, BlockState state) {
		Block block = state.getBlock();
		List<Rule> rules = rulesOf(chan, block);
		List<Rule> rt = RUNTIME.get(chan).get(block);
		boolean hasRt = rt != null && !rt.isEmpty();
		if (rules == null && !hasRt) return null;

		Map<BlockState, Object> cache = STATE_CACHE[chan.ordinal()];
		Object cached = cache.get(state);
		if (cached != null) return cached == NONE ? null : (Rule) cached;

		Rule best = null;
		int bestN = 0;
		// 运行时规则先遍历：n > bestN 严格大于 ⇒ 同具体度时 runtime 胜出
		for (int pass = 0; pass < 2; pass++) {
			List<Rule> list = pass == 0 ? rt : rules;
			if (list == null) continue;
			for (Rule r : list) {
				int n = 0;
				boolean ok = true;
				for (Map.Entry<String, String> e : r.key.entrySet()) {
					String v = DynamicShapes.propValueOf(state, e.getKey());
					if (v == null || !v.equals(e.getValue())) {
						ok = false;
						break;
					}
					n++;
				}
				if (!ok || n == 0) continue;
				if (n > bestN) {
					bestN = n;
					best = r;
				}
			}
		}
		if (cache.size() < 100000) cache.put(state, best == null ? NONE : best);
		return best;
	}

	/** 遮光率（0..15）；null = 无规则 */
	@Nullable
	public static Integer opacityFor(BlockState state) {
		Rule r = match(Chan.OPACITY, state);
		return r == null ? null : r.num;
	}

	@Nullable
	public static Integer weakPowerFor(BlockState state) {
		Rule r = match(Chan.WEAK, state);
		return r == null ? null : r.num;
	}

	@Nullable
	public static Integer strongPowerFor(BlockState state) {
		Rule r = match(Chan.STRONG, state);
		return r == null ? null : r.num;
	}

	@Nullable
	public static PistonBehavior pistonFor(BlockState state) {
		Rule r = match(Chan.PISTON, state);
		return r == null ? null : r.piston;
	}

	/** 该方块有没有任何红石功率规则（给 emitsRedstonePower 用；便宜、不查状态） */
	public static boolean hasRedstone(Block block) {
		return rulesOf(Chan.WEAK, block) != null || rulesOf(Chan.STRONG, block) != null
				|| RUNTIME.get(Chan.WEAK).containsKey(block) || RUNTIME.get(Chan.STRONG).containsKey(block);
	}

	@Nullable
	private static List<Rule> rulesOf(Chan chan, Block block) {
		Map<Block, List<Rule>> byBlock = BY_BLOCK.get(chan);
		List<Rule> cached = byBlock.get(block);
		if (cached != null) return cached;
		if (RESOLVED_EMPTY.get(chan).contains(block)) return null;
		List<Rule> rules = null;
		try {
			Object builder = DynamicShapes.builderOf(block);
			if (builder != null) rules = BY_BUILDER.get(chan).get(builder);
		} catch (Throwable t) {
			DynamicShapes.LOG.warn("[BlockJS] 取 BlockBuilder 失败(props): {}", block, t);
		}
		if (rules == null || rules.isEmpty()) {
			RESOLVED_EMPTY.get(chan).add(block);
			return null;
		}
		byBlock.put(block, rules);
		return rules;
	}
}
