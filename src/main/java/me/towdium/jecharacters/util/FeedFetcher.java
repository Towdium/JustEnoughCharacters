package me.towdium.jecharacters.util;

import com.google.gson.*;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.TransformerRegistry;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static me.towdium.jecharacters.JechConfig.Item.*;
import static me.towdium.jecharacters.JechConfig.*;

/**
 * Author: towdium
 * Date:   17-7-31.
 */
public class FeedFetcher {
    public static void fetch() {
        try {
            Feed f = null;
            String s = IOUtils.toString(new URL(
                    "https://raw.githubusercontent.com/Towdium/JustEnoughCharacters/1.12.0/feed.json"), "UTF-8");
            JsonElement fullE = new JsonParser().parse(s);
            JsonArray fullA = fullE.getAsJsonArray();
            for (JsonElement feedE : fullA) {
                JsonObject feedO = feedE.getAsJsonObject();
                if (feedO.get("version").getAsInt() == 1) {
                    f = new Gson().fromJson(feedO, Feed.class);
                    break;
                }
            }
            if (f == null) return;
            update(listDefaultString, listAdditionalString, LIST_ADDITIONAL_STRING, f.string);
            update(listDefaultRegExp, listAdditionalRegExp, LIST_ADDITIONAL_REGEXP, f.regexp);
            update(listDefaultSuffix, listAdditionalSuffix, LIST_ADDITIONAL_SUFFIX, f.suffix);
            update(listDefaultStrsKt, listAdditionalStrsKt, LIST_ADDITIONAL_STRSKT, f.strskt);
            JechConfig.update();
            TransformerRegistry.transformerRegExp.reload();
            TransformerRegistry.transformerString.reload();
            TransformerRegistry.transformerSuffix.reload();
            TransformerRegistry.transformerStrsKt.reload();
        } catch (IOException e) {
            JechCore.LOG.warn("Caught an exception when fetching online data.");
        }
    }

    private static void update(String[] defolt, String[] additional, JechConfig.Item config, List<String> record) {
        HashSet<String> buf = new HashSet<>();
        Collections.addAll(buf, additional);
        if (record != null) buf.addAll(record);
        buf.removeAll(Arrays.asList(defolt));
        config.getProperty().set(buf.stream().sorted().collect(Collectors.toList()).toArray(new String[]{}));
    }

    public static class Feed {
        public List<String> string;
        public List<String> regexp;
        public List<String> suffix;
        public List<String> strskt;
    }
}
