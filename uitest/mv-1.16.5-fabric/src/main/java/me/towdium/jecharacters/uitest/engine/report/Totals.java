package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.Status;

/** Run-wide counters, written into the report's {@code totals} block. */
public final class Totals {

    private int scenarios;

    private int passed;

    private int failed;

    private int errored;

    private int skipped;

    private int steps;

    private int checks;

    private int checksFailed;

    private int captures;

    /** Counts one finished scenario and its outcome. */
    public void countScenario(Status status) {
        scenarios++;
        // Port note (1.21.1 → 1.16.5, Java 8): the arrow switch is Java 14+.
        switch (status) {
            case pass:
                passed++;
                break;
            case fail:
                failed++;
                break;
            case error:
            case hung:
                errored++;
                break;
            case skipped:
                skipped++;
                break;
            default:
                break;
        }
    }

    /** Counts one finished step and its outcome. */
    public void countStep(Status status, int checksInStep, int failedChecksInStep, int capturesInStep) {
        steps++;
        checks += checksInStep;
        checksFailed += failedChecksInStep;
        captures += capturesInStep;
    }

    public int scenarios() {
        return scenarios;
    }

    public int passed() {
        return passed;
    }

    public int failed() {
        return failed;
    }

    public int errored() {
        return errored;
    }

    public int skipped() {
        return skipped;
    }

    public int steps() {
        return steps;
    }

    public int checks() {
        return checks;
    }

    public int checksFailed() {
        return checksFailed;
    }

    public int captures() {
        return captures;
    }
}
