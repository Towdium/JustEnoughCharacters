package me.towdium.jecharacters.uitest.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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
    public void teleport(Object player, Object level, double x, double y, double z) {
        ServerPlayer serverPlayer = (ServerPlayer) player;
        serverPlayer.teleportTo((ServerLevel) level, x, y, z, serverPlayer.getYRot(), serverPlayer.getXRot());
    }

    @Override
    public Object itemByName(String id) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
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
        ((MinecraftServer) server).getCommands()
                .performPrefixedCommand((CommandSourceStack) source, command);
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
