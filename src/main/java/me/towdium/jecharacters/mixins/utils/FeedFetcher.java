package me.towdium.jecharacters.mixins.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FeedFetcher {

    private final static String MC_VERSION = "1.19";
    private final static Logger LOGGER = LogManager.getLogger();

    public static void fetch() {
        CompletableFuture<String> feedJson = getFeedJson();
        try {
            JsonObject jsonObject = JsonParser.parseString(feedJson.get(5000, TimeUnit.MILLISECONDS)).getAsJsonObject();
            putData(jsonObject.getAsJsonObject("suffix"), Feed.suffix);
            putData(jsonObject.getAsJsonObject("contains"), Feed.contains);
            putData(jsonObject.getAsJsonObject("equals"), Feed.equals);
            putData(jsonObject.getAsJsonObject("regexp"), Feed.regexp);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.error("Can't fetch feed from network,using default!");
        }
    }

    private static void putData(JsonObject jsonObject, Map<String, Boolean> data){
        jsonObject.get("common")
                .getAsJsonArray()
                .forEach(e -> data.put(e.getAsString(), false));
        jsonObject.get("static")
                .getAsJsonArray()
                .forEach(e -> data.put(e.getAsString(), true));
    }

    private static CompletableFuture<String> getFeedJson() {

        CompletableFuture<String> githubGetter = CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/Towdium/JustEnoughCharacters/fabric-/" + MC_VERSION + "/feed.json");
                URLConnection cnn = url.openConnection();
                cnn.setConnectTimeout(5000);
                cnn.setReadTimeout(5000);
                return IOUtils.toString(cnn.getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                LOGGER.error("Failed to fetch feed json", e);
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<String> giteeGetter = CompletableFuture.supplyAsync(() -> {
            try {
                URL url = new URL("https://gitee.com/vfyjxf/JustEnoughCharacters/raw/fabric-" + MC_VERSION + "/feed.json");
                URLConnection cnn = url.openConnection();
                cnn.setConnectTimeout(5000);
                cnn.setReadTimeout(5000);
                return IOUtils.toString(cnn.getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                LOGGER.error("Failed to fetch feed json from gitee", e);
                throw new RuntimeException(e);
            }
        });

        return giteeGetter.applyToEither(githubGetter, s -> s);
    }

    public static class Feed {
        public final static Map<String, Boolean> suffix = new HashMap<>();
        public final static Map<String, Boolean> contains = new HashMap<>();
        public final static Map<String, Boolean> equals = new HashMap<>();
        public final static Map<String, Boolean> regexp = new HashMap<>();
    }
}
