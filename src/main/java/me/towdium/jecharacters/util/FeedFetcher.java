package me.towdium.jecharacters.util;

import com.google.gson.*;
import me.towdium.jecharacters.core.JechCore;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Author: towdium
 * Date:   17-7-31.
 */
public class FeedFetcher {
    public static void fetch(BiConsumer<List<String>, List<String>> stringNRegexp) {
        try {
            String s = IOUtils.toString(new URL(
                    "https://raw.githubusercontent.com/Towdium/JustEnoughCharacters/1.12.0/feed.json"), "UTF-8");
            JsonElement fullE = new JsonParser().parse(s);
            JsonArray fullA = fullE.getAsJsonArray();
            for (JsonElement feedE : fullA) {
                JsonObject feedO = feedE.getAsJsonObject();
                if (feedO.get("version").getAsInt() == 1) {
                    Feed f = new Gson().fromJson(feedO, Feed.class);
                    stringNRegexp.accept(f.string, f.regexp);
                    break;
                }
            }
        } catch (IOException e) {
            JechCore.LOG.warn("Caught an exception when fetching online data.");
        }
    }

    public static class Feed {
        public int version;
        public List<String> string;
        public List<String> regexp;
    }
}
