package me.towdium.jecharacters.uitest.match;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which recorded comparisons an assertion is about.
 *
 * <p>Every term is optional and all present terms must hold, so {@code MatchFilter(null, "shitou",
 * "石")} reads as "a comparison whose query contains {@code shitou} and whose candidate contains
 * {@code 石}". A {@code null} term is "anything", never "empty": an assertion cannot accidentally
 * pass because it forgot to name a term.
 *
 * <p><b>Port note.</b> The 1.21.1 record of the same name, as a plain immutable class: Java 8 has
 * no records, so the fields are final, the accessors are the record components' names and the
 * public constructor is the canonical one. Nothing else changes.
 *
 * @param method            the exact label of the tap, or {@code null} for any
 * @param queryContains     a substring the query must contain, or {@code null} for any
 * @param candidateContains a substring the candidate must contain, or {@code null} for any
 */
public final class MatchFilter {

    @Nullable
    private final String method;
    @Nullable
    private final String queryContains;
    @Nullable
    private final String candidateContains;

    public MatchFilter(
        @Nullable String method,
        @Nullable String queryContains,
        @Nullable String candidateContains) {
        this.method = method;
        this.queryContains = queryContains;
        this.candidateContains = candidateContains;
    }

    public String method() {
        return method;
    }

    public String queryContains() {
        return queryContains;
    }

    public String candidateContains() {
        return candidateContains;
    }

    /** Whether a recorded comparison satisfies every term. */
    public boolean accepts(MatchRecorder.Record record) {
        if (method != null && !method.equals(record.method())) return false;
        return holds(record.query(), queryContains) && holds(record.candidate(), candidateContains);
    }

    /** Whether any term is set. A filter with no terms matches everything, which is a scenario bug. */
    public boolean isEmpty() {
        return method == null && queryContains == null && candidateContains == null;
    }

    /** The filter as a phrase, for step names and failure details. */
    public String describe() {
        List<String> parts = new ArrayList<>(3);
        if (method != null) parts.add("method=" + method);
        if (queryContains != null) parts.add("query~" + queryContains);
        if (candidateContains != null) parts.add("candidate~" + candidateContains);
        return parts.isEmpty() ? "(any comparison)" : String.join(" and ", parts);
    }

    private static boolean holds(@Nullable String text, @Nullable String needle) {
        if (needle == null) return true;
        return text != null && text.contains(needle);
    }
}
