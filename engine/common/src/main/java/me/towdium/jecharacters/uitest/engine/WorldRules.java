package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.platform.ServerPlatform;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WorldRules {

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

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

    public static void pin(MinecraftServer server) {
        ServerPlatform platform = ServerPlatform.current();
        Object level = platform.overworld(server);
        Object source = platform.worldRuleSource(server, level);
        for (String command : commands) {
            platform.perform(server, source, command);
        }
        int removed = killNonPlayers(server);
        log.info("pinned {} world rules on {}", commands.size(), platform.dimensionId(level));
        if (removed > 0) log.info("removed {} non-player entities", removed);
    }

    public static int killNonPlayers(MinecraftServer server) {
        ServerPlatform platform = ServerPlatform.current();
        List<Object> doomed = new ArrayList<>();
        for (Object entity : platform.nonPlayerEntities(server)) {
            doomed.add(entity);
        }
        for (Object entity : doomed) {
            platform.discard(entity);
        }
        return doomed.size();
    }
}
