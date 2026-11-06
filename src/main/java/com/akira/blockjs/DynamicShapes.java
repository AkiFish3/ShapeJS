package com.akira.blockjs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * BlockJS 核心：给 KubeJS 方块提供「按 blockstate 动态碰撞/形状」。
 *
 * <h2>为什么需要它</h2>
 * KubeJS 的 {@code BlockBuilder.customShape} 是 {@code List<Box>}（合成一个静态 VoxelShape），
 * {@code BasicBlockJS.shape} 也是单值字段 —— 没有任何「按属性返回不同形状」的入口；
 * {@code createCustom} 又不走 builder（会丢掉 property / placementState / blockEntity）。
 * 所以只能用 mixin 在 {@code getCollisionShape} / {@code getOutlineShape} 上接管。
 *
 * <h2>脚本侧 API（由 BlockBuilderMixin 加进 BlockBuilder）</h2>
 * <pre>
 * StartupEvents.registry('block', event => {
 *   let b = event.create('minia:window_slanted_white', 'basic')
 *   b.dynShape('half=bottom', 0, 0, 0, 16, 8, 16)     // 键 = blockstate 变体键语法（子集匹配）
 *   b.dynShape('half=top',    0, 8, 0, 16, 16, 16)
 *   // 同一个键写多条 = 取并集（例如拐角 L 形）
 *   b.dynShape('shape=inner_left',  0, 0, 0, 8, 16, 16)
 *   b.dynShape('shape=inner_left',  0, 0, 8, 16, 16, 16)
 * })
 * </pre>
 * 匹配规则与 Minecraft 的 blockstate {@code variants} 一致：键里写到的属性必须全部相等；
 * 多条命中时取【最具体】（属性对最多）的一组并集；没有任何命中则回落到 KubeJS 自己的形状。
 *
 * <h2>性能</h2>
 * {@code getCollisionShape} 是热路径（实体移动每 tick 每方块都调），所以：
 * <ul>
 *   <li>Block → 规则表 用 IdentityHashMap 缓存，且「查过没有」也记下来，不重复反射；</li>
 *   <li>BlockState → VoxelShape 再缓存一层（BlockState 是规范化单例，IdentityHashMap 安全）。</li>
 * </ul>
 */
public final class DynamicShapes {

	public static final String MOD_ID = "blockjs";
	public static final Logger LOG = LoggerFactory.getLogger("BlockJS");

	private DynamicShapes() {
	}

	/** 一条规则：键（属性名→值，全部小写） + 若干 AABB */
	public static final class Rule {
		public final Map<String, String> key;
		public final List<Box> boxes = new ArrayList<>(2);

		Rule(Map<String, String> key) {
			this.key = key;
		}
	}

	/** builder 对象 → 规则表（builder 用弱引用键，避免长期占用） */
	private static final Map<Object, List<Rule>> BY_BUILDER = Collections.synchronizedMap(new WeakHashMap<>());
	/** Block → 已解析的规则（热路径缓存） */
	private static final Map<Block, List<Rule>> BY_BLOCK = new IdentityHashMap<>();
	/** 已经找过 builder 且【没有】规则的方块（别每次反射） */
	private static final Set<Block> RESOLVED_EMPTY = Collections.newSetFromMap(new IdentityHashMap<>());
	/** BlockState → 形状（规范化单例，IdentityHashMap 安全；空形状=无命中） */
	private static final Map<BlockState, VoxelShape> BY_STATE = new IdentityHashMap<>();
	/** 「无命中」的占位（不能往 map 里放 null） */
	private static final VoxelShape NONE = VoxelShapes.empty();

	// ---------------------------------------------------------------- 脚本侧写入
	public static void addRule(Object builder, String key, double x1, double y1, double z1,
			double x2, double y2, double z2) {
		if (builder == null || key == null || key.isEmpty()) return;
		Map<String, String> parsed = parseKey(key);
		if (parsed.isEmpty()) return;
		List<Rule> rules = BY_BUILDER.computeIfAbsent(builder, k -> new ArrayList<>(4));
		Rule target = null;
		for (Rule r : rules) {
			if (r.key.equals(parsed)) {
				target = r;
				break;
			}
		}
		if (target == null) {
			target = new Rule(parsed);
			rules.add(target);
		}
		target.boxes.add(newBox(x1, y1, z1, x2, y2, z2));
	}

	public static void clearRules(Object builder) {
		if (builder != null) BY_BUILDER.remove(builder);
	}

	/** 脚本改了规则之后调用：把两级缓存清掉（热路径会重新解析） */
	public static void invalidateCache() {
		synchronized (DynamicShapes.class) {
			BY_BLOCK.clear();
			RESOLVED_EMPTY.clear();
			BY_STATE.clear();
		}
	}

	private static Box newBox(double x1, double y1, double z1, double x2, double y2, double z2) {
		return new Box(x1 / 16.0D, y1 / 16.0D, z1 / 16.0D, x2 / 16.0D, y2 / 16.0D, z2 / 16.0D);
	}

	/** "facing=north,shape=inner_left" → {facing:north, shape:inner_left} */
	public static Map<String, String> parseKey(String key) {
		Map<String, String> out = new HashMap<>(4);
		for (String part : key.split(",")) {
			String s = part.trim();
			if (s.isEmpty()) continue;
			int eq = s.indexOf('=');
			if (eq <= 0) continue;
			out.put(s.substring(0, eq).trim().toLowerCase(), s.substring(eq + 1).trim().toLowerCase());
		}
		return out;
	}

	// ---------------------------------------------------------------- 运行时改写（server 脚本期）
	/** 运行时规则：Block → 规则（优先级高于 builder 规则；IdentityHashMap + 显式失效） */
	private static final Map<Block, List<Rule>> RUNTIME = new IdentityHashMap<>();

	/**
	 * server 脚本期追加/覆盖一条形状规则（像素坐标六参）。
	 * Rhino 例：{@code Java.loadClass('com.akira.blockjs.DynamicShapes').runtimeAdd('coc:table', 'facing=north', 0,0,0,32,16,16)}
	 */
	public static void runtimeAdd(String blockId, String key, double x1, double y1, double z1,
			double x2, double y2, double z2) {
		Block block = blockById(blockId);
		if (block == null) return;
		Map<String, String> parsed = parseKey(key);
		if (parsed.isEmpty()) return;
		synchronized (DynamicShapes.class) {
			List<Rule> rules = RUNTIME.computeIfAbsent(block, k -> new ArrayList<>(4));
			Rule target = null;
			for (Rule r : rules) {
				if (r.key.equals(parsed)) { target = r; break; }
			}
			if (target == null) { target = new Rule(parsed); rules.add(target); } else { target.boxes.clear(); }
			target.boxes.add(newBox(x1, y1, z1, x2, y2, z2));
		}
		invalidateCache();
	}

	/** 清运行时形状规则：blockId 为 null/空 = 清全部 */
	public static void runtimeClear(@Nullable String blockId) {
		synchronized (DynamicShapes.class) {
			if (blockId == null || blockId.isEmpty()) {
				RUNTIME.clear();
			} else {
				Block b = blockById(blockId);
				if (b != null) RUNTIME.remove(b);
			}
		}
		invalidateCache();
	}

	@Nullable
	static Block blockById(String blockId) {
		Identifier id = Identifier.tryParse(blockId);
		if (id == null || !Registries.BLOCK.containsId(id)) {
			LOG.warn("[BlockJS] runtimeAdd: 未知方块 id '{}'", blockId);
			return null;
		}
		return Registries.BLOCK.get(id);
	}

	// ---------------------------------------------------------------- 运行时查询
	/**
	 * @return 该状态对应的动态形状；{@code null} = 没有规则命中（调用方应走原逻辑）。
	 */
	@Nullable
	public static VoxelShape shapeFor(BlockState state) {
		Block block = state.getBlock();
		List<Rule> rules = rulesOf(block);
		List<Rule> rt = RUNTIME.get(block);
		if (rules == null && (rt == null || rt.isEmpty())) return null;   // 该方块根本没有规则

		VoxelShape cached = BY_STATE.get(state);
		if (cached != null) return cached == NONE ? null : cached;

		List<Rule> all = rules;
		if (rt != null && !rt.isEmpty()) {
			all = new ArrayList<>(rules == null ? rt.size() : rules.size() + rt.size());
			if (rules != null) all.addAll(rules);
			all.addAll(rt);   // 同键已在 runtimeAdd 里覆盖；不同键同具体度时按规则 = 取并集
		}
		VoxelShape out = resolve(state, all);
		if (BY_STATE.size() < 100000) {                        // 防御：别无限长
			BY_STATE.put(state, out == null ? NONE : out);
		}
		return out;
	}

	@Nullable
	private static VoxelShape resolve(BlockState state, List<Rule> rules) {
		List<Rule> hit = null;
		int best = 0;
		for (Rule r : rules) {
			int n = 0;
			boolean ok = true;
			for (Map.Entry<String, String> e : r.key.entrySet()) {
				String v = propValueOf(state, e.getKey());
				if (v == null || !v.equals(e.getValue())) {
					ok = false;
					break;
				}
				n++;
			}
			if (!ok || n == 0) continue;
			if (n > best) {
				best = n;
				hit = new ArrayList<>(2);
				hit.add(r);
			} else if (n == best && hit != null) {
				hit.add(r);
			}
		}
		if (hit == null) return null;

		VoxelShape out = VoxelShapes.empty();
		for (Rule r : hit) {
			for (Box b : r.boxes) {
				out = VoxelShapes.union(out, VoxelShapes.cuboid(b));
			}
		}
		return out;
	}

	@Nullable
	static String propValueOf(BlockState state, String name) {   // 包级可见：DynamicLights 复用
		for (Property<?> p : state.getProperties()) {
			if (p.getName().equals(name)) {
				Comparable<?> v = state.get(p);
				return v == null ? null : String.valueOf(v).toLowerCase();
			}
		}
		return null;
	}

	@Nullable
	private static List<Rule> rulesOf(Block block) {
		List<Rule> cached = BY_BLOCK.get(block);
		if (cached != null) return cached;
		if (RESOLVED_EMPTY.contains(block)) return null;       // 已确认无规则
		List<Rule> rules = null;
		try {
			Object builder = builderOf(block);
			if (builder != null) rules = BY_BUILDER.get(builder);
		} catch (Throwable t) {
			LOG.warn("[BlockJS] 取 BlockBuilder 失败: {}", block, t);
		}
		if (rules == null || rules.isEmpty()) {
			RESOLVED_EMPTY.add(block);
			return null;
		}
		BY_BLOCK.put(block, rules);
		return rules;
	}

	/**
	 * 反射拿 KubeJS 的 BlockBuilder。
	 * KubeJS 自己用 mixin 给 {@code Block} 加了 {@code kjs$getBlockBuilder()}
	 * （见 {@code BlockBuilder.transformObject → Block.kjs$setBlockBuilder}），
	 * 所以【所有】KubeJS 方块（basic / cardinal / shaped / …）都拿得到。
	 */
	@Nullable
	static Object builderOf(Block block) throws Exception {   // 包级可见：DynamicLights 复用
		java.lang.reflect.Method m = block.getClass().getMethod("kjs$getBlockBuilder");
		return m.invoke(block);
	}

	/** 供调试：把某方块的规则打印出来 */
	public static String describe(Block block) {
		Identifier id = Registries.BLOCK.getId(block);
		List<Rule> rules = rulesOf(block);
		if (rules == null) return id + " (无动态形状)";
		StringBuilder sb = new StringBuilder(id + " 规则 " + rules.size() + " 条:");
		for (Rule r : rules) {
			sb.append("\n  ").append(r.key).append(" -> ").append(r.boxes.size()).append(" box");
		}
		return sb.toString();
	}
}
