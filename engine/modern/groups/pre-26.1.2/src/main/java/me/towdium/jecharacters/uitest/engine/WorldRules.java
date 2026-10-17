package me.towdium.jecharacters.uitest.engine;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class WorldRules {

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    private static final List<String> commands = List.of(
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "gamerule doMobSpawning false",
        "gamerule doFireTick false",
        "gamerule doTraderSpawning false",
        "gamerule doPatrolSpawning false",
        "gamerule randomTickSpeed 0",
        "gamerule announceAdvancements false",
        "gamerule doImmediateRespawn true",
        "gamerule sendCommandFeedback false",
        "time set noon",
        "weather clear",
        "difficulty peaceful"
    );

    private WorldRules() {}

    public static void pin(MinecraftServer server) {
        ServerLevel level = server.overworld();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4)
                .withLevel(level).withPosition(Vec3.atCenterOf(BlockPos.ZERO)).withRotation(new Vec2(0f, 0f));
        for (String command : commands) {
            server.getCommands().performPrefixedCommand(source, command);
        }
        int removed = killNonPlayers(server);
        log.info("pinned {} world rules on {}", commands.size(), level.dimension().location());
        if (removed > 0) log.info("removed {} non-player entities", removed);
    }

    public static int killNonPlayers(MinecraftServer server) {
        List<Entity> doomed = new ArrayList<>();
        for (Entity entity : server.overworld().getAllEntities()) {
            if (!(entity instanceof Player)) {
                doomed.add(entity);
            }
        }
        doomed.forEach(Entity::discard);
        return doomed.size();
    }
}
