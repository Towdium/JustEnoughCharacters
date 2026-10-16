package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.CaptureRef;
import me.towdium.jecharacters.uitest.api.CheckResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ReportWriter {

    public static final String jsonFile = "report.json";

    public static final String textFile = "report.txt";

    public static final String junitFile = "junit.xml";

    private static final Logger log = LogManager.getLogger("jechtest-uitest");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private ReportWriter() {}

    public static void write(RunReport report, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        writeAtomically(outDir.resolve(jsonFile), gson.toJson(report));
        writeAtomically(outDir.resolve(textFile), summaryOf(report));
        writeAtomically(outDir.resolve(junitFile), JUnitXmlWriter.toXml(report));
        logSummary(report);
    }

    public static String summaryOf(RunReport report) {
        StringBuilder text = new StringBuilder(1024);
        Environment environment = report.environment();
        text.append("status: ").append(report.status()).append("   run: ").append(report.runId())
                .append("   duration: ").append(report.durationMs()).append(" ms\n");
        text.append("selection: ").append(report.selection()).append('\n');
        if (!report.sessionPlan().isEmpty()) {
            text.append("session plan: ").append(report.sessionPlan()).append('\n');
        }
        text.append("environment: mc=").append(environment.minecraft()).append(" fabric=")
                .append(environment.fabric()).append(" java=").append(environment.java()).append(" os=")
                .append(environment.os()).append(" guiScale=").append(environment.guiScale()).append(" window=")
                .append(environment.windowWidth()).append('x').append(environment.windowHeight())
                .append(" framebuffer=").append(environment.framebufferWidth()).append('x')
                .append(environment.framebufferHeight()).append(" input=").append(environment.inputMode())
                .append(" headless=").append(environment.headless()).append('\n');
        Totals totals = report.totals();
        text.append("totals: scenarios=").append(totals.passed()).append('/').append(totals.scenarios())
                .append(" passed, failed=").append(totals.failed()).append(", errored=").append(totals.errored())
                .append(", skipped=").append(totals.skipped()).append(", steps=").append(totals.steps())
                .append(", checks=").append(totals.checks() - totals.checksFailed()).append('/').append(totals.checks())
                .append(", captures=").append(totals.captures()).append('\n');
        for (ScenarioReport scenario : report.scenarios()) {
            text.append("scenario ").append(scenario.name()).append(": ").append(scenario.status()).append(" (")
                    .append(scenario.durationMs()).append(" ms)");
            if (scenario.message() != null) text.append("  ").append(scenario.message());
            text.append('\n');
            appendSteps(text, scenario);
        }
        return text.toString();
    }

    public static void logSummary(RunReport report) {
        Totals totals = report.totals();
        log.info(
            "jechtest-uitest {}: {}/{} scenarios passed, {}/{} checks passed, {} captures, {} ms",
            report.status(),
            totals.passed(),
            totals.scenarios(),
            totals.checks() - totals.checksFailed(),
            totals.checks(),
            totals.captures(),
            report.durationMs()
        );
    }

    private static void appendSteps(StringBuilder text, ScenarioReport scenario) {
        for (StepReport step : scenario.steps()) {
            boolean interesting = !"PASS".equals(step.status());
            if (!interesting && step.checks().isEmpty() && step.captures().isEmpty()) continue;
            text.append("  step ").append(step.index()).append(' ').append(step.name()).append(": ")
                    .append(step.status()).append(" (").append(step.durationMs()).append(" ms)");
            if (step.attempts() > 1) text.append(" attempts=").append(step.attempts());
            if (step.waitingFor() != null) text.append(" waitingFor=").append(step.waitingFor());
            text.append('\n');
            if (step.error() != null) {
                text.append("    error ").append(step.error().type()).append(": ").append(step.error().message())
                        .append('\n');
            }
            for (CheckResult check : step.checks()) {
                if (check.passed() && !interesting) continue;
                text.append("    check ").append(check.label()).append(": ").append(check.passed() ? "ok" : "FAILED");
                if (check.detail() != null) text.append(" (").append(check.detail()).append(')');
                text.append('\n');
            }
            for (CaptureRef capture : step.captures()) {
                text.append("    capture ").append(capture.path());
                if (capture.suspect()) text.append("  SUSPECT flat colour");
                text.append('\n');
            }
            for (String line : step.log()) {
                text.append("    log ").append(line).append('\n');
            }
        }
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, content.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
