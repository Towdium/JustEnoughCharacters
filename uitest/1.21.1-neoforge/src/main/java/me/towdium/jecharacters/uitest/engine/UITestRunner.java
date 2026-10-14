package me.towdium.jecharacters.uitest.engine;

import com.mojang.blaze3d.platform.Window;
import me.towdium.jecharacters.uitest.ScenarioRegistry;
import me.towdium.jecharacters.uitest.api.CapturePipeline;
import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.ErrorPolicy;
import me.towdium.jecharacters.uitest.api.InputDriver;
import me.towdium.jecharacters.uitest.api.RunConfig;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import me.towdium.jecharacters.uitest.api.ScenarioOptions;
import me.towdium.jecharacters.uitest.api.ServerContext;
import me.towdium.jecharacters.uitest.api.Session;
import me.towdium.jecharacters.uitest.api.Status;
import me.towdium.jecharacters.uitest.api.Step;
import me.towdium.jecharacters.uitest.api.TestContext;
import me.towdium.jecharacters.uitest.engine.report.Environment;
import me.towdium.jecharacters.uitest.engine.report.ReportWriter;
import me.towdium.jecharacters.uitest.engine.report.RunReport;
import me.towdium.jecharacters.uitest.engine.report.ScenarioReport;
import me.towdium.jecharacters.uitest.engine.report.StepReport;
import me.towdium.jecharacters.uitest.match.MatchRecorder;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Drives one harness run: one phase transition and one step per rendered frame.
 *
 * <p>The clock is the frame itself. Nothing here sleeps and nothing blocks; world creation pumps the
 * render loop internally, so the whole body sits behind a re-entrancy guard that turns those nested
 * frames into no-ops. A step that is not finished calls {@link TestContext#repeat(String)} and is
 * called again next frame, with no settle in between, until its wait budget runs out.
 *
 * <p>Nothing here may kill the client from inside a frame event: {@code Minecraft#stop} ends the
 * main loop, a shutdown hook carries the exit code out, and the {@code exiting} phase keeps frames
 * turning so that shutdown finds a live render thread.
 */
public final class UITestRunner {

    /** Where the run is in its lifecycle. At most one transition happens per frame. */
    enum Phase {
        waitGameReady, pinOptions, createWorld, awaitWorld, pinWorld, nextScenario, runScenario, finish, exiting
    }

    private static final Logger log = LoggerFactory.getLogger("jechtest-uitest");

    private static final int gameReadyBudgetMs = 180_000;

    private static final int worldBudgetMs = 180_000;

    private static final int worldPinBudgetMs = 60_000;

    private static final int reenterBudgetMs = 60_000;

    private static final int resizeSettleMs = 50;

    private static final int worldReadySettleMs = 100;

    private static final int shutdownCodeFailure = 1;

    private static final int watchdogExitCode = 3;

    private final Minecraft minecraft;

    private final RunConfig config;

    private final RunReport report;

    private final InputDriver driver;

    private final CapturePipeline pipeline;

    private final String runId;

    private final List<Queued> queue = new ArrayList<>();

    private final long startedAt = System.currentTimeMillis();

    private Phase phase = Phase.waitGameReady;

    private long phaseStartedAt = System.currentTimeMillis();

    private volatile long lastFrameAt = System.currentTimeMillis();

    private @Nullable ServerContext serverContext;

    private @Nullable ScenarioRun current;

    /** The restart schedule the selection resolved to: tiers in run order, cheapest first. */
    private final List<SessionGroup> sessionPlan = new ArrayList<>();

    private int groupCursor;

    private int memberCursor;

    private boolean groupSessionStarted;

    private @Nullable Reenter reenter;

    /** The save the current world lives in; a fresh group gets one of its own. */
    private @Nullable String worldName;

    private int freshWorlds;

    private long stepSequence;

    private long settleUntil;

    private boolean inStep;

    private volatile boolean shuttingDown;

    private @Nullable String fatalMessage;

    private boolean worldRulesSubmitted;

    private volatile boolean worldRulesDone;

    private volatile @Nullable Throwable worldRulesFailure;

    private boolean needsWorld;

    private Status runStatus = Status.pass;

    private UITestRunner(Minecraft minecraft, RunConfig config) {
        this.minecraft = minecraft;
        this.config = config;
        this.runId = ProcessHandle.current().pid() + "_" + Integer.toHexString(System.identityHashCode(config));
        this.driver = HarnessFactory.driver(config.inputMode());
        this.pipeline = HarnessFactory.pipeline(config);
        this.report = new RunReport(
            runId,
            config.selection(),
            environment(minecraft, config),
            Instant.now().toString()
        );
        this.pipeline.setSink(this::onCaptureWritten);
    }

    /** The runner for this client, created on the first frame of a requested run. */
    public static UITestRunner bootstrap() {
        RunConfig config = RunConfig.fromSystemProperties();
        UITestRunner runner = new UITestRunner(Minecraft.getInstance(), config);
        log.info(
            "jechtest-uitest run {} selecting '{}' with {} input, {} s watchdog, out={}, headless={}",
            runner.runId,
            config.selection(),
            config.inputMode(),
            config.watchdogSeconds(),
            config.outDir(),
            config.headless()
        );
        return runner;
    }

    /**
     * Starts a keep-open run from inside the game, against the world the player already has loaded.
     *
     * <p>Nothing here creates a world or touches the window: the prologue only scans scenarios,
     * installs the driver and jumps straight to the scenario queue. The returned message is what
     * the command reports back to the player, so a refusal explains itself instead of leaving a
     * half-started run behind.
     *
     * @return a message describing what happened; the run is installed when it names a scenario
     *         count
     */
    public static String runInteractive(String selection) {
        UITestRunner active = UITestBootstrap.runner();
        if (active != null && !active.shuttingDown()) {
            return "a UI test run is already in progress";
        }
        UITestRunner runner = new UITestRunner(Minecraft.getInstance(), RunConfig.interactive(selection));
        String refusal = runner.interactivePrologue();
        if (refusal != null) {
            return refusal;
        }
        UITestBootstrap.install(runner);
        runner.startWatchdog();
        return "running " + runner.queue.size() + " scenario(s): " + String.join(", ", runner.queueNames())
                + " (report in " + runner.config.outDir() + ")";
    }

    /**
     * The interactive prologue: select scenarios, check the world can serve them, install the
     * driver, and enter the queue. No window pinning, no world creation.
     *
     * @return the reason the run cannot start, or {@code null} once it has
     */
    private @Nullable String interactivePrologue() {
        queue.clear();
        List<ScenarioRegistry.Entry> registered = ScenarioRegistry.scan();
        for (ScenarioRegistry.Entry entry : registered) {
            if (config.selects(entry.name(), entry.tags(), entry.group(), entry.type())) {
                queue.add(Queued.of(entry));
            }
        }
        if (queue.isEmpty()) {
            return "selection '" + config.selection() + "' matched no scenarios of " + registered.size();
        }
        needsWorld = queue.stream().anyMatch(Queued::requiresWorld);
        if (needsWorld && (minecraft.level == null || minecraft.player == null)) {
            return "selection '" + config.selection() + "' needs a loaded world; join one first";
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            serverContext = new ServerContext(server);
        }
        startRecording();
        log.info("interactive run {} starting {} scenario(s)", runId, queue.size());
        driver.install();
        enter(Phase.nextScenario);
        settle(resizeSettleMs);
        return null;
    }

    /** The identifier this run's world and report are named after. */
    public String runId() {
        return runId;
    }

    /** Whether the run is over and the client is only being kept alive to be looked at. */
    public boolean shuttingDown() {
        return shuttingDown;
    }

    /** The names of the queued scenarios, in run order. */
    public List<String> queueNames() {
        return queue.stream().map(Queued::name).toList();
    }

    /** One frame of the run. */
    public void onFrame() {
        lastFrameAt = now();
        if (minecraft.getOverlay() == null) {
            // the embodiment's own per-frame pump, before anything else this frame does: a scripted
            // sweep turns once per rendered frame, no matter where the step clock happens to be
            driver.playerInput().pumpFrame();
        }
        if (inStep) return;
        if (minecraft.getOverlay() != null) return;
        inStep = true;
        try {
            pump();
        } catch (Throwable thrown) {
            log.error("jechtest frame pump failed", thrown);
            fatal("frame pump: " + describe(thrown));
        } finally {
            inStep = false;
        }
    }

    /** One tick of the game's own clock, which the run's sweeps are measured in. */
    public void onTick() {
        driver.playerInput().pumpTick();
    }

    /** Starts the daemon thread that turns a stalled client into a report instead of a hang. */
    public void startWatchdog() {
        Thread watchdog = new Thread(this::watch, "jechtest-uitest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // region phase pump

    private void pump() {
        if (!pipeline.isEmpty()) {
            pipeline.serviceOne();
            return;
        }
        if (settleUntil > 0L) {
            if (now() < settleUntil) return;
            settleUntil = 0L;
        }
        switch (phase) {
            case waitGameReady -> waitGameReady();
            case pinOptions -> pinOptions();
            case createWorld -> createWorld();
            case awaitWorld -> awaitWorld();
            case pinWorld -> pinWorld();
            case nextScenario -> nextScenario();
            case runScenario -> runScenario();
            case finish -> finish();
            case exiting -> {}
        }
    }

    private void waitGameReady() {
        Screen screen = minecraft.screen;
        if (screen instanceof AccessibilityOnboardingScreen) {
            minecraft.options.onboardAccessibility = false;
            minecraft.options.save();
            minecraft.setScreen(new TitleScreen());
            return;
        }
        if (!(screen instanceof TitleScreen)) {
            if (phaseElapsed() > gameReadyBudgetMs) {
                fatal("the client never reached the title screen within " + gameReadyBudgetMs + " ms");
            }
            return;
        }
        enter(Phase.pinOptions);
    }

    private void pinOptions() {
        pinClientOptions();
        List<ScenarioRegistry.Entry> registered = ScenarioRegistry.scan();
        for (ScenarioRegistry.Entry entry : registered) {
            if (config.selects(entry.name(), entry.tags(), entry.group(), entry.type())) {
                queue.add(Queued.of(entry));
            }
        }
        log.info(
            "selection '{}' matched {} of {} registered scenarios",
            config.selection(),
            queue.size(),
            registered.size()
        );
        if (queue.isEmpty()) {
            fatal("selection '" + config.selection() + "' matched no scenarios");
            return;
        }
        needsWorld = queue.stream().anyMatch(Queued::requiresWorld);
        buildSessionPlan();
        if (needsWorld) {
            // the world the run creates up front IS the first group's session: nothing has run in it
            groupSessionStarted = true;
        }
        startRecording();
        driver.install();
        enter(needsWorld ? Phase.createWorld : Phase.nextScenario);
        settle(resizeSettleMs);
    }

    /**
     * Turns the match recorder on and reports any JSON scenario the scan could not read.
     *
     * <p>Recording is on for the whole of a run rather than per step, because a scenario's assertion
     * is about what happened <em>before</em> it ran; the buffer is cleared as each scenario starts so
     * one scenario's matches can never satisfy the next one's assertion.
     */
    private void startRecording() {
        MatchRecorder.setEnabled(true);
        for (String problem : ScenarioRegistry.registrationProblems()) {
            log.error("jechtest-uitest: a JSON scenario could not be read and will fail if selected: {}", problem);
        }
        log.info(MatchRecorder.stats());
    }

    /**
     * Turns the selection into the smallest restart schedule that satisfies every scenario in it.
     *
     * <p>Cheapest tier first, because a stronger session satisfies a weaker one: every {@code shared}
     * scenario runs in the world the run already has, then the {@code reenter} scenarios run in one
     * rejoined session, then each {@code fresh} scenario gets a world of its own. Within a tier the
     * registration order is kept, so a run's order is predictable from the registry.
     *
     * <p><b>Why one rejoin can serve a whole block of {@code reenter} scenarios.</b> The tier means
     * "not the previous session" — the client state a join resets has to be new, and the world has to
     * be the same one — and that is exactly what one leave-and-rejoin gives every scenario that runs
     * after it. The classification is what makes the batch honest: a scenario that needed a session
     * of its own *relative to its predecessors in the block* would have to say so, and the only tiers
     * that say so are {@code fresh} (a world of its own) — a scenario cannot currently demand "a
     * session nobody else has used". {@code fresh} groups therefore stay one scenario each, and
     * {@code shared} runs get no restart at all.
     *
     * <p>The group at the head of the schedule never pays a transition of its own: the world the run
     * creates (or the title screen a world-less run starts on) has had no scenario in it yet, so it
     * already is the session that group asked for.
     *
     */
    private void buildSessionPlan() {
        sessionPlan.clear();
        List<Queued> shared = new ArrayList<>();
        List<Queued> reenter = new ArrayList<>();
        List<Queued> fresh = new ArrayList<>();
        for (Queued queued : queue) {
            switch (queued.options().session()) {
                case reenter -> reenter.add(queued);
                case fresh -> fresh.add(queued);
                default -> shared.add(queued);
            }
        }
        if (!shared.isEmpty()) sessionPlan.add(new SessionGroup(Session.shared, List.copyOf(shared)));
        if (!reenter.isEmpty()) sessionPlan.add(new SessionGroup(Session.reenter, List.copyOf(reenter)));
        for (Queued queued : fresh) sessionPlan.add(new SessionGroup(Session.fresh, List.of(queued)));
        int rejoins = reenter.isEmpty() ? 0 : 1;
        report.sessionPlan(
            "shared[" + shared.size() + "] -> reenter[" + reenter.size() + "] -> fresh[" + fresh.size() + "]" + " = "
                    + rejoins + " rejoin(s), " + fresh.size() + " new world(s), 0 shared restarts; order: "
                    + sessionPlan.stream().map(group -> group.tier() + "(" + group.members().size() + ")")
                            .collect(Collectors.joining(" "))
        );
        log.info(
            "session plan: shared[{}] reenter[{}] fresh[{}] => {} rejoin(s), {} world creation(s)",
            shared.size(),
            reenter.size(),
            fresh.size(),
            rejoins,
            fresh.size()
        );
    }

    /** The plan as one line, for a report reader. */
    private String sessionPlanLine() {
        return report.sessionPlan();
    }

    private void createWorld() {
        if (worldName == null) {
            worldName = WorldBootstrap.worldName(runId);
        }
        // keep this run's own saves: the world it just left is still being flushed by its server
        WorldBootstrap.deleteStaleWorlds(minecraft.getLevelSource(), ownedWorlds());
        enter(Phase.awaitWorld);
        log.info("creating throwaway world {}", worldName);
        WorldBootstrap.createFreshLevel(minecraft, worldName);
    }

    private void awaitWorld() {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (minecraft.player == null || minecraft.level == null || server == null) {
            if (phaseElapsed() > worldBudgetMs) {
                fatal("the world never loaded within " + worldBudgetMs + " ms");
            }
            return;
        }
        serverContext = new ServerContext(server);
        enter(Phase.pinWorld);
    }

    private void pinWorld() {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            fatal("the integrated server went away while pinning world rules");
            return;
        }
        if (!worldRulesSubmitted) {
            worldRulesSubmitted = true;
            server.execute(() -> {
                try {
                    WorldRules.pin(server);
                } catch (Throwable thrown) {
                    worldRulesFailure = thrown;
                } finally {
                    worldRulesDone = true;
                }
            });
            return;
        }
        if (!worldRulesDone) {
            if (phaseElapsed() > worldPinBudgetMs) {
                fatal("pinning world rules did not finish within " + worldPinBudgetMs + " ms");
            }
            return;
        }
        Throwable failure = worldRulesFailure;
        if (failure != null) {
            fatal("pinning world rules failed: " + describe(failure));
            return;
        }
        minecraft.setScreen(null);
        enter(Phase.nextScenario);
        settle(worldReadySettleMs);
    }

    private void nextScenario() {
        if (reenter != null) {
            if (!advanceReenter()) return;
            reenter = null;
            settle(worldReadySettleMs);
            return;
        }
        if (groupCursor >= sessionPlan.size()) {
            enter(Phase.finish);
            return;
        }
        SessionGroup group = sessionPlan.get(groupCursor);
        if (!groupSessionStarted) {
            groupSessionStarted = true;
            if (!startGroupSession(group)) return;
        }
        if (memberCursor >= group.members().size()) {
            groupCursor++;
            memberCursor = 0;
            groupSessionStarted = false;
            return;
        }
        Queued queued = group.members().get(memberCursor++);
        ScenarioRun run = startScenario(queued);
        if (run == null) {
            enter(Phase.nextScenario);
            return;
        }
        current = run;
        enter(Phase.runScenario);
        settle(resizeSettleMs);
    }

    /**
     * Gives {@code group} the session it declared, and reports whether it is ready to run in it.
     *
     * <p>A {@code reenter} group starts a leave-and-rejoin, which the following frames carry through
     * {@link #advanceReenter}; a {@code fresh} group hands the work to the world-creation phases, which
     * return to {@code nextScenario} once the new world is pinned. The head of the schedule never gets
     * here at all — the run's own initial world is its session.
     */
    private boolean startGroupSession(SessionGroup group) {
        boolean head = groupCursor == 0;
        return switch (group.tier()) {
            case shared -> true;
            case reenter -> {
                if (head) {
                    yield true;
                }
                log.info("session: rejoining {} for {} scenario(s)", worldName, group.members().size());
                reenter = new Reenter(false);
                yield false;
            }
            case fresh -> {
                if (head) {
                    yield true;
                }
                prepareFreshWorld();
                if (minecraft.level != null) {
                    // a world is up: leave it before creating the next one, or two servers would own
                    // this client's saves at once
                    reenter = new Reenter(true);
                    yield false;
                }
                log.info("session: creating {} for {}", worldName, group.members().getFirst().name());
                enter(Phase.createWorld);
                yield false;
            }
        };
    }

    /** Names the next world of this run and re-arms the rule pinning for it. */
    private void prepareFreshWorld() {
        worldName = WorldBootstrap.worldName(runId + "_w" + (++freshWorlds));
        worldRulesSubmitted = false;
        worldRulesDone = false;
        worldRulesFailure = null;
    }

    /** The save directories this run owns, which the stale-world sweep must not touch. */
    private List<String> ownedWorlds() {
        List<String> owned = new ArrayList<>();
        owned.add(WorldBootstrap.worldName(runId));
        for (int index = 1; index <= Math.max(freshWorlds, 1); index++) {
            owned.add(WorldBootstrap.worldName(runId + "_w" + index));
        }
        return owned;
    }

    /**
     * One step of a leave-and-rejoin, one per frame: leave the world, wait for the client to be out of
     * it, reopen the same save, wait for it to load. The world is not re-pinned — its rules were pinned
     * into the save when it was created, and the point of the tier is that nothing about the world
     * changed.
     */
    private boolean advanceReenter() {
        Reenter state = reenter;
        if (state == null) return true;
        switch (state.step) {
            case 0 -> {
                log.info("session: leaving {}", worldName);
                // The clean leave, in the order that cannot deadlock: clear the screen first (a screen
                // pauses the integrated server, and a paused server never reaches the end of
                // Minecraft.disconnect's own wait loop — that wait is why this does not call it), tell
                // the server the client is leaving, then stop the server. Stopping a single-player
                // world's server IS leaving it: the client's connection closes with it and the client
                // tears its level down on the next tick, exactly as it would after a normal quit.
                minecraft.setScreen(null);
                ClientLevel level = minecraft.level;
                if (level != null) {
                    level.disconnect();
                }
                MinecraftServer server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.halt(false);
                }
                state.step = 1;
                state.startedAt = now();
                return false;
            }
            case 1 -> {
                if (minecraft.level == null && minecraft.player == null && minecraft.getSingleplayerServer() == null) {
                    state.step = 2;
                    return false;
                }
                if (now() - state.startedAt > reenterBudgetMs) {
                    fatal("the client never left the world within " + reenterBudgetMs + " ms");
                }
                return false;
            }
            case 2 -> {
                if (state.createFresh) {
                    // the old world is gone; the creation phases take it from here and come back to
                    // nextScenario once the new one is pinned
                    log.info("session: creating {} (no world was up)", worldName);
                    enter(Phase.createWorld);
                    return true;
                }
                String name = worldName == null ? WorldBootstrap.worldName(runId) : worldName;
                WorldBootstrap.rejoinLevel(minecraft, name);
                state.step = 3;
                state.startedAt = now();
                return false;
            }
            default -> {
                if (minecraft.level != null && minecraft.player != null) {
                    MinecraftServer server = minecraft.getSingleplayerServer();
                    if (server == null) return false;
                    serverContext = new ServerContext(server);
                    minecraft.setScreen(null);
                    log.info("session: rejoined {}", worldName);
                    return true;
                }
                if (now() - state.startedAt > worldBudgetMs) {
                    fatal("the rejoined world never loaded within " + worldBudgetMs + " ms");
                }
                return false;
            }
        }
    }

    private void runScenario() {
        ScenarioRun run = current;
        if (run == null) {
            enter(Phase.nextScenario);
            return;
        }
        long now = now();
        if (now - run.startedAt > run.scenarioTimeoutMs) {
            run.status = Status.worst(run.status, Status.error);
            run.note = "scenario budget of " + run.scenarioTimeoutMs + " ms exhausted";
            abortToTeardown(run);
        }
        Step step = run.currentStep();
        if (step == null) {
            completeScenario(run);
            return;
        }
        tickStep(run, step, now);
    }

    private void finish() {
        if (shuttingDown) {
            phase = Phase.exiting;
            return;
        }
        shuttingDown = true;
        driver.uninstall();
        MatchRecorder.setEnabled(false);
        log.info("jechtest-uitest released the input seams; still installed={}", driver.installed());
        Status status = fatalMessage == null ? runStatus : Status.worst(runStatus, Status.error);
        report.complete(status, Instant.now().toString(), now() - startedAt);
        writeReport();
        if (config.keepOpen()) {
            log.info("jechtest-uitest keeping the client open; the report is in {}", config.outDir());
            phase = Phase.exiting;
            return;
        }
        int code = report.failed() ? shutdownCodeFailure : 0;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Runtime.getRuntime().halt(code), "jechtest-uitest-exit")); // NOPMD
        phase = Phase.exiting;
        minecraft.stop();
    }

    // endregion

    // region scenarios and steps

    private @Nullable ScenarioRun startScenario(Queued queued) {
        if (minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        minecraft.setScreen(null);
        // the buffer is per scenario: an assertion is about the input this scenario just made, never
        // about a match an earlier scenario happened to leave behind
        MatchRecorder.clear();
        ScenarioOptions options = queued.options();
        minecraft.options.guiScale().set(options.guiScale(config.guiScale()));
        minecraft.options.hideGui = options.hideGui();
        minecraft.resizeDisplay();

        ScenarioBuilder builder = new ScenarioBuilder();
        try {
            queued.define().accept(builder);
        } catch (RuntimeException e) {
            ScenarioReport failed = report.addScenario(
                new ScenarioReport(queued.name(), queued.group(), queued.tags(), queued.options().session().name())
            );
            failed.complete(Status.error, 0L, describe(e));
            report.totals().countScenario(Status.error);
            runStatus = Status.worst(runStatus, Status.error);
            log.error("scenario {} failed while defining its steps", queued.name(), e);
            if (config.errorPolicy() == ErrorPolicy.abortRun) {
                fatal("scenario " + queued.name() + " failed while defining its steps");
            }
            return null;
        }
        TestContext context = new TestContext(
            minecraft,
            driver,
            pipeline,
            serverContext,
            queued.name(),
            config.outDir()
        );
        ScenarioReport scenario = report.addScenario(
            new ScenarioReport(queued.name(), queued.group(), queued.tags(), queued.options().session().name())
        );
        List<Step> steps = new ArrayList<>(builder.steps());
        int teardownStart = steps.size();
        steps.addAll(builder.teardownSteps());
        int clientTeardownCount = steps.size() - teardownStart;
        steps.addAll(builder.serverTeardownSteps());
        return new ScenarioRun(
            queued,
            context,
            scenario,
            steps,
            teardownStart,
            clientTeardownCount,
            options.scenarioTimeoutMs(config.scenarioTimeoutMs()),
            options.settleMs(config.defaultSettleMs()),
            options.defaultTimeoutMs(config.defaultTimeoutMs()),
            options.captureOnFailure(config.captureOnFailure())
        );
    }

    private void tickStep(ScenarioRun run, Step step, long now) {
        if (run.stepReport == null) {
            run.stepReport = run.scenario.addStep(
                new StepReport(step.index(), step.name(), step.kind().name(), roleOf(run, run.cursor), step.group())
            );
            run.stepStartedAt = now;
            run.attempts = 0;
            run.stepSequence = ++stepSequence;
            run.settleUntil = 0L;
        }
        if (run.settleUntil > 0L) {
            if (now < run.settleUntil) return;
            run.settleUntil = 0L;
        }
        run.attempts++;
        TestContext context = run.context;
        context.beginStep(step.index(), run.stepSequence);
        ServerContext server = serverContext;
        if (server != null) {
            server.beginStep(run.stepSequence);
        }
        StepReport stepReport = run.stepReport;
        Throwable failure = null;
        try {
            step.body().run(context);
        } catch (Throwable thrown) {
            failure = thrown;
        }
        if (failure != null) {
            stepReport.failed(failure);
            finishStep(run, stepReport, step, Status.error, now);
            return;
        }
        if (context.repeatRequested()) {
            int budget = step.timeoutMs() > 0 ? step.timeoutMs() : run.defaultTimeoutMs;
            if (now - run.stepStartedAt > budget) {
                stepReport.setWaitingFor(context.repeatReason());
                stepReport.failed(
                    new IllegalStateException("step waited longer than " + budget + " ms for " + context.repeatReason())
                );
                finishStep(run, stepReport, step, Status.error, now);
            }
            return;
        }
        finishStep(run, stepReport, step, context.checksFailed() ? Status.fail : Status.pass, now);
    }

    private void finishStep(ScenarioRun run, StepReport stepReport, Step step, Status status, long now) {
        TestContext context = run.context;
        context.checks().forEach(stepReport::addCheck);
        context.log().forEach(stepReport::addLog);
        context.captures().forEach(stepReport::addCapture);
        stepReport.setWaitingFor(context.repeatReason());
        int settle = step.settleMs() > 0 ? step.settleMs() : run.settleMs;
        stepReport.setSettleMs(settle);
        stepReport.complete(status, now - run.stepStartedAt, run.attempts);
        if (status.failed()) {
            // the failure capture belongs to this step, so it has to be recorded before the step is
            // rolled into the run totals, not after
            run.note = "step " + stepReport.index() + " " + stepReport.name() + " " + status.wire();
            captureOnFailure(run, stepReport);
        }
        report.totals().countStep(status, context.checks().size(), failedChecks(context), stepReport.captures().size());
        run.status = Status.worst(run.status, status);
        run.lastStepReport = stepReport;
        run.stepReport = null;
        run.cursor++;
        run.settleUntil = settle > 0 ? now + settle : 0L;
        if (status.failed()) {
            ErrorPolicy policy = config.errorPolicy();
            if (policy == ErrorPolicy.abortScenario || policy == ErrorPolicy.abortRun) {
                abortToTeardown(run);
            }
        }
    }

    private void captureOnFailure(ScenarioRun run, StepReport stepReport) {
        if (!run.captureOnFailure) return;
        stepReport.addCapture(
            new CaptureRef("FULL", TestContext.screenshotPath(run.queued.name(), "error", stepReport.index()), false)
        );
        pipeline.queueFull(run.queued.name(), "error", stepReport.index());
    }

    private void abortToTeardown(ScenarioRun run) {
        run.cursor = run.cursor >= run.teardownStart ? run.steps.size() : run.teardownStart;
        run.stepReport = null;
        run.settleUntil = 0L;
    }

    private void completeScenario(ScenarioRun run) {
        run.scenario.complete(run.status, now() - run.startedAt, run.note);
        report.totals().countScenario(run.status);
        runStatus = Status.worst(runStatus, run.status);
        log.info("scenario {}: {}", run.queued.name(), run.status.wire());
        current = null;
        if (config.errorPolicy() == ErrorPolicy.abortRun && run.status.failed()) {
            fatal("scenario " + run.queued.name() + " failed under ABORT_RUN");
            return;
        }
        enter(Phase.nextScenario);
    }

    // endregion

    // region client pinning

    private void pinClientOptions() {
        Window window = minecraft.getWindow();
        long handle = window.getWindow();
        if (config.headless()) {
            GLFW.glfwHideWindow(handle);
        }
        GLFW.glfwSetWindowSize(handle, config.windowWidthOrDefault(), config.windowHeightOrDefault());
        minecraft.options.guiScale().set(config.guiScale());
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.renderDistance().set(4);
        minecraft.options.enableVsync().set(false);
        minecraft.options.save();
        minecraft.resizeDisplay();
        log.info(
            "pinned gui scale {}, window {}x{}, headless={}",
            config.guiScale(),
            config.windowWidthOrDefault(),
            config.windowHeightOrDefault(),
            config.headless()
        );
    }

    // endregion

    // region watchdog and reporting

    private void watch() {
        long limit = Math.max(1, config.watchdogSeconds()) * 1000L;
        while (!shuttingDown) {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (shuttingDown) return;
            if (now() - lastFrameAt <= limit) continue;
            String dump = threadDump();
            log.error("jechtest-uitest made no progress for {} ms; dumping threads", limit);
            log.error("{}", dump);
            runStatus = Status.worst(runStatus, Status.hung);
            fatalMessage = "no rendered frame for " + limit + " ms";
            report.complete(Status.hung, Instant.now().toString(), now() - startedAt);
            writeReport();
            writeHangDump(dump);
            Runtime.getRuntime().halt(watchdogExitCode); // NOPMD - a shutdown hook would join the threads that are stuck
            return;
        }
    }

    private void fatal(String message) {
        if (fatalMessage == null) fatalMessage = message;
        log.error("jechtest-uitest: {}", message);
        runStatus = Status.worst(runStatus, Status.error);
        enter(Phase.finish);
    }

    private void writeReport() {
        try {
            ReportWriter.write(report, config.outDir());
        } catch (IOException e) {
            log.error("jechtest-uitest could not write the report into {}", config.outDir(), e);
        }
    }

    private void writeHangDump(String dump) {
        try {
            Files.createDirectories(config.outDir());
            Files.writeString(config.outDir().resolve("hang.txt"), dump, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("jechtest-uitest could not write the hang dump", e);
        }
    }

    private void onCaptureWritten(
        String scenario,
        String kind,
        String label,
        int stepIndex,
        String path,
        boolean suspect
    ) {
        log.debug("capture '{}' for scenario {} step {} wrote {}", label, scenario, stepIndex, path);
        ScenarioRun run = current;
        if (run == null || !run.queued.name().equals(scenario)) {
            log.debug("capture {} arrived after its scenario finished; it is on disk but not in the report", path);
            return;
        }
        run.context.recordWrittenCapture(label, path);
        run.context.markWritten(kind, path, suspect);
        // the step that queued it finished in an earlier frame, so its published report needs the
        // same correction or it would name the guessed path
        StepReport published = run.lastStepReport;
        if (published != null) {
            published.markWritten(kind, path, suspect);
        }
        if (suspect) {
            log.warn("capture {} is a single flat colour - wrong framebuffer?", path);
        }
    }

    private static String threadDump() {
        StringBuilder dump = new StringBuilder(4096);
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            dump.append('"').append(thread.getName()).append("\" state=").append(thread.getState()).append(" daemon=")
                    .append(thread.isDaemon()).append('\n');
            for (StackTraceElement frame : entry.getValue()) {
                dump.append("    at ").append(frame).append('\n');
            }
        }
        return dump.toString();
    }

    private static Environment environment(Minecraft minecraft, RunConfig config) {
        Window window = minecraft.getWindow();
        List<String> mods = ModList.get().getMods().stream().map(IModInfo::getModId).sorted().toList();
        String neoforge = ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        return new Environment(
            SharedConstants.getCurrentVersion().getName(),
            neoforge,
            System.getProperty("java.version", "unknown"),
            System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", ""),
            mods,
            config.guiScale(),
            window.getWidth(),
            window.getHeight(),
            minecraft.getMainRenderTarget().width,
            minecraft.getMainRenderTarget().height,
            config.inputMode().name().toUpperCase(Locale.ROOT),
            config.headless()
        );
    }

    // endregion

    // region helpers

    private void enter(Phase next) {
        phase = next;
        phaseStartedAt = now();
        settleUntil = 0L;
    }

    private void settle(int millis) {
        settleUntil = Math.max(settleUntil, now() + millis);
    }

    private long phaseElapsed() {
        return now() - phaseStartedAt;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static int failedChecks(TestContext context) {
        int failed = 0;
        for (var check : context.checks()) {
            if (!check.passed()) failed++;
        }
        return failed;
    }

    private static String roleOf(ScenarioRun run, int cursor) {
        if (cursor >= run.teardownStart + run.clientTeardownCount) return "teardownServer";
        if (cursor >= run.teardownStart) return "teardown";
        return "step";
    }

    private static String describe(Throwable thrown) {
        return thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
    }

    /**
     * One session's worth of scenarios: everything that can run back to back after a single session
     * transition. The tier is what the group needs; the members are the scenarios that share it.
     */
    private record SessionGroup(Session tier, List<Queued> members) {}

    /**
     * A leave-and-rejoin in flight. The phases of a world creation already run through the phase
     * machine; a rejoin is three steps that belong to no phase of their own, so they live here.
     */
    private static final class Reenter {

        /** Whether the leave ends in a world creation rather than a rejoin of the same save. */
        private final boolean createFresh;

        private int step;

        Reenter(boolean createFresh) {
            this.createFresh = createFresh;
        }

        private long startedAt = System.currentTimeMillis();
    }

    /**
     * One scenario waiting its turn.
     *
     * <p>The queue holds the closure that produces the steps rather than the annotation behind it,
     * so everything downstream of it is the same however a scenario's steps were produced.
     *
     * @param name          the registered name
     * @param group         the annotation's group label, empty when unset
     * @param tags          the annotation's tags
     * @param options       the per-scenario tuning, already resolved
     * @param requiresWorld whether the scenario needs a loaded world
     * @param define        hands the scenario's steps to a builder
     */
    private record Queued(
        String name,
        String group,
        List<String> tags,
        ScenarioOptions options,
        boolean requiresWorld,
        Consumer<ScenarioBuilder> define
    ) {

        static Queued of(ScenarioRegistry.Entry entry) {
            return new Queued(
                entry.name(),
                entry.group(),
                entry.tags(),
                entry.options(),
                entry.requiresWorld(),
                entry::define
            );
        }

    }

    /** One scenario in flight. */
    private static final class ScenarioRun {

        private final Queued queued;

        private final TestContext context;

        private final ScenarioReport scenario;

        private final List<Step> steps;

        private final int teardownStart;

        private final int clientTeardownCount;

        private final long scenarioTimeoutMs;

        private final int settleMs;

        private final int defaultTimeoutMs;

        private final boolean captureOnFailure;

        private final long startedAt = now();

        private int cursor;

        private int attempts;

        private long stepSequence;

        private long stepStartedAt;

        private long settleUntil;

        private @Nullable StepReport stepReport;

        /** The step report that just completed, for capture corrections that arrive a frame later. */
        private @Nullable StepReport lastStepReport;

        private Status status = Status.pass;

        private @Nullable String note;

        private ScenarioRun(
            Queued queued,
            TestContext context,
            ScenarioReport scenario,
            List<Step> steps,
            int teardownStart,
            int clientTeardownCount,
            int scenarioTimeoutMs,
            int settleMs,
            int defaultTimeoutMs,
            boolean captureOnFailure
        ) {
            this.queued = queued;
            this.context = context;
            this.scenario = scenario;
            this.steps = steps;
            this.teardownStart = teardownStart;
            this.clientTeardownCount = clientTeardownCount;
            this.scenarioTimeoutMs = scenarioTimeoutMs;
            this.settleMs = settleMs;
            this.defaultTimeoutMs = defaultTimeoutMs;
            this.captureOnFailure = captureOnFailure;
        }

        private @Nullable Step currentStep() {
            return cursor < steps.size() ? steps.get(cursor) : null;
        }
    }

    // endregion
}
