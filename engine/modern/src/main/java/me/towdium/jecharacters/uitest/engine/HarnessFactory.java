package me.towdium.jecharacters.uitest.engine;

import me.towdium.jecharacters.uitest.api.CapturePipeline;
import me.towdium.jecharacters.uitest.api.InputDriver;
import me.towdium.jecharacters.uitest.api.InputMode;
import me.towdium.jecharacters.uitest.api.RunConfig;
import me.towdium.jecharacters.uitest.capture.FrameCapturePipeline;
import me.towdium.jecharacters.uitest.input.RealInputDriver;
import me.towdium.jecharacters.uitest.input.SyntheticInputDriver;

final class HarnessFactory {

    private HarnessFactory() {}

    static InputDriver driver(InputMode mode) {
        return mode == InputMode.real ? new RealInputDriver() : new SyntheticInputDriver();
    }

    static CapturePipeline pipeline(RunConfig config) {
        return new FrameCapturePipeline(config.outDir());
    }
}
