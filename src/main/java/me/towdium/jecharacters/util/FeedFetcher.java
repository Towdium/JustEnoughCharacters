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
            HashSet<String> buf = new HashSet<>();
            Collections.addAll(buf, JechConfig.listAdditionalString);
            buf.addAll(f.string);
            buf.removeAll(Arrays.asList(JechConfig.listDefaultString));
            JechConfig.Item.LIST_ADDITIONAL_STRING.getProperty()
                    .set(buf.stream().sorted().collect(Collectors.toList()).toArray(new String[]{}));
            buf.clear();
            Collections.addAll(buf, JechConfig.listAdditionalRegExp);
            buf.addAll(f.regexp);
            buf.removeAll(Arrays.asList(JechConfig.listDefaultRegExp));
            JechConfig.Item.LIST_ADDITIONAL_REGEXP.getProperty()
                    .set(buf.stream().sorted().collect(Collectors.toList()).toArray(new String[]{}));
            buf.clear();
            Collections.addAll(buf, JechConfig.listAdditionalSuffix);
            buf.addAll(f.suffix);
            buf.removeAll(Arrays.asList(JechConfig.listDefaultSuffix));
            JechConfig.Item.LIST_ADDITIONAL_SUFFIX.getProperty()
                    .set(buf.stream().sorted().collect(Collectors.toList()).toArray(new String[]{}));
            JechConfig.update();
            TransformerRegistry.transformerRegExp.reload();
            TransformerRegistry.transformerString.reload();
            TransformerRegistry.transformerSuffix.reload();
        } catch (IOException e) {
            JechCore.LOG.warn("Caught an exception when fetching online data.");
        }
    }

    public static class Feed {
        public List<String> string;
        public List<String> regexp;
        public List<String> suffix;
    }
}
