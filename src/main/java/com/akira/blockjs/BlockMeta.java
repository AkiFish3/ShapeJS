package com.akira.blockjs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.block.BlockBuilder;
import net.minecraft.sound.BlockSoundGroup;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 方块附属属性表（音效 / 渲染层 / 同种剔面），按方块 id 查。
 *
 * <p>为什么要从脚本搬到 Java：{@code b.sound('wood')} / {@code b.sameCull()} 是 builder <b>子类</b>上的方法，
 * Rhino 调用后实测<b>效果不体现</b>（音效仍为默认 STONE、玻璃相邻面照样画）。改成按 id 查表、
 * 由 {@code createObject()} 直接应用，绕开脚本层的方法解析。
 *
 * <p>数据由 `_gen_meta.py` 从各批次的 SOUND / RENDER 表合并，落在 jar 内
 * {@code /assets/blockjs/meta.json}：{@code {"cocricot:xxx": {"snd":"wood","rnd":"cutout"}}}。
 */
public final class BlockMeta {

    private BlockMeta() {}

    /** @param sound 音效短名（见 {@link SoundPresets}）· @param render cutout / translucent / null
     *  @param cull  同种方块相邻剔面（只对满立方开，非满方块剔面会删掉可见几何）
     *  @param flip  放置时 facing 取反（coc 天窗的模型基准朝向与 vanilla 相反）
     *  @param occ   是否遮挡邻居（= 满立方且不透明；false 时调 notSolid()）
     *  @param fam   连接族（NSEW 互连的"同族不同变体"，如 garland_black 与 garland_pop）。
     *               null / 缺省 = 只连自己（与改动前一致）。
     *  @param red   活板门是否必须红石才能开（金属材质）；木/石为 false = 可手动开
     *  @param nocull 完全不做面剔除（半透明 / 大量镂空的摆件：包围盒远大于真实几何，
     *               FaceCull 的 isSideCovered 会误判 ⇒ 同款并排时各缺一面）
     *  @param nocol 没有碰撞体积（只保留轮廓，仍能选中/破坏）：火、垂花、冰淇淋这类装饰
     *  @param nsame NSEW 只认同款方块（不粘墙/不粘别的家具）：地毯、餐桌这类
     *               （Aki 2026-10-28：圆形地毯与餐桌腿会互相"连接"，长出侧边模型）
     *  @param fc    碰撞强制整格、轮廓保留原几何（斜向玻璃板 `_pane_slant*`，Aki A4） */
    public record Meta(String sound, String render, boolean cull, boolean flip, boolean occ, String fam,
                       boolean red, boolean nocull, boolean nocol, boolean nsame, boolean fullCollision) {}

    private static Map<String, Meta> DATA;
    private static boolean failed = false;

    private static synchronized Map<String, Meta> data() {
        if (DATA != null || failed) return DATA;
        DATA = new HashMap<>();
        try (InputStreamReader r = new InputStreamReader(
                BlockMeta.class.getResourceAsStream("/assets/blockjs/meta.json"), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                JsonElement s = o.get("snd"), rn = o.get("rnd");
                JsonElement cu = o.get("cull"), fl = o.get("flip"), oc = o.get("occ");
                JsonElement fa = o.get("fam"), rd = o.get("red");
                JsonElement nc = o.get("nc"), ncl = o.get("nocol"), ns = o.get("ns"), fc = o.get("fc");
                DATA.put(e.getKey(), new Meta(
                        s == null || s.isJsonNull() ? null : s.getAsString(),
                        rn == null || rn.isJsonNull() ? null : rn.getAsString(),
                        cu != null && !cu.isJsonNull() && cu.getAsInt() != 0,
                        fl != null && !fl.isJsonNull() && fl.getAsInt() != 0,
                        oc == null || oc.isJsonNull() || oc.getAsInt() != 0,
                        fa == null || fa.isJsonNull() ? null : fa.getAsString(),
                        rd != null && !rd.isJsonNull() && rd.getAsInt() != 0,
                        nc != null && !nc.isJsonNull() && nc.getAsInt() != 0,
                        ncl != null && !ncl.isJsonNull() && ncl.getAsInt() != 0,
                        ns != null && !ns.isJsonNull() && ns.getAsInt() != 0,
                        fc != null && !fc.isJsonNull() && fc.getAsInt() != 0));
            }
            DynamicShapes.LOG.info("[BlockJS] 附属属性表已加载 {} 个方块", DATA.size());
        } catch (Throwable t) {
            failed = true; DATA = null;
            DynamicShapes.LOG.info("[BlockJS] 附属属性表加载失败: " + t);
        }
        return DATA;
    }

    /** 放置时是否要把 facing 取反 */
    public static boolean flip(String id) {
        Meta m = of(id);
        return m != null && m.flip();
    }

    /** 活板门是否必须红石才能开（金属材质） */
    public static boolean needsRedstone(String id) {
        Meta m = of(id);
        return m != null && m.red();
    }

    /** 连接族（NSEW 互连用）；没配返回 null */
    public static String family(String id) {
        Meta m = of(id);
        return m == null ? null : m.fam();
    }

    public static Meta of(String id) {
        if (id == null) return null;
        Map<String, Meta> d = data();
        return d == null ? null : d.get(id);
    }

    /**
     * 在 builder 的 {@code createObject()} 开头调用：按 id 应用渲染层 + 音效。
     * 幂等——脚本里若也调过 {@code defaultCutout()} / {@code sound()}，这里覆盖成同一值。
     */
    public static void apply(BlockBuilder b, String id) {
        Meta m = of(id);
        if (m == null) return;
        // 非「满立方 + 不透明」⇒ 不遮挡邻居（非满方块 / 玻璃 / 天窗）
        if (!m.occ()) b.notSolid();
        if (m.render() != null) {
            if ("translucent".equals(m.render())) b.defaultTranslucent();
            else b.defaultCutout();
        }
        if (m.sound() != null) {
            BlockSoundGroup g = SoundPresets.of(m.sound());
            if (g != null) b.soundType(g);
            // C10（Aki A9）：全部空手可挖 —— 工具只是加速（见 minecraft:mineable/* 标签），
            // 不是"必须正确工具才掉落"的门槛。显式写 false，别依赖 KubeJS 默认值。
            b.requiresTool(false);
            b.hardness(SoundPresets.hardnessOf(m.sound()));
        }
    }
}
