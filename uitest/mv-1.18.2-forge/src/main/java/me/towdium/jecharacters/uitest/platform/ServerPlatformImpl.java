package me.towdium.jecharacters.uitest.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ServerPlatformImpl implements ServerPlatform {

    @Override
    public Object overworld(Object server) {
        return ((MinecraftServer) server).overworld();
    }

    @Override
    public Object localPlayer(Object server) {
        List<ServerPlayer> players = ((MinecraftServer) server).getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    @Override
    public String shortPos(Object pos) {
        return ((BlockPos) pos).toShortString();
    }

    @Override
    public void setBlock(Object level, Object pos, Object state) {
        ((ServerLevel) level).setBlock((BlockPos) pos, (BlockState) state, 3);
    }

    @Override
    public void fill(Object level, Object from, Object to, Object state) {
        BlockPos start = (BlockPos) from;
        BlockPos end = (BlockPos) to;
        BlockState blockState = (BlockState) state;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    ((ServerLevel) level).setBlock(cursor.set(x, y, z), blockState, 3);
                }
            }
        }
    }

    @Override
    public Object airState() {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public Object blockEntity(Object level, Object pos) {
        return ((ServerLevel) level).getBlockEntity((BlockPos) pos);
    }

    @Override
    public void acceptBlockEntity(Object action, Object blockEntity) {
        ((Consumer<BlockEntity>) action).accept((BlockEntity) blockEntity);
    }

    @Override
    public void teleport(Object player, Object level, double x, double y, double z) {
        ServerPlayer serverPlayer = (ServerPlayer) player;
        serverPlayer.teleportTo((ServerLevel) level, x, y, z, serverPlayer.getYRot(), serverPlayer.getXRot());
    }

    @Override
    public void look(Object player, Object level, float yaw, float pitch) {
        ServerPlayer serverPlayer = (ServerPlayer) player;
        serverPlayer.teleportTo((ServerLevel) level, serverPlayer.getX(), serverPlayer.getY(),
                serverPlayer.getZ(), yaw, pitch);
    }

    @Override
    public Object itemByName(String id) {
        Item item = Registry.ITEM.get(new ResourceLocation(id));
        return item == Items.AIR ? null : item;
    }

    @Override
    public void addToInventory(Object player, Object item, int count) {
        ((ServerPlayer) player).getInventory().add(new ItemStack((Item) item, count));
    }

    @Override
    public String canonicalItemId(String id) {
        return new ResourceLocation(id).toString();
    }

    @Override
    public Object commandSource(Object server) {
        return ((MinecraftServer) server).createCommandSourceStack().withSuppressedOutput().withPermission(4);
    }

    @Override
    public Object worldRuleSource(Object server, Object level) {
        return ((MinecraftServer) server).createCommandSourceStack().withSuppressedOutput().withPermission(4)
                .withLevel((ServerLevel) level)
                .withPosition(Vec3.atCenterOf(BlockPos.ZERO))
                .withRotation(new Vec2(0f, 0f));
    }

    @Override
    public void perform(Object server, Object source, String command) {
        ((MinecraftServer) server).getCommands().performCommand((CommandSourceStack) source, command);
    }

    @Override
    public String dimensionId(Object level) {
        return ((ServerLevel) level).dimension().location().toString();
    }

    @Override
    public Iterable<Object> nonPlayerEntities(Object server) {
        List<Object> doomed = new ArrayList<>();
        for (Entity entity : ((MinecraftServer) server).overworld().getAllEntities()) {
            if (!(entity instanceof Player)) {
                doomed.add(entity);
            }
        }
        return doomed;
    }

    @Override
    public void discard(Object entity) {
        ((Entity) entity).discard();
    }
}
