package me.towdium.jecharacters.uitest.engine;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;

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

    // 1.16.5 ships log4j2 and no slf4j (slf4j-api entered the game with 1.17), so this
    // line logs through Minecraft's own stack — the same substitution uitest/1.12.2-forge
    // makes. Measured on the resolved compileClasspath; see build.gradle.
    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private static final List<String> commands = Collections.unmodifiableList(Arrays.asList("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule doFireTick false", "gamerule doTraderSpawning false", "gamerule doPatrolSpawning false", "gamerule randomTickSpeed 0", "gamerule announceAdvancements false", "gamerule doImmediateRespawn true", "gamerule sendCommandFeedback false", "time set noon", "weather clear", "difficulty peaceful"));

    private WorldRules() {}

    /** Applies every pin. Runs on the integrated server thread. */
    public static void pin(MinecraftServer server) {
        ServerLevel level = server.overworld();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4)
                .withLevel(level).withPosition(Vec3.atCenterOf(BlockPos.ZERO)).withRotation(new Vec2(0f, 0f));
        for (String command : commands) {
            // Port note (1.21.1 → 1.16.5): performPrefixedCommand is 1.19.1+; the strip it
            // does internally is spelled out for this version's performCommand.
            server.getCommands().performCommand(
                source,
                command.startsWith("/") ? command.substring(1) : command
            );
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
        // Port note (1.18.2 → 1.16.5): this version's Entity.remove() is the discard() of 1.17+.
        doomed.forEach(Entity::remove);
        return doomed.size();
    }
}
