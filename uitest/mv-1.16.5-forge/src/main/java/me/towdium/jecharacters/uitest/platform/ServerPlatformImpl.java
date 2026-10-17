package me.towdium.jecharacters.uitest.platform;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.server.ServerWorld;

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
        List<ServerPlayerEntity> players = ((MinecraftServer) server).getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    @Override
    public String shortPos(Object pos) {
        return ((BlockPos) pos).toShortString();
    }

    @Override
    public void setBlock(Object level, Object pos, Object state) {
        ((ServerWorld) level).setBlock((BlockPos) pos, (BlockState) state, 3);
    }

    @Override
    public void fill(Object level, Object from, Object to, Object state) {
        BlockPos start = (BlockPos) from;
        BlockPos end = (BlockPos) to;
        BlockState blockState = (BlockState) state;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int y = start.getY(); y <= end.getY(); y++) {
                for (int z = start.getZ(); z <= end.getZ(); z++) {
                    ((ServerWorld) level).setBlock(cursor.set(x, y, z), blockState, 3);
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
        return ((ServerWorld) level).getBlockEntity((BlockPos) pos);
    }

    @Override
    public void acceptBlockEntity(Object action, Object blockEntity) {
        ((Consumer<TileEntity>) action).accept((TileEntity) blockEntity);
    }

    @Override
    public void teleport(Object player, Object level, double x, double y, double z) {
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        serverPlayer.teleportTo((ServerWorld) level, x, y, z, serverPlayer.yRot, serverPlayer.xRot);
    }

    @Override
    public void look(Object player, Object level, float yaw, float pitch) {
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        serverPlayer.teleportTo((ServerWorld) level, serverPlayer.getX(), serverPlayer.getY(),
                serverPlayer.getZ(), yaw, pitch);
    }

    @Override
    public Object itemByName(String id) {
        Item item = Registry.ITEM.get(new ResourceLocation(id));
        return item == Items.AIR ? null : item;
    }

    @Override
    public void addToInventory(Object player, Object item, int count) {
        ((ServerPlayerEntity) player).inventory.add(new ItemStack((Item) item, count));
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
                .withLevel((ServerWorld) level)
                .withPosition(Vector3d.atCenterOf(BlockPos.ZERO))
                .withRotation(new Vector2f(0f, 0f));
    }

    @Override
    public void perform(Object server, Object source, String command) {
        ((MinecraftServer) server).getCommands().performCommand((CommandSource) source, command);
    }

    @Override
    public String dimensionId(Object level) {
        return ((ServerWorld) level).dimension().location().toString();
    }

    @Override
    public Iterable<Object> nonPlayerEntities(Object server) {
        List<Object> doomed = new ArrayList<>();
        for (Entity entity : ((MinecraftServer) server).overworld().getAllEntities()) {
            if (!(entity instanceof PlayerEntity)) {
                doomed.add(entity);
            }
        }
        return doomed;
    }

    @Override
    public void discard(Object entity) {
        ((Entity) entity).remove();
    }
}
