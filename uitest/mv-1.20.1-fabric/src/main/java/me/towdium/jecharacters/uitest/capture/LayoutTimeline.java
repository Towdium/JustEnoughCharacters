package me.towdium.jecharacters.uitest.capture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.towdium.jecharacters.uitest.api.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The file-backed {@link Timeline}: one JSON object per line under
 * {@code <outDir>/timeline/<scenario>.jsonl}, written next to the report.
 *
 * <p>A record is {@code {"frame":N,"t":<millis>,"name":"…","blob":"…"}} — the scenario's own frame
 * counter, the wall-clock timecode, the record's name and the blob verbatim. Each append opens,
 * writes and closes the file: the volume is a handful of lines per frame at most, and appending in
 * real time is what makes the log survive a run that dies mid-scenario.
 *
 * <p>Nothing here throws into a run — a log that cannot be written is warned about and dropped, the
 * same rule every other capture artifact follows.
 */
public final class LayoutTimeline implements Timeline {

    /** Directory under the run output root that holds the per-scenario logs. */
    public static final String timelineDirectory = "timeline";

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

    private static final Gson gson = new Gson();

    private final Path file;
    private final long startedAt = System.currentTimeMillis();
    private long frame;

    /** A timeline for one scenario, replacing any log a previous run of it left behind. */
    public LayoutTimeline(Path outDir, String scenario) {
        this.file = outDir.resolve(relativePath(scenario));
        try {
            Files.createDirectories(file.getParent());
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("[uitest] timeline {} could not be reset: {}", file, e.toString());
        }
    }

    /** Where a scenario's log lands, relative to the run's output directory. */
    public static String relativePath(String scenario) {
        return timelineDirectory + "/" + FrameCapturePipeline.sanitize(scenario) + ".jsonl";
    }

    /** How many records this timeline has taken. */
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
            Files.writeString(
                file,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            logger.warn("[uitest] timeline {} could not take a record: {}", file, e.toString());
        }
    }

    /** The log file, for a caller that wants to name it in a report. */
    public Path file() {
        return file;
    }
}
