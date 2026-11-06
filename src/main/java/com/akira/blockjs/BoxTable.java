package com.akira.blockjs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧版 {@code BLOCKJS_RULES} 的运行时查表（真值碰撞盒）。
 *
 * <p>数据来自 <b>之前搬运的代码</b>（`blockjs_dyn.js` → `global.BLOCKJS_RULES`），
 * 覆盖 811 个方块 / 8560 个状态：vslab 467 · stair_v 151 · vstairs 49 · skylight_half 135 · slanted 9。
 * 这些盒是旧版从官方 jar 的 {@code getShape} 里逐状态取出来的，**比自己按 facing/shape 推的准**。
 *
 * <p>键格式：{@code "facing=east,shape=outer_left"}，值是像素盒 {@code [[x1,y1,z1,x2,y2,z2], ...]}（0..16）。
 * 匹配规则采用<b>子集匹配</b>：键里写出的属性必须全部相等；没写的属性不参与比较
 * （与 blockstate 的 variant 匹配语义一致）。
 */
public final class BoxTable {

    private BoxTable() {}

    private record Entry(Map<String, String> cond, VoxelShape shape) {}

    private static Map<String, List<Entry>> DATA;
    private static final Map<BlockState, VoxelShape> CACHE = new HashMap<>();
    private static boolean failed = false;

    /**
     * ★C139 第二通道：碰撞覆盖表★ 轮廓与碰撞在官方是**两套**（`m_5909_/m_5940_` vs `m_5939_`），
     * 但本 fork 的 {@code kind=boxed} 只有一条 {@code boxes.json}。这份可选覆盖表只放「碰撞≠轮廓」的 id，
     * 缺省（无该 id / 无匹配 cond / 文件不存在）一律回落 {@code boxes.json}，因此对既有 9 千多个 id 零影响。
     * 值写 {@code []} = **该状态碰撞为空**（与「没有条目」是两件事，不要混）。
     */
    private static Map<String, List<Entry>> COLL;
    private static final Map<BlockState, VoxelShape> COLL_CACHE = new HashMap<>();
    private static boolean collFailed = false;

    private static Map<String, List<Entry>> load(String path) {
        try (InputStreamReader r = new InputStreamReader(
                BoxTable.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            Map<String, List<Entry>> out = new HashMap<>();
            for (var idEntry : root.entrySet()) {
                List<Entry> list = new ArrayList<>();
                for (var stateEntry : idEntry.getValue().getAsJsonObject().entrySet()) {
                    Map<String, String> cond = new HashMap<>();
                    String key = stateEntry.getKey();
                    if (!key.isEmpty()) {
                        for (String tok : key.split(",")) {
                            int eq = tok.indexOf('=');
                            if (eq > 0) cond.put(tok.substring(0, eq).trim(), tok.substring(eq + 1).trim());
                        }
                    }
                    VoxelShape shape = VoxelShapes.empty();
                    for (JsonElement el : stateEntry.getValue().getAsJsonArray()) {
                        var a = el.getAsJsonArray();
                        shape = VoxelShapes.union(shape, VoxelShapes.cuboid(
                                a.get(0).getAsDouble() / 16.0, a.get(1).getAsDouble() / 16.0, a.get(2).getAsDouble() / 16.0,
                                a.get(3).getAsDouble() / 16.0, a.get(4).getAsDouble() / 16.0, a.get(5).getAsDouble() / 16.0));
                    }
                    list.add(new Entry(cond, shape));
                }
                out.put(idEntry.getKey(), list);
            }
            return out;
        } catch (Throwable t) {
            DynamicShapes.LOG.info("[BlockJS] " + path + " 读取失败: " + t);
            return null;
        }
    }

    private static synchronized Map<String, List<Entry>> data() {
        if (DATA != null || failed) return DATA;
        DATA = load("/assets/blockjs/boxes.json");
        if (DATA == null) {
            failed = true;
            DynamicShapes.LOG.info("[BlockJS] 盒表加载失败（回落自算几何）");
        } else {
            DynamicShapes.LOG.info("[BlockJS] 盒表已加载 {} 个方块", DATA.size());
        }
        return DATA;
    }

    /** 碰撞覆盖表（可选；文件不存在 = 空表，不是错误） */
    private static synchronized Map<String, List<Entry>> collision() {
        if (COLL != null || collFailed) return COLL;
        COLL = load("/assets/blockjs/boxes_collision.json");
        if (COLL == null) {
            collFailed = true;
        } else {
            DynamicShapes.LOG.info("[BlockJS] 碰撞覆盖表已加载 {} 个方块", COLL.size());
        }
        return COLL;
    }

    private static boolean matches(BlockState state, Map<String, String> cond) {
        for (var e : cond.entrySet()) {
            boolean found = false;
            for (Property<?> p : state.getProperties()) {
                if (!p.getName().equals(e.getKey())) continue;
                found = true;
                // ★枚举属性必须用 Property.name(value) 取「注册名」★
                // `String.valueOf(state.get(p))` 对 EnumProperty 给的是枚举常量名（`BOTTOM`/`STRAIGHT`/`SINGLE`），
                // 而 blockstate 与 boxes.json 的 cond 一律是小写注册名（`bottom`/`straight`/`single`）
                // ⇒ 2026-11-04 之前所有含 `half=` / `shape=` / `type=` / `position=` 的 cond **从未命中**，
                //    query 返回 null、静默回落自算几何（C122 实机逮到：coffeetable 0.25 vs 盒表 0.1101）。
                // 这里两种写法都接受：注册名优先，旧的大写写法也保留（向后兼容）。
                String canonical = canonicalName(state, p);
                String raw = String.valueOf(state.get(p));
                if (!canonical.equals(e.getValue()) && !raw.equals(e.getValue())) return false;
                break;
            }
            if (!found) return false;   // 状态里没这个属性 ⇒ 不匹配（避免把 key 忽略掉）
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String canonicalName(BlockState state, Property<?> p) {
        try {
            Property raw = p;
            return raw.name(state.get(raw));
        } catch (Throwable t) {
            return "";
        }
    }

    /** 预热：注册完方块后主动解析一次（5.3MB JSON，懒加载会在第一次碰到 boxed 方块时卡一下） */
    public static void warmup() {
        data();
        collision();
    }

    /** 查不到返回 null（调用方回落到自算几何） */
    public static VoxelShape query(String id, BlockState state) {
        if (id == null) return null;
        Map<String, List<Entry>> d = data();
        if (d == null) return null;
        List<Entry> list = d.get(id);
        if (list == null) return null;
        VoxelShape cached = CACHE.get(state);
        if (cached != null) return cached;
        VoxelShape out = null;
        for (Entry e : list) {
            if (!matches(state, e.cond)) continue;
            out = out == null ? e.shape : VoxelShapes.union(out, e.shape);
        }
        if (out != null) CACHE.put(state, out);
        return out;
    }

    /**
     * ★碰撞覆盖通道（C139）★ 返回 {@code null} = **本表没管这个 id/状态**（调用方回落 {@code boxes.json} / 自算几何）；
     * 返回空 shape = 该状态碰撞**确实为空**（表里显式写了 {@code []}）。
     * 这两者必须区分，否则「未覆盖」会被当成「无碰撞」。
     */
    public static VoxelShape queryCollision(String id, BlockState state) {
        if (id == null) return null;
        Map<String, List<Entry>> d = collision();
        if (d == null) return null;
        List<Entry> list = d.get(id);
        if (list == null) return null;
        VoxelShape cached = COLL_CACHE.get(state);
        if (cached != null) return cached;
        VoxelShape out = null;
        for (Entry e : list) {
            if (!matches(state, e.cond)) continue;
            out = out == null ? e.shape : VoxelShapes.union(out, e.shape);
        }
        if (out != null) COLL_CACHE.put(state, out);
        return out;
    }

    // ---- 给 dynShape 之类的旧接口留的兼容方法（未使用） ----
    static VoxelShape noop(BlockState s, BlockView v, BlockPos p) { return null; }
}