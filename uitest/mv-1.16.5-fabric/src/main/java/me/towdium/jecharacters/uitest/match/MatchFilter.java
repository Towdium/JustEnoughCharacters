package me.towdium.jecharacters.uitest.match;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    public boolean accepts(MatchRecorder.Record record) {
        if (method != null && !method.equals(record.method())) return false;
        return holds(record.query(), queryContains) && holds(record.candidate(), candidateContains);
    }

    public boolean isEmpty() {
        return method == null && queryContains == null && candidateContains == null;
    }

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
