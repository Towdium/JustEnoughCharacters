package me.towdium.jecharacters.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
public class VersionChecker {

    static final Pattern P = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)-(\\d+)\\.(\\d+)\\.(\\d+)$");

    // HIGH: a > b
    @SuppressWarnings("SameParameterValue")
    public static enumResult checkVersion(String toCheck, String standard) {
        if (toCheck.equals("@VERSION@"))
            return enumResult.DEV;

        int[] ai = new int[6];
        int[] bi = new int[6];
        Matcher m = P.matcher(toCheck);
        if (m.matches()) {
            for (int i = 1; i <= 6; i++) {
                ai[i - 1] = Integer.parseInt(m.group(i));
            }
        } else {
            return enumResult.UNKNOWN;
        }
        m = P.matcher(standard);
        if (m.matches()) {
            for (int i = 1; i <= 6; i++) {
                bi[i - 1] = Integer.parseInt(m.group(i));
            }
        } else {
            return enumResult.UNKNOWN;
        }
        for (int i = 0; i < 6; i++) {
            if (ai[i] > bi[i]) {
                return enumResult.HIGH;
            } else if (ai[i] < bi[i]) {
                return enumResult.LOW;
            }
        }
        return enumResult.SAME;
    }

    public enum enumResult {
        HIGH, SAME, LOW, UNKNOWN, DEV;

        public int toInt() {
            switch (this) {
                case HIGH:
                    return 1;
                case SAME:
                    return 0;
                case LOW:
                    return -1;
                case UNKNOWN:
                    return Integer.MIN_VALUE;
                case DEV:
                    return Integer.MAX_VALUE;
                default:
                    throw new RuntimeException("Internal error.");
            }
        }


    }
}
