package me.towdium.jecharacters.uitest.engine;

import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

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

    private static final List<String> commands = Collections.unmodifiableList(Arrays.asList(
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
    ));

    private WorldRules() {}

    /** Applies every pin. Runs on the integrated server thread. */
    public static void pin(MinecraftServer server) {
        ServerWorld level = server.overworld();
        CommandSource source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4)
                .withLevel(level).withPosition(Vector3d.atCenterOf(BlockPos.ZERO)).withRotation(new Vector2f(0f, 0f));
        for (String command : commands) {
            server.getCommands().performCommand(source, command);
        }
        int removed = killNonPlayers(server);
        log.info("pinned {} world rules on {}", commands.size(), level.dimension().location());
        if (removed > 0) log.info("removed {} non-player entities", removed);
    }

    /** Discards every entity in the overworld that is not a player. */
    public static int killNonPlayers(MinecraftServer server) {
        List<Entity> doomed = new ArrayList<>();
        for (Entity entity : server.overworld().getAllEntities()) {
            if (!(entity instanceof PlayerEntity)) {
                doomed.add(entity);
            }
        }
        doomed.forEach(Entity::remove);
        return doomed.size();
    }
}
