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

public final class UITestRunner {

    enum Phase {
        waitGameReady, pinOptions, createWorld, awaitWorld, pinWorld, nextScenario, runScenario, finish, exiting
    }

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private static final int gameReadyBudgetMs = 180_000;

    private static final int worldBudgetMs = 180_000;

    private static final int resizeSettleMs = 50;

    private static final int worldReadySettleMs = 200;

    private static final int shutdownCodeFailure = 1;

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
            this.fatalMessage = "input mode REAL is not implemented on the 1.12.2 line; the coremod "
                    + "seam answers SYNTHETIC input only";
            this.runStatus = Status.error;
            this.phase = Phase.finish;
            log.error("jechtest-uitest: {}", fatalMessage);
        }
    }

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

    public static UITestRunner installed() {
        return installed;
    }

    public static String runInteractive(String selection) {
        UITestRunner active = installed;
        if (active != null && !active.shuttingDown) {
            return "a UI test run is already in progress";
        }
        return "jechtest: interactive runs land in a later wave (selection '" + selection + "')";
    }

    public String runId() {
        return runId;
    }

    public boolean shuttingDown() {
        return shuttingDown;
    }

    public List<String> queueNames() {
        List<String> names = new ArrayList<String>(queue.size());
        for (Queued queued : queue) names.add(queued.name());
        return names;
    }

    // region the frame and tick pump

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        lastFrameAt = now();
        if (worldLaunchPending) {
            launchWorld();
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        lastFrameAt = now();
        onFrame();
    }

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

    private void startRecording() {
        MatchRecorder.setEnabled(true);
        for (String problem : ScenarioRegistry.registrationProblems()) {
            log.error("jechtest-uitest: a scenario could not be read and will fail if selected: {}", problem);
        }
    }

    private void createWorld() {
        worldName = WorldBootstrap.worldName(runId);
        WorldBootstrap.deleteStaleWorlds(WorldBootstrap.savesDir(minecraft), ownedWorlds());
        worldLaunchPending = true;
        enter(Phase.awaitWorld);
    }

    private void launchWorld() {
        worldLaunchPending = false;
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

    private void pinWorld() {
        if (minecraft.player != null) {
            minecraft.player.closeScreen();
        }
        minecraft.displayGuiScreen(null);
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
        final Minecraft client = minecraft;
        steps.add(new Step(
                steps.size(),
                StepKind.teardown,
                "reset the screen",
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
                    step.index(), step.name(), step.kind().name(), roleOf(run, run.cursor)));
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
        try {
            minecraft.loadWorld(null);
        } catch (Throwable thrown) {
            log.warn("jechtest-uitest could not stop the world cleanly before exiting", thrown);
        }
        FMLCommonHandler.instance().handleExit(code);
    }

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

    private List<String> ownedWorlds() {
        if (worldName == null) return Collections.emptyList();
        List<String> owned = new ArrayList<String>(1);
        owned.add(worldName);
        return owned;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

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
