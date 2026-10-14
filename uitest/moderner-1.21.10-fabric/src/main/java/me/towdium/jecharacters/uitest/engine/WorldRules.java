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

/**
 * Pins the world so two runs of the same scenario see the same thing.
 *
 * <p>Every rule is applied through a suppressed-output command on the integrated server thread: the
 * commands keep the pinned state visible to anything that later reads it (a scenario may assert on
 * the time of day), and suppression keeps the test client's log readable.
 *
 * <p>Entities are removed directly rather than through {@code kill @e[type=!player]}, because a
 * selector needs a source position and this code has no reason to invent one.
 */
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

    /** Applies every pin. Runs on the integrated server thread. */
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

    /** Discards every entity in the overworld that is not a player. */
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
