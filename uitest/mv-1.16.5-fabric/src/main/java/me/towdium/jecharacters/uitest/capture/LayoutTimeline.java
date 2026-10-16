package me.towdium.jecharacters.uitest.capture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.towdium.jecharacters.uitest.api.Timeline;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class LayoutTimeline implements Timeline {

    public static final String timelineDirectory = "timeline";

    private static final Logger logger = LogManager.getLogger("jechtest/uitest");

    private static final Gson gson = new Gson();

    private final Path file;
    private final long startedAt = System.currentTimeMillis();
    private long frame;

    public LayoutTimeline(Path outDir, String scenario) {
        this.file = outDir.resolve(relativePath(scenario));
        try {
            Files.createDirectories(file.getParent());
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("[uitest] timeline {} could not be reset: {}", file, e.toString());
        }
    }

    public static String relativePath(String scenario) {
        return timelineDirectory + "/" + FrameCapturePipeline.sanitize(scenario) + ".jsonl";
    }

    public long records() {
        return frame;
    }

    @Override
    public void record(String name, String blob) {
        long index = frame++;
        JsonObject object = new JsonObject();
        object.addProperty("frame", index);
        object.addProperty("t", System.currentTimeMillis() - startedAt);
        object.addProperty("name", name);
        object.addProperty("blob", blob);
        String line = gson.toJson(object) + System.lineSeparator();
        try {
            Files.write(
                file,
                line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            logger.warn("[uitest] timeline {} could not take a record: {}", file, e.toString());
        }
    }

    public Path file() {
        return file;
    }
}
