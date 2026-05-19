package me.towdium.jecharacters.utils;

import com.google.auto.service.AutoService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@AutoService(Profiler.InfoReader.class)
public class FabricInfoReader implements Profiler.InfoReader {
    @Override
    public Profiler.Platform getPlatform() {
        return Profiler.Platform.FABRIC;
    }

    @Override
    public Profiler.ModContainer[] readInfo(InputStream is) {
        JsonObject jsonObject = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
        if (jsonObject != null) {
            Profiler.ModContainer mc = new Profiler.ModContainer(
                    jsonObject.has("id") ? jsonObject.get("id").getAsString() : "",
                    jsonObject.has("name") ? jsonObject.get("name").getAsString() : "",
                    jsonObject.has("version") ? jsonObject.get("version").getAsString() : ""
            );
            return new Profiler.ModContainer[]{mc};
        } else {
            Profiler.LOGGER.error("Failed to read fabric mod list.");
        }
        try {
            is.close();
        } catch (IOException e) {
            Profiler.LOGGER.error("Failed to close input stream.");
        }
        return null;
    }
}
