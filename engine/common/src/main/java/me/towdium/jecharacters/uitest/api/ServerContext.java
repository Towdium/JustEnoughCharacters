package me.towdium.jecharacters.uitest.api;

import me.towdium.jecharacters.uitest.platform.ServerPlatform;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class ServerContext {

    private final MinecraftServer server;
    private long stepSeq = Long.MIN_VALUE;
    private boolean submitted;
    private volatile boolean done;
    private volatile boolean answer;
    private volatile @Nullable Throwable failure;
    private long tickAnchor = -1L;

    public ServerContext(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer server() {
        return server;
    }

    public void beginStep(long sequence) {
        if (sequence == stepSeq) return;
        stepSeq = sequence;
        submitted = false;
        done = false;
        answer = false;
        failure = null;
        tickAnchor = -1L;
    }

    public Object overworld() {
        return platform().overworld(server);
    }

    public @Nullable Object localPlayer() {
        return platform().localPlayer(server);
    }

    public void run(TestContext context, String label, Runnable task) {
        submitOnce(() -> {
            task.run();
            return true;
        });
        if (!done) {
            context.repeat("server: " + label);
            return;
        }
        finish(label, true);
    }

    public void poll(TestContext context, String label, Supplier<Boolean> predicate) {
        submitOnce(predicate);
        if (!done) {
            context.repeat("server: " + label);
            return;
        }
        boolean satisfied = finish(label, false);
        if (!satisfied) {
            context.repeat("server: " + label);
        }
    }

    public void teleportPlayer(TestContext context, double x, double y, double z) {
        run(context, "teleportPlayer", () -> platform().teleport(requirePlayer(), overworld(), x, y, z));
    }

    public static String canonicalItemId(String item) {
        return platform().canonicalItemId(item);
    }

    public void giveItem(TestContext context, String item, int count) {
        run(context, "giveItem " + canonicalItemId(item), () -> {
            Object resolved = platform().itemByName(item);
            if (resolved == null) {
                throw new IllegalStateException("unknown item " + canonicalItemId(item));
            }
            platform().addToInventory(requirePlayer(), resolved, count);
        });
    }

    public void runCommand(TestContext context, String command) {
        run(context, "command " + command,
            () -> platform().perform(server, platform().commandSource(server), command));
    }

    public void serverTicks(TestContext context, int ticks) {
        if (tickAnchor < 0L) {
            tickAnchor = server.getTickCount();
        }
        long target = tickAnchor + ticks;
        poll(context, "serverTicks(" + ticks + ")", () -> server.getTickCount() >= target);
    }

    private Object requirePlayer() {
        Object player = localPlayer();
        if (player == null) {
            throw new IllegalStateException("the integrated server has no player yet");
        }
        return player;
    }

    private static ServerPlatform platform() {
        return ServerPlatform.current();
    }

    private void submitOnce(Supplier<Boolean> body) {
        if (submitted) return;
        submitted = true;
        server.execute(() -> {
            try {
                answer = body.get();
            } catch (Throwable thrown) {
                failure = thrown;
            } finally {
                done = true;
            }
        });
    }

    private boolean finish(String label, boolean once) {
        done = false;
        submitted = once;
        Throwable thrown = failure;
        failure = null;
        if (thrown != null) {
            throw new IllegalStateException("server task '" + label + "' failed", thrown);
        }
        return answer;
    }
}
