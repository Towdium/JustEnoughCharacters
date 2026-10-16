package me.towdium.jecharacters.uitest.match;

public final class MatchFilter {

    private final String method;
    private final String queryContains;
    private final String candidateContains;

    public MatchFilter(String method, String queryContains, String candidateContains) {
        this.method = method;
        this.queryContains = queryContains;
        this.candidateContains = candidateContains;
    }

    public boolean matches(MatchRecorder.Record record) {
        if (method != null && !method.equals(record.method)) return false;
        if (queryContains != null && !contains(record.query, queryContains)) return false;
        return candidateContains == null || contains(record.candidate, candidateContains);
    }

    public boolean isEmpty() {
        return method == null && queryContains == null && candidateContains == null;
    }

    public String describe() {
        return "method=" + method + " query~'" + queryContains + "' candidate~'" + candidateContains + "'";
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.contains(needle);
    }
}
