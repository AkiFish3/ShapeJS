package com.akira.blockjs;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 形状锤：手持时左右键切换方块属性（mtshape / type / variant / click / shape / position …）。
 *
 * <ul>
 *   <li>右键：切下一个取值</li>
 *   <li>Shift + 右键：切上一个取值</li>
 *   <li>左键：切「另一个」属性（按优先级取第二个可切的）</li>
 * </ul>
 */
public class ShapeHammerItem extends Item {

    /** 可切属性的优先级：越靠前越优先 */
    private static final List<String> PRIORITY = List.of(
            "mtshape", "variant", "mtype", "click", "shift", "type", "shape", "position", "layers", "corner", "sofa",
            "vertical");

    /**
     * ★只能切这些属性★（Aki 2026-10-28：其他属性切了"看起来很怪"）。
     *
     * <p>被排除的：`facing`（整块转向）、`half`（半砖跳上跳下）、`axis`、`waterlogged`（看不见）、
     * `lighting` / `open` / `switch`（这些走右键开关）、`fence` / `north|south|east|west`（连接态）。
     */
    private static final java.util.Set<String> ALLOWED = java.util.Set.of(
            "click", "mtshape", "variant", "mtype", "shift", "type", "shape",
            "position", "layers", "corner", "sofa", "vertical");

    public ShapeHammerItem(Settings settings) {
        super(settings);
    }

    private static List<Property<?>> cycleable(BlockState state) {
        List<Property<?>> list = new ArrayList<>();
        for (Property<?> p : state.getProperties()) {
            if (p.getValues().size() > 1 && ALLOWED.contains(p.getName())) list.add(p);
        }
        list.sort(Comparator.comparingInt(p -> {
            int i = PRIORITY.indexOf(p.getName());
            return i < 0 ? 999 : i;
        }));
        return list;
    }

    /** 切换第 index 个可切属性；backwards=true 时反向 */
    public static boolean cycle(World world, BlockPos pos, PlayerEntity player, int index, boolean backwards) {
        BlockState state = world.getBlockState(pos);
        List<Property<?>> props = cycleable(state);
        if (props.isEmpty()) return false;
        // 左键要求「第二个可切属性」；只有一个属性时不要退化成和右键一样
        if (index >= props.size()) return false;
        Property<?> prop = props.get(index);
        return cycleProperty(world, pos, state, prop, backwards);
    }

    /**
     * 手持方块物品 + Shift + 右键（对空）= 切「下一个形态」，不放置、不消耗。
     *
     * <p>形态写在 stack 的 {@code BlockStateTag} 里 —— 这是原版 1.20.1 的正规入口：
     * {@code BlockItem#place} 内部会把 tag 逐属性 {@code Property#parse} 后套到放置状态上，
     * 并且先用 flags=2 预置一次（字节码实证，`/give ... {BlockStateTag:{...}}` 走的是同一条路）。
     *
     * <p>为什么不在 JS 里记自定义 NBT：那样得在 {@code BlockEvents.placed} 里补写，
     * 而 placed 事件在放置完成之后，客户端会先闪一帧默认形态；BlockStateTag 则是「放的那一刻就是对的」。
     *
     * @return true = 确实切了（调用方据此吞掉本次右键）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean cycleStack(net.minecraft.item.ItemStack stack, boolean backwards) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof net.minecraft.item.BlockItem bi)) return false;
        // 只管搬运的脚本方块：原版方块（如土块）不该被这套规则动
        if (!(bi.getBlock() instanceof ScriptBlock)) return false;
        BlockState base = bi.getBlock().getDefaultState();
        List<Property<?>> props = cycleable(base);
        if (props.isEmpty()) return false;
        Property prop = props.get(0);
        List values = new ArrayList(prop.getValues());
        if (values.size() < 2) return false;
        // 当前值：优先读已有 tag（可能已经切过几轮），读不到就落回默认态
        Comparable cur = (Comparable) base.get(prop);
        net.minecraft.nbt.NbtCompound old = stack.getSubNbt("BlockStateTag");
        if (old != null && old.contains(prop.getName(), net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            String s = old.getString(prop.getName());
            for (Object v : values) {
                if (prop.name((Comparable) v).equals(s)) { cur = (Comparable) v; break; }
            }
        }
        int i = values.indexOf(cur);
        int next = i < 0 ? 0
                : (backwards ? (i - 1 + values.size()) % values.size() : (i + 1) % values.size());
        // ★一律写成字符串★：原版是 Property#parse(String)，int / bool / enum 三种都能吃
        stack.getOrCreateSubNbt("BlockStateTag")
                .putString(prop.getName(), prop.name((Comparable) values.get(next)));
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> boolean cycleProperty(World world, BlockPos pos, BlockState state,
                                                                  Property<T> prop, boolean backwards) {
        List<T> values = new ArrayList<>(prop.getValues());
        T cur = state.get(prop);
        int i = values.indexOf(cur);
        int next = backwards ? (i - 1 + values.size()) % values.size() : (i + 1) % values.size();
        world.setBlockState(pos, state.with(prop, values.get(next)), 3);
        return true;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        PlayerEntity player = ctx.getPlayer();
        if (player == null) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        boolean ok = cycle(world, pos, player, 0, player.isSneaking());
        return ok ? ActionResult.SUCCESS : ActionResult.PASS;
    }
}
