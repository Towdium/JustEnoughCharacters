package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.api.CapturePipeline;
import me.towdium.jecharacters.uitest.api.InputDriver;
import me.towdium.jecharacters.uitest.api.InputMode;
import me.towdium.jecharacters.uitest.api.RunConfig;
import me.towdium.jecharacters.uitest.capture.FrameCapturePipeline;
import me.towdium.jecharacters.uitest.input.RealInputDriver;
import me.towdium.jecharacters.uitest.input.SyntheticInputDriver;

/**
 * The one place the engine names the input and capture half of the harness: the driver for an input
 * mode and the capture pipeline for an output directory.
 *
 * <p>Keeping the names here rather than scattering them through the runner means the two halves are
 * joined at exactly two call sites, and the engine's own contract with them is the pair of
 * interfaces in {@code uitest.api}.
 *
 * <p><b>Port note.</b> The upstream factory also built the golden-image comparator; the golden
 * baselines are not ported, so there is no third seam here.
 */
final class HarnessFactory {

    private HarnessFactory() {}

    /** The driver for the run's input mode: a faked pointer, or the real OS cursor. */
    static InputDriver driver(InputMode mode) {
        return mode == InputMode.real ? new RealInputDriver() : new SyntheticInputDriver();
    }

    /** The capture pipeline, writing under the run's output directory. */
    static CapturePipeline pipeline(RunConfig config) {
        return new FrameCapturePipeline(config.outDir());
    }
}
