package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.ScenarioRegistry;
import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.CheckResult;
import me.towdium.jecharacters.uitest.api.InputMode;
import me.towdium.jecharacters.uitest.api.RunConfig;
import me.towdium.jecharacters.uitest.api.ScenarioBuilder;
import me.towdium.jecharacters.uitest.api.ScenarioOptions;
import me.towdium.jecharacters.uitest.api.ScenarioSelection;
import me.towdium.jecharacters.uitest.api.Status;
import me.towdium.jecharacters.uitest.api.Step;
import me.towdium.jecharacters.uitest.api.StepKind;
import me.towdium.jecharacters.uitest.api.TestContext;
import me.towdium.jecharacters.uitest.engine.report.Environment;
import me.towdium.jecharacters.uitest.engine.report.ReportWriter;
import me.towdium.jecharacters.uitest.engine.report.RunReport;
import me.towdium.jecharacters.uitest.engine.report.ScenarioReport;
import me.towdium.jecharacters.uitest.engine.report.StepReport;
import me.towdium.jecharacters.uitest.input.CursorState;
import me.towdium.jecharacters.uitest.input.KeyState;
import me.towdium.jecharacters.uitest.match.MatchRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives one harness run: one phase transition and one step per rendered frame.
 *
 * <p>The clock is the frame itself. Nothing here sleeps; the one call that blocks — the world launch —
 * is performed at a tick boundary on the client thread (see {@link #createWorld() createWorld}), and
 * everything else sits behind a re-entrancy guard that turns nested frames into no-ops. A step that is
 * not finished calls {@link TestContext#repeat(String)} and is called again next frame, with no settle
 * in between, until its wait budget runs out.
 *
 * <p>Nothing here may kill the client from inside a frame event: the run writes its report, stops the
 * throwaway world and leaves through FML's exit, which the client's own security manager allows (see
 * the exit note below).
 *
 * <p><b>Port note (leaving the process, which is the other measured delta).</b> 1.21.1 stops the client
 * with {@code minecraft.stop()} and lands its exit code with a shutdown hook that calls
 * {@code Runtime.halt(code)}. Neither half of that survives on 1.12.2, and both were measured:
 * FML installs a {@code SecurityManager} that traps {@code exitVM} unless the caller is FML itself
 * (FMLSecurityManager.java:33-56), so {@code halt} from this package throws {@code ExitTrappedException};
 * and a {@code System.exit} from inside a shutdown hook deadlocks on the JDK's own
 * {@code Shutdown.exit} monitor (the hook-running thread holds it), which was observed as a live client
 * that never left after printing its verdict. So the run does the shutdown itself, on the client
 * thread, before anything else can: stop the world, then exit through
 * {@code FMLCommonHandler.instance().handleExit(code)} — an FML class calling {@code System.exit}, which
 * is allowed — from a thread that is not in a shutdown. The observable contract is unchanged
 * (0 pass, 1 failure, 3 watchdog); only the route is this line's.
 *
 * <p><b>Port note (the frame clock, kept from the previous wave).</b> 1.21.1's runner is pumped by a
 * mixin into {@code Minecraft#runTick} and by NeoForge's {@code RenderFrameEvent.Post}. 1.12.2 keeps
 * the FML bus events {@code TickEvent.ClientTickEvent} and {@code TickEvent.RenderTickEvent}, which
 * FML fires from {@code onPreClientTick}, {@code onPostClientTick}, {@code onRenderTickStart} and
 * {@code onRenderTickEnd} — and, in this Forge build, on the very same bus as
 * {@code MinecraftForge.EVENT_BUS} ({@code FMLCommonHandler.bus()} is a deprecated alias for it). Both
 * are fired from {@code Minecraft.runGameLoop} (Minecraft.java:1207/1213) around the frame's render,
 * so {@code RenderTickEvent} {@code Phase.END} is still once per rendered frame and still the one
 * point in the frame where the frame has been drawn into {@code framebufferMc} and the GL context is
 * current — which is why captures are taken there.
 *
 * <p><b>Port note (what this wave does not have).</b> The 1.21.1 runner is also the session manager:
 * it buckets scenarios into {@code shared}/{@code reenter}/{@code fresh} tiers, leaves and rejoins
 * saves, and runs per-tier worlds. None of that is here. Every scenario of this wave's run shares the
 * one world created up front, the error policy is fixed at {@code abortScenario}, and the capture
 * pipeline, the server context and the input driver are absent for the reasons their own classes
 * give. The phase machine, the step protocol, the watchdog, the report and the exit-code contract are
 * the same.
 */
public final class UITestRunner {

    /** Where the run is in its lifecycle. At most one transition happens per frame. */
    enum Phase {
        waitGameReady, pinOptions, createWorld, awaitWorld, pinWorld, nextScenario, runScenario, finish, exiting
    }

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    /** How long the client may take to reach the title screen. */
    private static final int gameReadyBudgetMs = 180_000;

    /** How long a world may take to create and load. */
    private static final int worldBudgetMs = 180_000;

    /** The settle after a step that changed the screen. */
    private static final int resizeSettleMs = 50;

    /** The settle after the world is ready, so the first frame of it is a whole one. */
    private static final int worldReadySettleMs = 200;

    /** The exit code a run reports when a scenario failed. */
    private static final int shutdownCodeFailure = 1;

    /** The exit code a stalled run reports instead of hanging. */
    private static final int watchdogExitCode = 3;

    private static volatile UITestRunner installed;

    private final Minecraft minecraft;
    private final RunConfig config;
    private final ScenarioSelection selection;
    private final RunReport report;
    private final String runId;
    private final List<Queued> queue = new ArrayList<Queued>();
    private final long startedAt = now();

    private Phase phase = Phase.waitGameReady;
    private long phaseStartedAt = now();
    private volatile long lastFrameAt = now();
    private ScenarioRun current;
    private String worldName;
    private long stepSequence;
    private long settleUntil;
    private boolean inStep;
    private volatile boolean shuttingDown;
    private String fatalMessage;
    private boolean needsWorld;
    private Status runStatus = Status.pass;
    private boolean worldLaunchPending;
    private Throwable worldFailure;

    private UITestRunner(RunConfig config) {
        this.minecraft = Minecraft.getMinecraft();
        this.config = config;
        this.selection = ScenarioSelection.parse(config.selection(), config.exclude());
        this.runId = processId() + "_" + Integer.toHexString(System.identityHashCode(config));
        this.report = new RunReport(
                runId,
                config.selection(),
                environment(minecraft, config),
                Instant.now().toString());
        if (config.inputMode() == InputMode.real) {
            // Refused rather than silently downgraded: the real driver is a later wave, and a run that
            // quietly used synthetic input while the operator asked for the OS cursor would be a
            // result nobody could interpret.
            this.fatalMessage = "input mode REAL is not implemented on the 1.12.2 line; the coremod "
                    + "seam answers SYNTHETIC input only";
            this.runStatus = Status.error;
            this.phase = Phase.finish;
            log.error("jechtest-uitest: {}", fatalMessage);
        }
    }

    /**
     * Installs the runner when a run was requested, and does nothing at all otherwise. Called from the
     * harness mod's init handler.
     */
    public static void bootstrap() {
        if (!RunConfig.requested()) {
            log.info("jechtest: harness idle (no -D{}run)", RunConfig.prefix);
            return;
        }
        RunConfig config = RunConfig.fromSystemProperties();
        UITestRunner runner = new UITestRunner(config);
        MinecraftForge.EVENT_BUS.register(runner);
        installed = runner;
        runner.startWatchdog();
        log.info(
                "jechtest-uitest run {} selecting '{}' (excluding '{}') with {} input, {} s watchdog, out={}, "
                        + "keepOpen={}",
                runner.runId,
                config.selection(),
                config.exclude(),
                config.inputMode(),
                config.watchdogSeconds(),
                config.outDir(),
                config.keepOpen());
    }

    /** The run driving this client, or {@code null} when nothing was requested. */
    public static UITestRunner installed() {
        return installed;
    }

    /**
     * Starts a run against the world the player already has loaded, for the in-game command.
     *
     * <p>The interactive entry point lands with the session tiers: a run needs the world bootstrap
     * above and a way to hand input back when it is over, and this wave has neither for a run that
     * did not create its own world. The refusal is spelled out so the command reports the truth
     * rather than starting a half-run.
     */
    public static String runInteractive(String selection) {
        UITestRunner active = installed;
        if (active != null && !active.shuttingDown) {
            return "a UI test run is already in progress";
        }
        return "jechtest: interactive runs land in a later wave (selection '" + selection + "')";
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
        List<String> names = new ArrayList<String>(queue.size());
        for (Queued queued : queue) names.add(queued.name());
        return names;
    }

    // region the frame and tick pump

    /**
     * One tick of the game's own clock.
     *
     * <p>Also the one place the world is launched from. {@code launchIntegratedServer} blocks and the
     * client thread may only block between ticks, not mid-frame (see
     * {@link #createWorld() createWorld}); a tick boundary is the begin of a frame's work, so the
     * loading screen it drives draws and the frame that follows it is a whole one.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        lastFrameAt = now();
        if (worldLaunchPending) {
            launchWorld();
        }
    }

    /** One rendered frame: the run's unit of work. */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        lastFrameAt = now();
        onFrame();
    }

    /** One frame of the run. */
    public void onFrame() {
        if (shuttingDown) return;
        if (inStep) return;
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

    private void pump() {
        if (settleUntil > 0L) {
            if (now() < settleUntil) return;
            settleUntil = 0L;
        }
        switch (phase) {
            case waitGameReady:
                waitGameReady();
                break;
            case pinOptions:
                pinOptions();
                break;
            case createWorld:
                createWorld();
                break;
            case awaitWorld:
                awaitWorld();
                break;
            case pinWorld:
                pinWorld();
                break;
            case nextScenario:
                nextScenario();
                break;
            case runScenario:
                runScenario();
                break;
            case finish:
                finish();
                break;
            default:
                break;
        }
    }

    private void waitGameReady() {
        GuiScreen screen = minecraft.currentScreen;
        if (screen instanceof GuiMainMenu) {
            enter(Phase.pinOptions);
            return;
        }
        if (phaseElapsed() > gameReadyBudgetMs) {
            fatal("the client never reached the title screen within " + gameReadyBudgetMs + " ms (now showing "
                    + nameOf(screen) + ")");
        }
    }

    /**
     * Pins the client options, resolves the selection, hands input to the harness and starts the world.
     *
     * <p>The 1.21.1 prologue also sizes and hides the window here. On 1.12.2 the window is sized by
     * LWJGL's display mode, which the run does not own (there is no {@code Window} handle to
     * reconfigure), and hiding it would break the real input driver this line does not have yet — so
     * the window is left exactly as it is and the environment block reports its real size instead of
     * the size the run asked for.
     */
    private void pinOptions() {
        pinClientOptions();
        List<ScenarioRegistry.Entry> registered = ScenarioRegistry.scan();
        for (ScenarioRegistry.Entry entry : registered) {
            if (selection.matches(entry.name(), entry.tags(), entry.group(), entry.type())) {
                queue.add(Queued.of(entry));
            }
        }
        log.info(
                "selection '{}' matched {} of {} registered scenarios",
                config.selection(),
                queue.size(),
                registered.size());
        if (queue.isEmpty()) {
            fatal("selection '" + config.selection() + "' matched no scenarios of " + registered.size());
            return;
        }
        needsWorld = false;
        for (Queued queued : queue) {
            if (queued.requiresWorld()) {
                needsWorld = true;
                break;
            }
        }
        report.sessionPlan(
                "shared[" + queue.size() + "] -> reenter[0] -> fresh[0] = 0 rejoin(s), 0 new world(s), 0 shared "
                        + "restarts; order: shared(" + queue.size() + ")");
        startRecording();
        installSeams();
        enter(needsWorld ? Phase.createWorld : Phase.nextScenario);
        settle(resizeSettleMs);
    }

    private void pinClientOptions() {
        minecraft.gameSettings.guiScale = config.guiScale();
        minecraft.gameSettings.hideGUI = false;
        minecraft.gameSettings.pauseOnLostFocus = false;
        log.info(
                "pinned gui scale {}, window {}x{} (display active={}, inGameHasFocus={})",
                config.guiScale(),
                minecraft.displayWidth,
                minecraft.displayHeight,
                Display.isActive(),
                minecraft.inGameHasFocus);
    }

    /**
     * Hands keyboard and pointer input to the harness, once per run.
     *
     * <p>This is the 1.21.1 runner's {@code driver.install()}. Installing before the world exists is
     * deliberate: the seam is what makes the open screen consume the harness's queue instead of the
     * real keyboard, and a run that only armed it at the first scenario would have the world-creation
     * frames reading the operator's real keyboard.
     */
    private void installSeams() {
        KeyState.setControlling(true);
        CursorState.setControlling(true);
        log.info("jechtest-uitest took the input seams; keyboard={} pointer={}",
                KeyState.isControlling(), CursorState.isControlling());
    }

    private void releaseSeams() {
        KeyState.setControlling(false);
        CursorState.setControlling(false);
        log.info("jechtest-uitest released the input seams; keyboard={} pointer={}",
                KeyState.isControlling(), CursorState.isControlling());
    }

    /** Turns the match recorder on and reports any scenario the scan could not read. */
    private void startRecording() {
        MatchRecorder.setEnabled(true);
        for (String problem : ScenarioRegistry.registrationProblems()) {
            log.error("jechtest-uitest: a scenario could not be read and will fail if selected: {}", problem);
        }
    }

    /**
     * Arms the world launch; the launch itself happens at the next tick.
     *
     * <p>Stale test worlds from earlier runs are swept first, keeping this run's own save: two runs of
     * the same run id would otherwise collide on the directory, and a leftover locked world is the
     * single biggest source of flakiness in this kind of harness. The sweep runs here rather than on
     * the launch path because it is pure file work and the frame clock can afford it.
     *
     * <p><b>Port note (this is the one place the 1.12.2 port changes an engine decision rather than a
     * spelling).</b> On 1.21.1 world creation is a call into {@code Minecraft.createWorldOpenFlows()}
     * which the runner makes from the client thread and which pumps the render loop internally, so the
     * frame clock and the phase machine keep running. On 1.12.2 there is no such API: the only entry
     * point is {@link Minecraft#launchIntegratedServer(String, String, WorldSettings)}, and it is
     * <em>blocking</em> — after starting the server it spins in
     * {@code while (!integratedServer.serverIsInRunLoop()) Thread.sleep(200)} (Minecraft.java:2514-2538),
     * then opens the local network connection and returns. So the run arms the launch here, the tick
     * handler performs it on the client thread, and the frame pump polls for {@code world != null &&
     * player != null} in {@link #awaitWorld()}.
     *
     * <p>Running it on a worker thread instead — which the previous wave's source reading suggested —
     * was measured and is impossible: the launcher drives the client's loading screen, and
     * {@code LoadingScreenRenderer.displayString} calls into GL, which the worker thread has no context
     * for ("No OpenGL context found in the current thread", {@code GlStateManager.clear}, first
     * measured boot). The GL context belongs to the client thread, so the blocking call has to happen
     * there. What that costs is stated rather than hidden: the frame clock stalls for the length of the
     * world load, and the watchdog is what tells a slow load from a hung one.
     */
    private void createWorld() {
        worldName = WorldBootstrap.worldName(runId);
        WorldBootstrap.deleteStaleWorlds(WorldBootstrap.savesDir(minecraft), ownedWorlds());
        worldLaunchPending = true;
        enter(Phase.awaitWorld);
    }

    /** Runs the blocking launch, on the client thread at a tick boundary. */
    private void launchWorld() {
        worldLaunchPending = false;
        // The stall this blocks the frame clock for must not count against the watchdog: start its
        // window fresh, so a launch that never returns is caught after a whole watchdog period.
        lastFrameAt = now();
        try {
            log.info("creating throwaway world {} (superflat, creative, seed fixed)", worldName);
            WorldBootstrap.createFreshWorld(minecraft, worldName, worldName);
            log.info("launchIntegratedServer returned for {}", worldName);
        } catch (Throwable thrown) {
            worldFailure = thrown;
            log.error("creating the world {} failed", worldName, thrown);
        }
        lastFrameAt = now();
    }

    /** Waits for the integrated server's world to reach the client. */
    private void awaitWorld() {
        Throwable failure = worldFailure;
        if (failure != null) {
            fatal("creating the world failed: " + describe(failure));
            return;
        }
        if (minecraft.world != null && minecraft.player != null) {
            enter(Phase.pinWorld);
            return;
        }
        if (phaseElapsed() > worldBudgetMs) {
            fatal("the world never loaded within " + worldBudgetMs + " ms (world=" + (minecraft.world != null)
                    + ", player=" + (minecraft.player != null) + ", server="
                    + (minecraft.getIntegratedServer() != null) + ")");
        }
    }

    /** Closes the loading screen and lets the world have the window. */
    private void pinWorld() {
        if (minecraft.player != null) {
            minecraft.player.closeScreen();
        }
        minecraft.displayGuiScreen(null);
        // The window may not be the active one (a CI box driving the run from a terminal); the game
        // takes the mouse only when it is, and nothing the harness does depends on it. Report which
        // one it is rather than trying to force it - the harness drives input through its own seam.
        log.info(
                "world ready: {} (display active={}, inGameHasFocus={})",
                worldName,
                Display.isActive(),
                minecraft.inGameHasFocus);
        enter(Phase.nextScenario);
        settle(worldReadySettleMs);
    }

    private void nextScenario() {
        if (queue.isEmpty()) {
            enter(Phase.finish);
            return;
        }
        Queued queued = queue.remove(0);
        ScenarioRun run = startScenario(queued);
        if (run == null) {
            enter(Phase.nextScenario);
            return;
        }
        current = run;
        enter(Phase.runScenario);
        settle(resizeSettleMs);
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

    // endregion

    // region scenarios and steps

    private ScenarioRun startScenario(Queued queued) {
        if (minecraft.player != null) {
            minecraft.player.closeScreen();
        }
        minecraft.displayGuiScreen(null);
        // The buffer is per scenario: an assertion is about the input this scenario just made, never
        // about a match an earlier scenario happened to leave behind.
        MatchRecorder.clear();
        if (config.guiScale() > 0) minecraft.gameSettings.guiScale = config.guiScale();
        minecraft.gameSettings.hideGUI = false;

        ScenarioBuilder builder = new ScenarioBuilder();
        try {
            queued.define().accept(builder);
        } catch (RuntimeException e) {
            ScenarioReport failed = report.addScenario(
                    new ScenarioReport(queued.name(), queued.group(), queued.tags(), "shared"));
            failed.complete(Status.error, 0L, describe(e));
            report.totals().countScenario(Status.error);
            runStatus = Status.worst(runStatus, Status.error);
            log.error("scenario {} failed while defining its steps", queued.name(), e);
            return null;
        }
        TestContext context = new TestContext(minecraft, queued.name(), config.outDir());
        ScenarioReport scenario = report.addScenario(
                new ScenarioReport(queued.name(), queued.group(), queued.tags(), "shared"));
        List<Step> steps = new ArrayList<Step>(builder.steps());
        int teardownStart = steps.size();
        steps.addAll(builder.teardownSteps());
        int clientTeardownCount = steps.size() - teardownStart;
        // Whatever the scenario left open is closed here, after its own teardown steps, so a scenario
        // that forgot cannot leak a screen into the next one.
        final Minecraft client = minecraft;
        steps.add(new Step(
                steps.size(),
                StepKind.teardown,
                "reset the screen",
                null,
                0,
                0,
                new me.towdium.jecharacters.uitest.api.StepBody() {
                    @Override
                    public void run(TestContext ctx) {
                        client.displayGuiScreen(null);
                    }
                }));
        ScenarioOptions options = queued.options();
        return new ScenarioRun(
                queued,
                context,
                scenario,
                steps,
                teardownStart,
                clientTeardownCount,
                options.scenarioTimeoutMs() > 0 ? options.scenarioTimeoutMs() : config.scenarioTimeoutMs(),
                options.settleMs() > 0 ? options.settleMs() : config.defaultSettleMs(),
                options.defaultTimeoutMs() > 0 ? options.defaultTimeoutMs() : config.defaultTimeoutMs());
    }

    private void tickStep(ScenarioRun run, Step step, long now) {
        if (run.stepReport == null) {
            run.stepReport = run.scenario.addStep(new StepReport(
                    step.index(), step.name(), step.kind().name(), roleOf(run, run.cursor), step.group()));
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
                stepReport.failed(new IllegalStateException(
                        "step waited longer than " + budget + " ms for " + context.repeatReason()));
                finishStep(run, stepReport, step, Status.error, now);
            }
            return;
        }
        finishStep(run, stepReport, step, context.checksFailed() ? Status.fail : Status.pass, now);
    }

    private void finishStep(ScenarioRun run, StepReport stepReport, Step step, Status status, long now) {
        TestContext context = run.context;
        for (CheckResult check : context.checks()) stepReport.addCheck(check);
        for (String line : context.log()) stepReport.addLog(line);
        for (CaptureRef capture : context.captures()) stepReport.addCapture(capture);
        stepReport.setWaitingFor(context.repeatReason());
        int settle = step.settleMs() > 0 ? step.settleMs() : run.settleMs;
        stepReport.setSettleMs(settle);
        stepReport.complete(status, now - run.stepStartedAt, run.attempts);
        if (status.failed()) {
            run.note = "step " + stepReport.index() + " " + stepReport.name() + " " + status.wire();
        }
        report.totals().countStep(
                status, context.checks().size(), failedChecks(context), stepReport.captures().size());
        run.status = Status.worst(run.status, status);
        run.lastStepReport = stepReport;
        run.stepReport = null;
        run.cursor++;
        run.settleUntil = settle > 0 ? now + settle : 0L;
        if (status.failed() && run.cursor < run.teardownStart) {
            // abortScenario: the rest of the body is skipped, the teardown still runs.
            abortToTeardown(run);
        }
    }

    private void abortToTeardown(ScenarioRun run) {
        run.cursor = Math.max(run.cursor, run.teardownStart);
        run.stepReport = null;
        run.settleUntil = 0L;
    }

    private void completeScenario(ScenarioRun run) {
        run.scenario.complete(run.status, now() - run.startedAt, run.note);
        report.totals().countScenario(run.status);
        runStatus = Status.worst(runStatus, run.status);
        log.info("scenario {}: {}", run.queued.name(), run.status.wire());
        current = null;
        enter(Phase.nextScenario);
    }

    // endregion

    // region finishing, watchdog and reporting

    private void finish() {
        if (shuttingDown) {
            phase = Phase.exiting;
            return;
        }
        shuttingDown = true;
        releaseSeams();
        MatchRecorder.setEnabled(false);
        Status status = fatalMessage == null ? runStatus : Status.worst(runStatus, Status.error);
        report.complete(status, Instant.now().toString(), now() - startedAt);
        writeReport();
        log.info(
                "jechtest-uitest {}: {}/{} scenarios passed; report in {}",
                status.wire(),
                report.totals().passed(),
                report.totals().scenarios(),
                config.outDir());
        if (config.keepOpen()) {
            log.info("jechtest-uitest keeping the client open");
            phase = Phase.exiting;
            return;
        }
        final int code = report.failed() ? shutdownCodeFailure : 0;
        phase = Phase.exiting;
        log.info("jechtest-uitest stopping the client with exit code {}", code);
        // Stop the throwaway world the way the client's own exit does, then leave. Both halves are
        // deliberate: Minecraft.shutdown() alone would let the JVM's vanilla exit path land 0, and
        // neither a shutdown hook nor a Runtime.halt can correct the code here (see the class note).
        try {
            minecraft.loadWorld(null);
        } catch (Throwable thrown) {
            log.warn("jechtest-uitest could not stop the world cleanly before exiting", thrown);
        }
        FMLCommonHandler.instance().handleExit(code);
    }

    /** Starts the daemon thread that turns a stalled client into a report instead of a hang. */
    public void startWatchdog() {
        Thread watchdog = new Thread(new Runnable() {
            @Override
            public void run() {
                watch();
            }
        }, "jechtest-uitest-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

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
            // Through FML, like the failure path: a RuntimeException.halt here is trapped by FML's
            // security manager and the stalled client would never leave.
            FMLCommonHandler.instance().handleExit(watchdogExitCode);
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
            File dir = config.outDir();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                log.warn("could not create the hang dump directory {}", dir);
                return;
            }
            Files.write(new File(dir, "hang.txt").toPath(), dump.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("jechtest-uitest could not write the hang dump", e);
        }
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

    /** The saves this run owns, which the stale-world sweep must not touch. */
    private List<String> ownedWorlds() {
        if (worldName == null) return Collections.emptyList();
        List<String> owned = new ArrayList<String>(1);
        owned.add(worldName);
        return owned;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * This JVM's process id, for the run id.
     *
     * <p>{@code ProcessHandle.current()} is Java 9, and this checkout's toolchain is pinned to
     * Java 8, so the runtime MX bean's {@code "<pid>@<host>"} name is parsed instead — the same value
     * the 1.21.1 runner reads, by the only route Java 8 offers.
     */
    private static String processId() {
        String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        return at < 0 ? name : name.substring(0, at);
    }

    private static int failedChecks(TestContext context) {
        int failed = 0;
        for (CheckResult check : context.checks()) {
            if (!check.passed()) failed++;
        }
        return failed;
    }

    private static String roleOf(ScenarioRun run, int cursor) {
        if (cursor >= run.teardownStart + run.clientTeardownCount) return "teardownServer";
        if (cursor >= run.teardownStart) return "teardown";
        return "step";
    }

    private static String nameOf(GuiScreen screen) {
        return screen == null ? "no screen" : screen.getClass().getSimpleName();
    }

    private static String describe(Throwable thrown) {
        return thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
    }

    private static String threadDump() {
        StringBuilder dump = new StringBuilder(4096);
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            dump.append('"').append(thread.getName()).append("\" state=").append(thread.getState())
                    .append(" daemon=").append(thread.isDaemon()).append('\n');
            for (StackTraceElement frame : entry.getValue()) {
                dump.append("    at ").append(frame).append('\n');
            }
        }
        return dump.toString();
    }

    private static Environment environment(Minecraft minecraft, RunConfig config) {
        List<String> mods = new ArrayList<String>();
        for (ModContainer container : Loader.instance().getModList()) {
            mods.add(container.getModId());
        }
        Collections.sort(mods);
        Framebuffer framebuffer = minecraft.getFramebuffer();
        return new Environment(
                Loader.instance().getMCVersionString(),
                ForgeVersion.getVersion(),
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", ""),
                mods,
                config.guiScale(),
                minecraft.displayWidth,
                minecraft.displayHeight,
                framebuffer == null ? -1 : framebuffer.framebufferWidth,
                framebuffer == null ? -1 : framebuffer.framebufferHeight,
                config.inputMode().name().toUpperCase(Locale.ROOT),
                config.headless());
    }

    // endregion

    /**
     * One scenario waiting its turn.
     *
     * <p>The queue holds the closure that produces the steps rather than the annotation behind it, so
     * everything downstream of it is the same however a scenario's steps were produced — which is what
     * will let the JSON layer feed the same queue.
     */
    private static final class Queued {

        private final String name;
        private final String group;
        private final List<String> tags;
        private final ScenarioOptions options;
        private final boolean requiresWorld;
        private final Consumer<ScenarioBuilder> define;

        Queued(
                String name,
                String group,
                List<String> tags,
                ScenarioOptions options,
                boolean requiresWorld,
                Consumer<ScenarioBuilder> define) {
            this.name = name;
            this.group = group;
            this.tags = tags;
            this.options = options;
            this.requiresWorld = requiresWorld;
            this.define = define;
        }

        static Queued of(ScenarioRegistry.Entry entry) {
            return new Queued(
                    entry.name(),
                    entry.group(),
                    entry.tags(),
                    entry.options(),
                    entry.requiresWorld(),
                    entry.define());
        }

        String name() {
            return name;
        }

        String group() {
            return group;
        }

        List<String> tags() {
            return tags;
        }

        ScenarioOptions options() {
            return options;
        }

        boolean requiresWorld() {
            return requiresWorld;
        }

        Consumer<ScenarioBuilder> define() {
            return define;
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
        private final long startedAt = now();

        private int cursor;
        private int attempts;
        private long stepSequence;
        private long stepStartedAt;
        private long settleUntil;
        private StepReport stepReport;
        private StepReport lastStepReport;
        private Status status = Status.pass;
        private String note;

        ScenarioRun(
                Queued queued,
                TestContext context,
                ScenarioReport scenario,
                List<Step> steps,
                int teardownStart,
                int clientTeardownCount,
                long scenarioTimeoutMs,
                int settleMs,
                int defaultTimeoutMs) {
            this.queued = queued;
            this.context = context;
            this.scenario = scenario;
            this.steps = steps;
            this.teardownStart = teardownStart;
            this.clientTeardownCount = clientTeardownCount;
            this.scenarioTimeoutMs = scenarioTimeoutMs;
            this.settleMs = settleMs;
            this.defaultTimeoutMs = defaultTimeoutMs;
        }

        Step currentStep() {
            return cursor < steps.size() ? steps.get(cursor) : null;
        }
    }
}
