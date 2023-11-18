package me.towdium.jecharacters.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FabricInfoReader implements Profiler.InfoReader {
    @Override
    public Profiler.ModContainer[] readInfo(InputStream is) {
        JsonObject jsonObject = new JsonParser().parse(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
        if (jsonObject != null) {
            Profiler.ModContainer mc = new Profiler.ModContainer(
                    jsonObject.has("modId") ? jsonObject.get("modId").getAsString() : "",
                    jsonObject.has("displayName") ? jsonObject.get("displayName").getAsString() : "",
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
