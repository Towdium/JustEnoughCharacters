package me.towdium.jecharacters.uitest.match;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A bounded, in-process log of the match decisions JECh makes, for the harness to assert on.
 *
 * <p><b>Why this exists.</b> A scenario that types a pinyin query and then looks at a screenshot is
 * asserting on a rendering; the interesting claim is that the engine <em>compared</em> that query
 * against a candidate and said yes. That comparison is what two taps record here:
 *
 * <ul>
 *   <li>every static method of {@code me.towdium.jecharacters.utils.Match} — the funnel every ASM
 *       adapter (creative search, recipe book, other mods' search boxes) is rewritten onto;</li>
 *   <li>the {@code TreeSearcher.search} call inside {@code JechSearchStorage.getSearchResults} — the
 *       PinIn tree JECh's own JEI integration searches with, which does <em>not</em> go through
 *       {@code Match} at all, so a JEI pinyin search would otherwise be invisible.</li>
 * </ul>
 *
 * <p>Both taps are injections into JECh's own classes (see {@code JechSearchStorageMixin} for why the
 * second one could not go into PinIn itself).
 *
 * <p><b>Zero cost outside a harness run.</b> {@link #isEnabled()} is a plain static read and every
 * entry point returns on its first line when recording is off, which is the state a normal dev
 * client and production are both in. The engine turns it on when a run actually starts and off
 * again when the run finishes.
 *
 * <p><b>Bounded.</b> A JEI world load puts tens of thousands of keys into the tree, so what is kept
 * is capped in both directions: the record log is a ring of {@value #capacity} entries, and the
 * key lookup remembers {@value #maxRememberedValues} values. Both caps are far above what a
 * scenario needs and far below anything that would matter on a 3 GB heap.
 *
 * <p>Thread-safe: {@code Match} is called from whatever thread searches, which is not always the
 * client thread.
 */
public final class MatchRecorder {

    /** One recorded comparison: what was compared, the answer, and when. */
    public record Record(String method, @Nullable String query, @Nullable String candidate, boolean result,
                         long millis) {

        /** A one-line rendering, used in check details and failure messages. */
        public String describe() {
            return method + "(" + render(query) + ", " + render(candidate) + ") -> " + result;
        }
    }

    /**
     * The label the search-storage tap gives its records.
     *
     * <p>The scenario files and the step vocabulary spell it as this string, so it names the thing
     * searched (the PinIn tree) rather than the method that happened to call it.
     */
    public static final String searchMethod = "TreeSearcher.search";

    private static final int capacity = 4_096;

    private static final int maxRememberedValues = 200_000;

    private static final int maxText = 512;

    private static final int maxDescribed = 8;

    /** The ring of recorded comparisons, oldest first. */
    private static final ArrayDeque<Record> records = new ArrayDeque<>(capacity);

    /**
     * Which strings a search-storage value was registered under.
     *
     * <p>Identity-keyed on purpose: the tree holds the very instances it was given, and the tap
     * looks a returned value back up by instance. A value registered under several keys — which is
     * the normal case, JEI puts an element under its names, its mod ids and its tooltip strings —
     * keeps them all.
     */
    private static final Map<Object, List<String>> keys = new IdentityHashMap<>();

    private static volatile boolean enabled;

    private MatchRecorder() {}

    /** Whether recording is on. The taps read this first and return when it is off. */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Turns recording on or off; turning it off leaves what was already recorded in place. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Drops every recorded comparison. The engine calls it as each scenario starts. */
    public static void clear() {
        synchronized (records) {
            records.clear();
        }
    }

    /** Records one comparison. A no-op unless recording is enabled. */
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

    /** Remembers the string a search-storage value was registered under. */
    public static void rememberKey(@Nullable Object value, @Nullable String key) {
        if (!enabled || value == null || key == null) return;
        synchronized (keys) {
            if (keys.size() >= maxRememberedValues && !keys.containsKey(value)) return;
            keys.computeIfAbsent(value, ignored -> new ArrayList<>(4)).add(clip(key));
        }
    }

    /** Every string a value was registered under, empty when it was never seen. */
    public static List<String> keysOf(@Nullable Object value) {
        if (value == null) return List.of();
        synchronized (keys) {
            List<String> found = keys.get(value);
            return found == null ? List.of() : List.copyOf(found);
        }
    }

    /**
     * Records one search-storage query and the values it returned.
     *
     * <p>The candidate is the registered keys of the values that came back — for a JEI search on a
     * Chinese client that is the item's own Chinese name, which is what a scenario asserting
     * {@code candidate_contains: "石"} is really asking about. The answer is whether the query
     * matched anything at all.
     *
     * <p>The keys are read from {@link #rememberKey}, so a run that never saw a registration reports
     * the number of results instead: that is a visibly different candidate, not a silent pass.
     */
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

    /** The most recently recorded comparison a filter accepts, or {@code null}. */
    public static @Nullable Record newestMatching(MatchFilter filter) {
        List<Record> snapshot = all();
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            if (filter.accepts(snapshot.get(i))) return snapshot.get(i);
        }
        return null;
    }

    /** Every recorded comparison, oldest first. */
    public static List<Record> all() {
        synchronized (records) {
            return List.copyOf(records);
        }
    }

    /** How many comparisons are held. */
    public static int size() {
        synchronized (records) {
            return records.size();
        }
    }

    /**
     * What the report says when nothing matched: how much was recorded and the tail of it, so a red
     * step can be diagnosed without re-running the scenario.
     */
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

    /** How many recorded comparisons a filter accepts. */
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

    /** A description of the recorder's own state, for a scenario's log. */
    public static String stats() {
        int remembered;
        synchronized (keys) {
            remembered = keys.size();
        }
        return String.format(Locale.ROOT, "match recorder: enabled=%s, %d record(s), %d remembered key(s)",
                enabled, size(), remembered);
    }
}
