package me.towdium.jecharacters.uitest.match;

/**
 * The filter an {@code assert_match_recorded} step matches against.
 *
 * <p>Every term given must hold, and a {@code null} term means "anything", never "empty" — which is
 * why an all-null filter is refused where it is built rather than here. {@code method} is one of the
 * labels {@link MatchRecorder} writes.
 */
public final class MatchFilter {

    private final String method;
    private final String queryContains;
    private final String candidateContains;

    public MatchFilter(String method, String queryContains, String candidateContains) {
        this.method = method;
        this.queryContains = queryContains;
        this.candidateContains = candidateContains;
    }

    /** Whether a record satisfies every term this filter names. */
    public boolean matches(MatchRecorder.Record record) {
        if (method != null && !method.equals(record.method)) return false;
        if (queryContains != null && !contains(record.query, queryContains)) return false;
        return candidateContains == null || contains(record.candidate, candidateContains);
    }

    /** Whether this filter names nothing at all, which an assertion refuses rather than passing. */
    public boolean isEmpty() {
        return method == null && queryContains == null && candidateContains == null;
    }

    /** How the filter reads in a failure message. */
    public String describe() {
        return "method=" + method + " query~'" + queryContains + "' candidate~'" + candidateContains + "'";
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.contains(needle);
    }
}
