package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * BlockJS · 动态亮度：给 KubeJS 方块提供「按 blockstate 变亮度」。
 *
 * <h2>为什么需要它</h2>
 * KubeJS 的 {@code BlockBuilder.lightLevel(x)} 是【常量】——
 * javap 实证 {@code lambda$createProperties$9 = state -> (int)(lightLevel * 15.0f)}，
 * 所有状态一个值。官方的灯是 {@code state -> lighting ? 15 : 0}，KubeJS 做不到。
 *
 * <p>1.20.1 里 {@code AbstractBlockState.luminance} 是构造期算好的缓存字段，
 * 改不动——所以我们在 {@code getLuminance()} 的 getter 上注入（HEAD 拦截），
 * 命中规则就直接返回动态值，缓存字段根本没机会被读到。
 *
 * <h2>脚本侧 API（BlockBuilderMixin 追加）</h2>
 * <pre>
 * b.dynLight('lighting=true', 15)     // 键 = blockstate 变体键（子集匹配，与 dynShape 一致）
 * b.dynLight('lighting=false', 0)
 * </pre>
 * 多条命中取【最具体】（属性对最多）的一条；同键重复登记 = 后者覆盖前者；无命中回落 KubeJS 原亮度。
 *
 * <h2>灯灭了为什么不重新算光照？</h2>
 * 会的。光照引擎在 {@code setBlockState} 时对比新旧 {@code getLuminance()}，
 * 不同就触发该区域重算——本类只改 getter 返回值，正好被这个机制接住。
 *
 * <h2>性能</h2>
 * 与 DynamicShapes 相同的两级 IdentityHashMap 缓存（Block→规则、BlockState→亮度）。
 */
public final class DynamicLights {

	private DynamicLights() {
	}

	/** 一条规则：键（属性名→值，小写） + 亮度 0..15 */
	public static final class Rule {
		public final Map<String, String> key;
		public int level;

		Rule(Map<String, String> key, int level) {
			this.key = key;
			this.level = level;
		}
	}

	private static final Map<Object, List<Rule>> BY_BUILDER = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Block, List<Rule>> BY_BLOCK = new IdentityHashMap<>();
	private static final Set<Block> RESOLVED_EMPTY = Collections.newSetFromMap(new IdentityHashMap<>());
	/** BlockState → 亮度；-1 = 查过、无命中（不能放 null） */
	private static final Map<BlockState, Integer> BY_STATE = new IdentityHashMap<>();
	private static final Integer NONE = -1;

	// ---------------------------------------------------------------- 脚本侧写入
	public static void addRule(Object builder, String key, int level) {
		if (builder == null || key == null || key.isEmpty()) return;
		Map<String, String> parsed = DynamicShapes.parseKey(key);
		if (parsed.isEmpty()) return;
		int lv = level < 0 ? 0 : Math.min(level, 15);
		List<Rule> rules = BY_BUILDER.computeIfAbsent(builder, k -> new ArrayList<>(2));
		for (Rule r : rules) {
			if (r.key.equals(parsed)) {          // 同键 = 覆盖（灯不取并集）
				r.level = lv;
				return;
			}
		}
		rules.add(new Rule(parsed, lv));
	}

	public static void clearRules(Object builder) {
		if (builder != null) BY_BUILDER.remove(builder);
	}

	/** 脚本改了规则之后调用：清两级缓存 */
	public static void invalidateCache() {
		synchronized (DynamicLights.class) {
			BY_BLOCK.clear();
			RESOLVED_EMPTY.clear();
			BY_STATE.clear();
		}
	}

	// ---------------------------------------------------------------- 运行时改写（server 脚本期）
	private static final Map<Block, List<Rule>> RUNTIME = new IdentityHashMap<>();

	/** 追加/覆盖一条亮度规则。例：runtimeAdd('coc:desklamp_red', 'lighting=true', 15) */
	public static void runtimeAdd(String blockId, String key, int level) {
		Block block = DynamicShapes.blockById(blockId);
		if (block == null) return;
		Map<String, String> parsed = DynamicShapes.parseKey(key);
		if (parsed.isEmpty()) return;
		int lv = level < 0 ? 0 : Math.min(level, 15);
		synchronized (DynamicLights.class) {
			List<Rule> rules = RUNTIME.computeIfAbsent(block, k -> new ArrayList<>(2));
			for (Rule r : rules) {
				if (r.key.equals(parsed)) { r.level = lv; invalidateCache(); return; }
			}
			rules.add(new Rule(parsed, lv));
		}
		invalidateCache();
	}

	/** 清运行时亮度规则：blockId 为 null/空 = 清全部 */
	public static void runtimeClear(@Nullable String blockId) {
		synchronized (DynamicLights.class) {
			if (blockId == null || blockId.isEmpty()) {
				RUNTIME.clear();
			} else {
				Block b = DynamicShapes.blockById(blockId);
				if (b != null) RUNTIME.remove(b);
			}
		}
		invalidateCache();
	}

	// ---------------------------------------------------------------- 运行时查询
	/**
	 * @return 该状态的动态亮度；{@code null} = 没有规则命中（调用方走原逻辑）。
	 */
	@Nullable
	public static Integer lightFor(BlockState state) {
		Block block = state.getBlock();
		List<Rule> rules = rulesOf(block);
		List<Rule> rt = RUNTIME.get(block);
		boolean hasRt = rt != null && !rt.isEmpty();
		if (rules == null && !hasRt) return null;

		Integer cached = BY_STATE.get(state);
		if (cached != null) return cached == NONE ? null : cached;

		Integer out = hasRt ? resolve(state, rt) : null;      // 运行时规则优先
		if (out == null && rules != null) out = resolve(state, rules);
		if (BY_STATE.size() < 100000) {
			BY_STATE.put(state, out == null ? NONE : out);
		}
		return out;
	}

	@Nullable
	private static Integer resolve(BlockState state, List<Rule> rules) {
		Rule best = null;
		int bestN = 0;
		for (Rule r : rules) {
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
			if (n > bestN) {                     // 平局保留先登记的（确定性）
				bestN = n;
				best = r;
			}
		}
		return best == null ? null : best.level;
	}

	@Nullable
	private static List<Rule> rulesOf(Block block) {
		List<Rule> cached = BY_BLOCK.get(block);
		if (cached != null) return cached;
		if (RESOLVED_EMPTY.contains(block)) return null;
		List<Rule> rules = null;
		try {
			Object builder = DynamicShapes.builderOf(block);
			if (builder != null) rules = BY_BUILDER.get(builder);
		} catch (Throwable t) {
			DynamicShapes.LOG.warn("[BlockJS] 取 BlockBuilder 失败(light): {}", block, t);
		}
		if (rules == null || rules.isEmpty()) {
			RESOLVED_EMPTY.add(block);
			return null;
		}
		BY_BLOCK.put(block, rules);
		return rules;
	}
}
