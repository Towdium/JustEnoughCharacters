package me.towdium.jecharacters.uitest.engine.report;

import me.towdium.jecharacters.uitest.api.Status;

/**
 * Run-wide counters, written into the report's {@code totals} block.
 *
 * <p><b>Port note.</b> The 1.21.1 class of the same name with its switch expressions rewritten as
 * statements; the counters and the JSON field names are identical, which is what lets the 1.12.2
 * {@code verifyJechUiTest} read {@code run.totals.*} the same way the other checkouts' does.
 */
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
        switch (status) {
            case pass:
                passed++;
                break;
            case fail:
                failed++;
                break;
            case skipped:
                skipped++;
                break;
            default:
                errored++;
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
