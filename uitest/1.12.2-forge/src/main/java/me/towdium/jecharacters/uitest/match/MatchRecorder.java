package me.towdium.jecharacters.uitest.match;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bounded record of the comparisons the engine really made.
 *
 * <p>Two taps feed it, and they are the 1.12.2 forms of the two Mixins on the 1.21.1 line: every
 * static of {@code me.towdium.jecharacters.util.Match} (the funnel JECh's ASM adapters are rewritten
 * onto) and the JEI-facing search inside JECh's own integration — {@code Match.FakeTree.search} /
 * {@code Match.FakeArray.search} for the legacy-JEI suffix-tree replacement, and
 * {@code JechJeiPlugin.JechSearchIndex} for Had Enough Items' search API. Both taps are installed by
 * the harness coremod, in {@link me.towdium.jecharacters.uitest.seam.MatchSeam}.
 *
 * <p><b>Port note.</b> Three deltas from 04's description, all forced by this line and none of them
 * silent:
 *
 * <ul>
 *   <li>1.12.2's {@code Match} has no {@code startsWith} overloads, so the label set is seven
 *       ({@code contains}, {@code containsSeq}, {@code containsIgnoreCase}, {@code equals},
 *       {@code matches}, {@code rank}, {@code matcher}) plus the JEI-side {@code TreeSearcher.search}.</li>
 *   <li>{@code matches(String, CharSequence)} is a pure delegate of {@code matches(String, String)}
 *       on this line, so it is <em>not</em> tapped: tapping it would write the same record twice.</li>
 *   <li>the key table is an equals-keyed map, not an {@code IdentityHashMap}: the legacy-JEI tap
 *       remembers an {@code int} suffix-tree index, which is re-boxed on every search and can never
 *       match by identity.</li>
 * </ul>
 *
 * <p>Off by default, bounded, and emptied per scenario — {@link #clear()} drops the records between
 * scenarios, the remembered keys deliberately outliving it. {@link #isEnabled()} is the first
 * statement of every <em>recording</em> entry point, so a client without {@code -PjechTest} never
 * builds a record; registering a key is not gated on it (see {@link #rememberKey}).
 */
public final class MatchRecorder {

    /** The label the JEI-side search reports under: the pinyin tree that answers a JEI query. */
    public static final String searchMethod = "TreeSearcher.search";

    private static final int capacity = 4_096;
    private static final int maxRememberedValues = 200_000;
    private static final int maxKeysPerValue = 8;
    private static final int maxText = 512;

    private static volatile boolean enabled;
    private static final Deque<Record> records = new ArrayDeque<Record>(capacity);

    /**
     * Which strings a search's values were registered under.
     *
     * <p>Equals-keyed rather than identity-keyed (see the port note on {@link #rememberKey}) and a
     * list per value: an ingredient is put into the legacy-JEI tree once per name it is searchable
     * by — its display name, and its mod id / tooltip / ore dictionary strings when those search
     * modes are on — and a record that only kept the last one would describe a JEI search by
     * whichever registration happened to come last.
     */
    private static final Map<Object, List<String>> keys = new ConcurrentHashMap<Object, List<String>>();

    private MatchRecorder() {}

    // ------------------------------------------------------------------ the seam's entry points

    /** {@code Match.contains(String, CharSequence)}. */
    public static void recordContains(String query, CharSequence candidate, boolean hit) {
        record("contains", query, text(candidate), hit);
    }

    /** {@code Match.contains(CharSequence, CharSequence)}. */
    public static void recordContainsSeq(CharSequence query, CharSequence candidate, boolean hit) {
        record("containsSeq", text(query), text(candidate), hit);
    }

    /** {@code Match.contains(CharSequence, CharSequence, boolean)}. */
    public static void recordContainsCase(
            CharSequence query, CharSequence candidate, boolean ignoreCase, boolean hit) {
        record(ignoreCase ? "containsIgnoreCase" : "containsSeq", text(query), text(candidate), hit);
    }

    /** {@code Match.equals(String, Object)}. */
    public static void recordEquals(String query, Object candidate, boolean hit) {
        record("equals", query, candidate == null ? null : candidate.toString(), hit);
    }

    /** {@code Match.matches(String, String)}. */
    public static void recordMatches(String query, String candidate, boolean hit) {
        record("matches", query, candidate, hit);
    }

    /** {@code Match.rank(Object, String, String)}: a hit iff the rank is not zero. */
    public static void recordRank(Object ranked, String query, String candidate, int rank) {
        record("rank", query, candidate, rank != 0);
    }

    /**
     * {@code Match.matcher(Pattern, CharSequence)}: the returned matcher is one of two shared
     * instances, so the answer is read off its region rather than probed with a call that would
     * consume the state the caller is about to read.
     */
    public static void recordMatcher(Pattern test, CharSequence name, Matcher returned) {
        boolean hit = returned != null && returned.regionEnd() > returned.regionStart();
        record("matcher", test == null ? null : test.pattern(), text(name), hit);
    }

    /** The legacy-JEI suffix tree: {@code Match.FakeTree.put(String, int)}. */
    public static void rememberIndex(String key, int index) {
        rememberKey(Integer.valueOf(index), key);
    }

    /** The HEI search index: {@code JechSearchIndex.put(String, T)}. */
    public static void rememberValue(String key, Object value) {
        rememberKey(value, key);
    }

    /** The legacy-JEI suffix tree: {@code Match.FakeTree.search(String)} answers tree indexes. */
    public static void recordIndexSearch(String token, it.unimi.dsi.fastutil.ints.IntSet results) {
        List<Integer> boxed = new ArrayList<Integer>(results.size());
        it.unimi.dsi.fastutil.ints.IntIterator iterator = results.iterator();
        while (iterator.hasNext()) boxed.add(Integer.valueOf(iterator.nextInt()));
        recordSearch(token, boxed);
    }

    /** The legacy-JEI suffix array: {@code Match.FakeArray.search(String)} answers values. */
    public static void recordListSearch(String token, java.util.List<?> results) {
        recordSearch(token, results);
    }

    /** The HEI search index: {@code JechSearchIndex.getSearchResults(String, Set)}. */
    public static void recordSetSearch(String token, java.util.Set<?> results) {
        recordSearch(token, results);
    }

    // ------------------------------------------------------------------ the recorder itself

    /** Turns recording on. The engine does this when a run really starts. */
    public static void startRecording() {
        setEnabled(true);
    }

    /** Turns recording on or off; turning it off keeps the records already written. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Drops every recorded comparison, keeping the remembered keys.
     *
     * <p><b>Port note.</b> 1.21.1's {@code clear()} is the same method, and it has to be: the key
     * table is the only way a search record can name its candidates, and on this line the legacy-JEI
     * index — the one that registers the keys — is built by JEI <em>before</em> the first scenario
     * starts. Emptying it per scenario would leave every later search record saying
     * "3 result(s)" instead of the names it found, and an assertion on a candidate would fail even
     * though the engine had answered correctly.
     */
    public static void clear() {
        synchronized (records) {
            records.clear();
        }
    }

    /** The record the taps write for a comparison. */
    public static void record(String method, String query, String candidate, boolean hit) {
        if (!enabled) return;
        Record record = new Record(method, clamp(query), clamp(candidate), hit);
        synchronized (records) {
            if (records.size() >= capacity) records.removeFirst();
            records.addLast(record);
        }
    }

    /**
     * Remembers which key a value was registered under. Registering a key is not a comparison: it
     * only makes a later search's candidates readable, because a tree answers with the values it was
     * given and not with the strings they were registered under.
     *
     * <p><b>Port note.</b> Unlike 1.21.1 this does not check {@link #isEnabled()}: on this line the
     * tap's owner — JEI's own {@code IngredientFilter} — registers the whole index once, while the
     * client is still starting, and the run that turns recording on begins a moment later. Gating the
     * keys on the run would mean the index was registered into nothing and no search record could
     * ever name a candidate. Losing the gate costs nothing: the tap itself only exists when the
     * harness coremod is loaded, the map is bounded by {@value #maxRememberedValues}, and it is the
     * records — the part a scenario asserts on — that stay gated.
     *
     * <p>The second 1.21.1 delta is the map's key type: 1.21.1 keys by identity because its tree
     * holds the very instances it was given, while the legacy-JEI tree answers with {@code int}
     * indexes that are re-boxed on every search and could never match by identity. So this is an
     * equals-keyed map — and every value is registered under more than one name, hence the list.
     */
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

    /** Every name a value was registered under; empty when it was never seen. */
    public static List<String> keysOf(Object value) {
        if (value == null) return java.util.Collections.emptyList();
        synchronized (keys) {
            List<String> found = keys.get(value);
            return found == null
                    ? java.util.Collections.<String>emptyList()
                    : new ArrayList<String>(found);
        }
    }

    /** Records a search: the query, and the registration keys of what it returned. */
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

    /** The records written so far, oldest first. */
    public static List<Record> records() {
        synchronized (records) {
            return new ArrayList<Record>(records);
        }
    }

    /** The last {@code count} records, for a failure message. */
    public static List<Record> tail(int count) {
        List<Record> all = records();
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    /** The newest recorded comparison the filter accepts, or {@code null} when none does. */
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

    /**
     * What to say when an assertion finds nothing: the tail of what was recorded instead.
     *
     * <p>"the engine never saw this query" and "the engine saw it and answered no" are different
     * diagnoses, so the miss carries the evidence either way.
     */
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

    /** One comparison the engine made. */
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
