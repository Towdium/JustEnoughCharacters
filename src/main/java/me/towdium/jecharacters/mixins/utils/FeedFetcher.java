package me.towdium.jecharacters.mixins.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;


public class FeedFetcher {

    private final static int TIMEOUT = 5;
    private final static String GITEE_URL = "https://gitee.com/vfyjxf/JustEnoughCharacters/raw/";
    private final static String GITHUB_URL = "https://raw.githubusercontent.com/Towdium/JustEnoughCharacters/";
    private final static String GITCODE_URL = "https://gitcode.net/cloudvf/JustEnoughCharacters/-/raw/";
    private final static String MC_VERSION = "1.19";
    private final static Logger LOGGER = LogManager.getLogger();

    private volatile String jsonString;
    private final AtomicInteger mainCounter = new AtomicInteger(0);
    private static final AtomicReferenceFieldUpdater<FeedFetcher, String> updater = AtomicReferenceFieldUpdater.newUpdater(FeedFetcher.class, String.class, "jsonString");

    public void fetch() throws IOException {
        //Load classes in advance to prevent deadlocks caused by loading classes in network threads.
        try {
            JsonObject empty = JsonParser.parseString("{\"fake\": {\"array\": [\"empty\"], \"empty\":{}}}").getAsJsonObject();
        } catch (JsonSyntaxException exception) {
            //Do nothing.
        }
        String jsonString = getFeedJson();
        JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
        putData(jsonObject.getAsJsonObject("suffix"), Feed.suffix);
        putData(jsonObject.getAsJsonObject("contains"), Feed.contains);
        putData(jsonObject.getAsJsonObject("equals"), Feed.equals);
        putData(jsonObject.getAsJsonObject("regexp"), Feed.regexp);
    }

    private void putData(JsonObject jsonObject, Map<String, Boolean> data) {
        jsonObject.get("common")
                .getAsJsonArray()
                .forEach(e -> data.put(e.getAsString(), false));
        jsonObject.get("static")
                .getAsJsonArray()
                .forEach(e -> data.put(e.getAsString(), true));
    }

    private String getFeedJson() throws IOException {
        Map<String, AtomicInteger> failureTimes = new ConcurrentHashMap<>(4);
        Map<String, Runnable> taskSupplier = Map.of(
                "gitee", () -> getFeedJson(GITEE_URL + "fabric-" + MC_VERSION + "/feed.json", "gitee", failureTimes),
                "coding", () -> getFeedFromCoding(failureTimes),
                "github", () -> getFeedJson(GITHUB_URL + "fabric-" + MC_VERSION + "/feed.json", "github", failureTimes),
                "gitcode",() ->getFeedJson(GITCODE_URL + "fabric-" + MC_VERSION + "/feed.json", "gitcode", failureTimes)
        );

        taskSupplier.values().forEach(Runnable::run);

        while (jsonString == null) {
            Thread.onSpinWait();
            if (mainCounter.get() > 3) {
                throw new IOException("Failed to fetch feed.json from all sources,using default!");
            }
            boolean allFailed = !failureTimes.isEmpty() && failureTimes.values()
                    .stream()
                    .allMatch(e -> e.get() > mainCounter.get());

            if (allFailed) {
                mainCounter.incrementAndGet();
                taskSupplier.values().forEach(Runnable::run);
            }

        }

        return jsonString;
    }

    private void getFeedFromCoding(Map<String, AtomicInteger> failureTimes) {
        failureTimes.computeIfAbsent("coding", k -> new AtomicInteger(0));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://cloudvf.coding.net/api/user/cloudvf/project/JustEnoughCharacters/shared-depot/JustEnoughCharacters/git/blob/auto-update/feed.json"))
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(e -> {
                    if (jsonString != null) return null;
                    failureTimes.get("coding").incrementAndGet();
                    return null;
                })
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    if (body == null) return;
                    JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
                    String jsonString = jsonObject.get("data")
                            .getAsJsonObject()
                            .get("file")
                            .getAsJsonObject()
                            .get("data")
                            .getAsString();
                    if (updater.compareAndSet(this, null, jsonString)) {
                        LOGGER.info("Successfully fetched feed.json from coding!");
                    }
                });
    }

    private void getFeedJson(String url, String source, Map<String, AtomicInteger> failureTimes) {
        failureTimes.computeIfAbsent(source, k -> new AtomicInteger(0));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(e -> {
                    if (jsonString != null) return null;
                    failureTimes.get(source).incrementAndGet();
                    LOGGER.error("Failed to fetch feed from {}!", source);
                    return null;
                })
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    if (body == null) return;
                    if (updater.compareAndSet(this, null, body)) {
                        LOGGER.info("Successfully fetched feed.json from {}!", source);
                    }
                });
    }

    public static class Feed {
        public final static Map<String, Boolean> suffix = new HashMap<>();
        public final static Map<String, Boolean> contains = new HashMap<>();
        public final static Map<String, Boolean> equals = new HashMap<>();
        public final static Map<String, Boolean> regexp = new HashMap<>();
    }
}
