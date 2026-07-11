package me.towdium.jecharacters.utils;

import java.util.Locale;

public class HexcessibleHooks {

    public static int mergeScore(
            int originalScore,
            String query,
            String candidate
    ) {
        if (originalScore != 0
                || query == null
                || candidate == null
                || query.isEmpty()
        ) {
            return originalScore;
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);

        if (!Match.contains(normalizedCandidate, normalizedQuery)) {
            return 0;
        }

        return Math.max(10, normalizedQuery.codePointCount(0, normalizedQuery.length()) * 10);
    }
}
