package me.towdium.jecharacters.uitest.match;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class MatchRecorder {

    public record Record(String method, @Nullable String query, @Nullable String candidate, boolean result,
                         long millis) {

        public String describe() {
            return method + "(" + render(query) + ", " + render(candidate) + ") -> " + result;
        }
    }

    public static final String searchMethod = "TreeSearcher.search";

    private static final int capacity = 4_096;

    private static final int maxRememberedValues = 200_000;

    private static final int maxText = 512;

    private static final int maxDescribed = 8;

    private static final ArrayDeque<Record> records = new ArrayDeque<>(capacity);

    private static final Map<Object, List<String>> keys = new IdentityHashMap<>();

    private static volatile boolean enabled;

    private MatchRecorder() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void clear() {
        synchronized (records) {
            records.clear();
        }
    }

    public static void record(String method, @Nullable String query, @Nullable String candidate, boolean result) {
        if (!enabled) return;
        Record entry = new Record(method, clip(query), clip(candidate), result, System.currentTimeMillis());
        synchronized (records) {
            while (records.size() >= capacity) {
                records.removeFirst();
            }
            records.addLast(entry);
        }
    }

    public static void rememberKey(@Nullable Object value, @Nullable String key) {
        if (!enabled || value == null || key == null) return;
        synchronized (keys) {
            if (keys.size() >= maxRememberedValues && !keys.containsKey(value)) return;
            keys.computeIfAbsent(value, ignored -> new ArrayList<>(4)).add(clip(key));
        }
    }

    public static List<String> keysOf(@Nullable Object value) {
        if (value == null) return List.of();
        synchronized (keys) {
            List<String> found = keys.get(value);
            return found == null ? List.of() : List.copyOf(found);
        }
    }

    public static void recordSearch(@Nullable String query, @Nullable List<?> results) {
        if (!enabled) return;
        boolean hit = results != null && !results.isEmpty();
        String candidate = null;
        if (hit) {
            List<String> surfaced = new ArrayList<>();
            for (Object value : results) {
                for (String key : keysOf(value)) {
                    if (!surfaced.contains(key)) surfaced.add(key);
                }
                if (surfaced.size() >= 16) break;
            }
            candidate = surfaced.isEmpty() ? String.valueOf(results.size()) + " result(s)"
                    : String.join(" | ", surfaced);
        }
        record(searchMethod, query, candidate, hit);
    }

    public static @Nullable Record newestMatching(MatchFilter filter) {
        List<Record> snapshot = all();
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            if (filter.accepts(snapshot.get(i))) return snapshot.get(i);
        }
        return null;
    }

    public static List<Record> all() {
        synchronized (records) {
            return List.copyOf(records);
        }
    }

    public static int size() {
        synchronized (records) {
            return records.size();
        }
    }

    public static String describeMiss(MatchFilter filter) {
        List<Record> snapshot = all();
        if (snapshot.isEmpty()) {
            return "no comparison was recorded at all while looking for " + filter.describe()
                    + " (is recording on, and did the query reach the engine?)";
        }
        int from = Math.max(0, snapshot.size() - maxDescribed);
        String tail = snapshot.subList(from, snapshot.size()).stream()
                .map(Record::describe)
                .collect(Collectors.joining("; "));
        return "no comparison matched " + filter.describe() + " in " + snapshot.size()
                + " recorded; the last " + (snapshot.size() - from) + " were: " + tail;
    }

    public static int countMatching(MatchFilter filter) {
        int count = 0;
        for (Record record : all()) {
            if (filter.accepts(record)) count++;
        }
        return count;
    }

    private static @Nullable String clip(@Nullable String text) {
        if (text == null) return null;
        return text.length() <= maxText ? text : text.substring(0, maxText) + "\u2026";
    }

    private static String render(@Nullable String text) {
        if (text == null) return "null";
        String clipped = text.length() <= 60 ? text : text.substring(0, 60) + "\u2026";
        return '"' + clipped + '"';
    }

    public static String stats() {
        int remembered;
        synchronized (keys) {
            remembered = keys.size();
        }
        return String.format(Locale.ROOT, "match recorder: enabled=%s, %d record(s), %d remembered key(s)",
                enabled, size(), remembered);
    }
}
