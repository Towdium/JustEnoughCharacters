package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.Status;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class JUnitXmlWriter {

    private static final String suitesName = "jechtest-uitest";

    private JUnitXmlWriter() {}

    public static String toXml(RunReport report) {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        Counts counts = Counts.of(report);
        xml.append("<testsuites name=\"").append(suitesName).append('"').append(" tests=\"").append(counts.tests)
                .append('"').append(" failures=\"").append(counts.failures).append('"').append(" errors=\"")
                .append(counts.errors).append('"').append(" skipped=\"").append(counts.skipped).append('"')
                .append(" time=\"").append(seconds(report.durationMs())).append("\">\n");
        for (ScenarioReport scenario : report.scenarios()) {
            appendScenario(xml, scenario);
        }
        xml.append("</testsuites>\n");
        return xml.toString();
    }

    private static void appendScenario(StringBuilder xml, ScenarioReport scenario) {
        Counts counts = Counts.of(scenario);
        xml.append("  <testsuite name=\"").append(escape(scenario.name())).append('"').append(" tests=\"")
                .append(counts.tests).append('"').append(" failures=\"").append(counts.failures).append('"')
                .append(" errors=\"").append(counts.errors).append('"').append(" skipped=\"").append(counts.skipped)
                .append('"').append(" time=\"").append(seconds(scenario.durationMs())).append("\">\n");
        if (scenario.steps().isEmpty()) {
            xml.append("    <testcase name=\"").append(escape(scenario.name())).append("\"").append(" classname=\"")
                    .append(escape(scenario.name())).append('"').append(" time=\"0.000\">\n");
            appendOutcome(xml, scenario.status(), scenario.message(), "nothing ran");
            xml.append("    </testcase>\n");
        }
        for (StepReport step : scenario.steps()) {
            appendStep(xml, scenario, step);
        }
        xml.append("  </testsuite>\n");
    }

    private static void appendStep(StringBuilder xml, ScenarioReport scenario, StepReport step) {
        xml.append("    <testcase name=\"").append(escape(step.name())).append('"').append(" classname=\"")
                .append(escape(scenario.name())).append('"').append(" time=\"").append(seconds(step.durationMs()))
                .append('"');
        Status status = Status.valueOf(step.status().toLowerCase(Locale.ROOT));
        if (status == Status.pass) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n");
        appendOutcome(xml, step.status(), failureMessage(step), "nothing recorded");
        xml.append("    </testcase>\n");
    }

    private static void appendOutcome(StringBuilder xml, String status, @Nullable String message, String fallback) {
        String text = message == null ? fallback : message;
        String tag;
        switch (Status.valueOf(status.toLowerCase(Locale.ROOT))) {
            case pass:
                tag = null;
                break;
            case skipped:
                tag = "skipped";
                break;
            case fail:
                tag = "failure";
                break;
            case error:
            case hung:
                tag = "error";
                break;
            default:
                tag = null;
                break;
        }
        if (tag == null) {
            xml.append("      <system-out>").append(escape(text)).append("</system-out>\n");
            return;
        }
        xml.append("      <").append(tag).append(" message=\"").append(escape(text)).append("\">").append(escape(text))
                .append("</").append(tag).append(">\n");
    }

    private static @Nullable String failureMessage(StepReport step) {
        if (step.error() != null) {
            ErrorInfo error = step.error();
            return error.type() + ": " + error.message();
        }
        StringBuilder message = new StringBuilder();
        step.checks().stream().filter(check -> !check.passed()).forEach(
            check -> message.append(check.label()).append(check.detail() == null ? "" : " (" + check.detail() + ")")
                    .append("; ")
        );
        return message.length() == 0 ? null : message.toString();
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    private static String escape(@Nullable String raw) {
        if (raw == null) return "";
        StringBuilder escaped = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&': escaped.append("&amp;"); break;
                case '<': escaped.append("&lt;"); break;
                case '>': escaped.append("&gt;"); break;
                case '"': escaped.append("&quot;"); break;
                case '\'': escaped.append("&apos;"); break;
                case '\n': escaped.append("&#10;"); break;
                case '\r': escaped.append("&#13;"); break;
                case '\t': escaped.append("&#9;"); break;
                default: escaped.append(c); break;
            }
        }
        return escaped.toString();
    }

    private static final class Counts {

        private final int tests;
        private final int failures;
        private final int errors;
        private final int skipped;

        private Counts(int tests, int failures, int errors, int skipped) {
            this.tests = tests;
            this.failures = failures;
            this.errors = errors;
            this.skipped = skipped;
        }

        public int tests() {
            return tests;
        }

        public int failures() {
            return failures;
        }

        public int errors() {
            return errors;
        }

        public int skipped() {
            return skipped;
        }


        static Counts of(RunReport report) {
            int tests = 0;
            int failures = 0;
            int errors = 0;
            int skipped = 0;
            for (ScenarioReport scenario : report.scenarios()) {
                Counts counts = of(scenario);
                tests += counts.tests;
                failures += counts.failures;
                errors += counts.errors;
                skipped += counts.skipped;
            }
            return new Counts(tests, failures, errors, skipped);
        }

        static Counts of(ScenarioReport scenario) {
            if (scenario.steps().isEmpty()) {
                Status status = Status.valueOf(scenario.status().toLowerCase(Locale.ROOT));
                return new Counts(
                    1,
                    status == Status.fail ? 1 : 0,
                    status == Status.error || status == Status.hung ? 1 : 0,
                    status == Status.skipped ? 1 : 0
                );
            }
            int failures = 0;
            int errors = 0;
            int skipped = 0;
            for (StepReport step : scenario.steps()) {
                Status status = Status.valueOf(step.status().toLowerCase(Locale.ROOT));
                if (status == Status.fail) failures++;
                if (status == Status.error || status == Status.hung) errors++;
                if (status == Status.skipped) skipped++;
            }
            return new Counts(scenario.steps().size(), failures, errors, skipped);
        }
    
    }

}
