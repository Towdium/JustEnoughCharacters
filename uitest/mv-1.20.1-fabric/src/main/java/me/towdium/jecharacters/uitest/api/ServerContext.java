package me.towdium.jecharacters.uitest.api;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs world mutation on the integrated server thread and holds the step clock still until it has
 * happened.
 *
 * <p>A step body runs on the client, one attempt per frame. Submitting work to the server is
 * therefore not a call but a handshake: the first attempt of a step submits, every attempt after
 * that repeats the step until the server has run the task (or the step's wait budget expires). The
 * runner tells this context when a new step starts, so a task is submitted exactly once per step.
 */
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

    /** The integrated server this context drives. */
    public MinecraftServer server() {
        return server;
    }

    /** Tells the context which step is running; a different sequence resets the handshake. */
    public void beginStep(long sequence) {
        if (sequence == stepSeq) return;
        stepSeq = sequence;
        submitted = false;
        done = false;
        answer = false;
        failure = null;
        tickAnchor = -1L;
    }

    /** The overworld of the integrated server. */
    public ServerLevel overworld() {
        return server.overworld();
    }

    /** The local player, or {@code null} when the server has no player yet. */
    public @Nullable ServerPlayer localPlayer() {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    /** Submits a task once and repeats the step until it has run. */
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

    /** Submits a poll once per frame and repeats the step until the predicate holds. */
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

    /** Places one block. */
    public void setBlock(TestContext context, BlockPos pos, BlockState state) {
        run(context, "setBlock " + pos.toShortString(), () -> overworld().setBlock(pos, state, 3));
    }

    /** Fills an inclusive box with one block. */
    public void fill(TestContext context, BlockPos from, BlockPos to, BlockState state) {
        run(context, "fill " + from.toShortString() + ".." + to.toShortString(), () -> {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = from.getX(); x <= to.getX(); x++) {
                for (int y = from.getY(); y <= to.getY(); y++) {
                    for (int z = from.getZ(); z <= to.getZ(); z++) {
                        overworld().setBlock(cursor.set(x, y, z), state, 3);
                    }
                }
            }
        });
    }

    /** Replaces an inclusive box with air. */
    public void clearArea(TestContext context, BlockPos from, BlockPos to) {
        fill(context, from, to, Blocks.AIR.defaultBlockState());
    }

    /** Hands a block entity to an action, on the server thread. */
    public void withBlockEntity(TestContext context, BlockPos pos, Consumer<BlockEntity> action) {
        run(context, "withBlockEntity " + pos.toShortString(), () -> {
            BlockEntity blockEntity = overworld().getBlockEntity(pos);
            if (blockEntity == null) {
                throw new IllegalStateException("no block entity at " + pos.toShortString());
            }
            action.accept(blockEntity);
        });
    }

    /** Moves the local player, keeping the current aim. */
    public void teleportPlayer(TestContext context, double x, double y, double z) {
        run(context, "teleportPlayer", () -> {
            ServerPlayer player = requirePlayer();
            player.teleportTo(overworld(), x, y, z, player.getYRot(), player.getXRot());
        });
    }

    /** Aims the local player, keeping the current position. */
    public void yawPitch(TestContext context, float yaw, float pitch) {
        run(context, "yawPitch", () -> {
            ServerPlayer player = requirePlayer();
            player.teleportTo(overworld(), player.getX(), player.getY(), player.getZ(), yaw, pitch);
        });
    }

    /** Puts a stack of an item into the local player's inventory. */
    public void giveItem(TestContext context, ResourceLocation item, int count) {
        run(context, "giveItem " + item, () -> {
            Item resolved = BuiltInRegistries.ITEM.get(item);
            if (resolved == Items.AIR) {
                throw new IllegalStateException("unknown item " + item);
            }
            requirePlayer().getInventory().add(new ItemStack(resolved, count));
        });
    }

    /** Runs a server command with its output suppressed. */
    public void runCommand(TestContext context, String command) {
        run(context, "command " + command, () -> {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            server.getCommands().performPrefixedCommand(source, command);
        });
    }

    /** Holds the step clock until the server has ticked a number of times. */
    public void serverTicks(TestContext context, int ticks) {
        if (tickAnchor < 0L) {
            tickAnchor = server.getTickCount();
        }
        long target = tickAnchor + ticks;
        poll(context, "serverTicks(" + ticks + ")", () -> server.getTickCount() >= target);
    }

    private ServerPlayer requirePlayer() {
        ServerPlayer player = localPlayer();
        if (player == null) {
            throw new IllegalStateException("the integrated server has no player yet");
        }
        return player;
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
