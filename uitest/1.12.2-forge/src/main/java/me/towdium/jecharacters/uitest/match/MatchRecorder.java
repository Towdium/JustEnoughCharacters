package me.towdium.jecharacters.uitest.match;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MatchRecorder {

    public static final String searchMethod = "TreeSearcher.search";

    private static final int capacity = 4_096;
    private static final int maxRememberedValues = 200_000;
    private static final int maxKeysPerValue = 8;
    private static final int maxText = 512;

    private static volatile boolean enabled;
    private static final Deque<Record> records = new ArrayDeque<Record>(capacity);

    private static final Map<Object, List<String>> keys = new ConcurrentHashMap<Object, List<String>>();

    private MatchRecorder() {}


    public static void recordContains(String query, CharSequence candidate, boolean hit) {
        record("contains", query, text(candidate), hit);
    }

    public static void recordContainsSeq(CharSequence query, CharSequence candidate, boolean hit) {
        record("containsSeq", text(query), text(candidate), hit);
    }

    public static void recordContainsCase(
            CharSequence query, CharSequence candidate, boolean ignoreCase, boolean hit) {
        record(ignoreCase ? "containsIgnoreCase" : "containsSeq", text(query), text(candidate), hit);
    }

    public static void recordEquals(String query, Object candidate, boolean hit) {
        record("equals", query, candidate == null ? null : candidate.toString(), hit);
    }

    public static void recordMatches(String query, String candidate, boolean hit) {
        record("matches", query, candidate, hit);
    }

    public static void recordRank(Object ranked, String query, String candidate, int rank) {
        record("rank", query, candidate, rank != 0);
    }

    public static void recordMatcher(Pattern test, CharSequence name, Matcher returned) {
        boolean hit = returned != null && returned.regionEnd() > returned.regionStart();
        record("matcher", test == null ? null : test.pattern(), text(name), hit);
    }

    public static void rememberIndex(String key, int index) {
        rememberKey(Integer.valueOf(index), key);
    }

    public static void rememberValue(String key, Object value) {
        rememberKey(value, key);
    }

    public static void recordIndexSearch(String token, it.unimi.dsi.fastutil.ints.IntSet results) {
        List<Integer> boxed = new ArrayList<Integer>(results.size());
        it.unimi.dsi.fastutil.ints.IntIterator iterator = results.iterator();
        while (iterator.hasNext()) boxed.add(Integer.valueOf(iterator.nextInt()));
        recordSearch(token, boxed);
    }

    public static void recordListSearch(String token, java.util.List<?> results) {
        recordSearch(token, results);
    }

    public static void recordSetSearch(String token, java.util.Set<?> results) {
        recordSearch(token, results);
    }


    public static void startRecording() {
        setEnabled(true);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void clear() {
        synchronized (records) {
            records.clear();
        }
    }

    public static void record(String method, String query, String candidate, boolean hit) {
        if (!enabled) return;
        Record record = new Record(method, clamp(query), clamp(candidate), hit);
        synchronized (records) {
            if (records.size() >= capacity) records.removeFirst();
            records.addLast(record);
        }
    }

    public static void rememberKey(Object value, String key) {
        if (value == null || key == null) return;
        synchronized (keys) {
            List<String> names = keys.get(value);
            if (names == null) {
                if (keys.size() >= maxRememberedValues) return;
                names = new ArrayList<String>(4);
                keys.put(value, names);
            }
            String clipped = clamp(key);
            if (names.size() < maxKeysPerValue && !names.contains(clipped)) names.add(clipped);
        }
    }

    public static List<String> keysOf(Object value) {
        if (value == null) return java.util.Collections.emptyList();
        synchronized (keys) {
            List<String> found = keys.get(value);
            return found == null
                    ? java.util.Collections.<String>emptyList()
                    : new ArrayList<String>(found);
        }
    }

    public static void recordSearch(String token, Iterable<?> results) {
        if (!enabled) return;
        StringBuilder candidates = new StringBuilder();
        int described = 0;
        int total = 0;
        for (Object value : results) {
            total++;
            if (described >= 16) continue;
            for (String key : keysOf(value)) {
                if (described >= 16) break;
                if (candidates.length() > 0) candidates.append(',');
                candidates.append(key);
                described++;
            }
        }
        String candidate = candidates.length() > 0 ? candidates.toString() : total + " result(s)";
        record(searchMethod, token, candidate, total > 0);
    }

    public static List<Record> records() {
        synchronized (records) {
            return new ArrayList<Record>(records);
        }
    }

    public static List<Record> tail(int count) {
        List<Record> all = records();
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    public static Record newestMatching(MatchFilter filter) {
        synchronized (records) {
            java.util.Iterator<Record> newestFirst = records.descendingIterator();
            while (newestFirst.hasNext()) {
                Record record = newestFirst.next();
                if (filter.matches(record)) return record;
            }
        }
        return null;
    }

    public static String describeMiss(MatchFilter filter) {
        List<Record> tail = tail(5);
        if (tail.isEmpty()) {
            return "nothing was recorded, so nothing matched " + filter.describe();
        }
        return "no record matched " + filter.describe() + "; the last " + tail.size() + ": " + tail;
    }

    private static String clamp(String text) {
        if (text == null) return null;
        return text.length() <= maxText ? text : text.substring(0, maxText) + "...";
    }

    private static String text(CharSequence sequence) {
        return sequence == null ? null : sequence.toString();
    }

    public static final class Record {
        public final String method;
        public final String query;
        public final String candidate;
        public final boolean hit;

        Record(String method, String query, String candidate, boolean hit) {
            this.method = method;
            this.query = query;
            this.candidate = candidate;
            this.hit = hit;
        }

        @Override
        public String toString() {
            return method + "(" + query + ", " + candidate + ") -> " + hit;
        }
    }
}
