package com.akira.blockjs;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * `/blockjs probe` —— 对着方块执行，聊天栏打印 id + 全部属性 + 命中的 boxes 键 + meta 标记。
 *
 * 2026-10-29 C 队列 C12：这条能砍掉一半沟通成本（Aki 报缺陷时只要贴一行输出）。
 * 只读，不改世界。
 */
public final class ProbeCommand {

    private ProbeCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("blockjs")
                        .then(CommandManager.literal("probe")
                                .executes(ctx -> probe(ctx.getSource(), null))
                                .then(CommandManager.argument("pos", net.minecraft.command.argument.BlockPosArgumentType.blockPos())
                                        .executes(ctx -> probe(ctx.getSource(),
                                                net.minecraft.command.argument.BlockPosArgumentType.getBlockPos(ctx, "pos")))))));
    }

    private static int probe(ServerCommandSource src, BlockPos explicit) {
        ServerPlayerEntity player = null;
        try {
            player = src.getPlayerOrThrow();
        } catch (Exception ignored) {
        }
        World world = player != null ? player.getWorld()
                : (src.getWorld() != null ? src.getWorld() : null);
        if (world == null) {
            src.sendError(Text.literal("没有世界上下文（控制台执行请带坐标：/blockjs probe <x> <y> <z>）"));
            return 0;
        }
        BlockPos pos;
        if (explicit != null) {
            pos = explicit;
        } else if (player != null) {
            HitResult hit = player.raycast(8.0D, 0.0F, false);
            pos = (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK)
                    ? bhr.getBlockPos() : player.getBlockPos().down();
        } else {
            src.sendError(Text.literal("控制台执行请带坐标：/blockjs probe <x> <y> <z>"));
            return 0;
        }
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        String sid = id.toString();

        List<String> lines = new ArrayList<>();
        lines.add("§e[probe] §f" + sid + " §7@ " + pos.getX() + "/" + pos.getY() + "/" + pos.getZ());

        // 1) 全部属性
        StringBuilder props = new StringBuilder();
        for (Property<?> p : state.getProperties()) {
            if (props.length() > 0) props.append(',');
            props.append(p.getName()).append('=').append(valueOf(state, p));
        }
        lines.add("§7属性 §f" + (props.length() == 0 ? "(无)" : props.toString()));

        // 2) 状态串（可直接 /setblock 复制）
        lines.add("§7状态串 §f" + state.toString());

        // 3) 碰撞 / 外形（boxes 命中的就是这里）
        VoxelShape shape = state.getCollisionShape(world, pos);
        List<Box> boxes = shape.isEmpty() ? List.of() : shape.getBoundingBoxes();
        StringBuilder bb = new StringBuilder();
        for (Box b : boxes) {
            if (bb.length() > 0) bb.append(" | ");
            bb.append(fmt(b.minX)).append(',').append(fmt(b.minY)).append(',').append(fmt(b.minZ))
              .append(" -> ").append(fmt(b.maxX)).append(',').append(fmt(b.maxY)).append(',').append(fmt(b.maxZ));
        }
        lines.add("§7碰撞 §f" + (boxes.isEmpty() ? "(空 = 可穿过)" : boxes.size() + " 盒: " + bb));

        VoxelShape outline = state.getOutlineShape(world, pos);
        lines.add("§7外形 §f" + (outline.isEmpty() ? "(空)" : outline.getBoundingBoxes().size() + " 盒"));

        // 4) meta 标记
        BlockMeta.Meta m = BlockMeta.of(sid);
        if (m == null) {
            lines.add("§7meta §c(无条目)");
        } else {
            lines.add("§7meta §fsnd=" + m.sound() + " render=" + m.render()
                    + " cull=" + m.cull() + " flip=" + m.flip() + " occ=" + m.occ()
                    + " fam=" + m.fam() + " red=" + m.red() + " nocull=" + m.nocull()
                    + " nocol=" + m.nocol() + " nsame=" + m.nsame() + " fc=" + m.fullCollision());
        }

        // 5) 注册类型（普通 Block / ScriptBlock 子类）
        lines.add("§7类 §f" + block.getClass().getName());

        for (String l : lines) {
            src.sendFeedback(() -> Text.literal(l), false);
        }
        return 1;
    }

    private static <T extends Comparable<T>> String valueOf(BlockState state, Property<T> p) {
        return p.name(state.get(p));
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.valueOf((int) Math.rint(v));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
