package me.towdium.hecharacters.match;

import me.towdium.hecharacters.core.HechCore;
import me.towdium.hecharacters.match.Utilities.IndexSet;
import me.towdium.hecharacters.match.matchables.Char;


/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class PinyinMatcher {
    public static boolean verbose = false;

    // s1.contains(s2)
    @SuppressWarnings("unused")
    public static boolean contains(String s1, CharSequence s2) {
        boolean ret;
        if (Utilities.isChinese(s1)) ret = check(s1, s2.toString());
        else ret = s1.contains(s2);
        return ret;
    }

    private static boolean check(String s1, CharSequence s2) {
        boolean b;
        if (s2 instanceof String) {
            if (s2.toString().isEmpty()) {
                b = true;
            } else {
                b = false;
                for (int i = 0; i < s1.length(); i++) {
                    if (check(s2.toString(), 0, s1, i)) {
                        b = true;
                        break;
                    }
                }
            }
        } else b = s1.contains(s2);
        if (verbose) HechCore.LOG.info("Full: " + s1 + ", Test: " + s2.toString() + ", -> " + b + '.');
        return b;
    }

    static boolean check(String s1, int start1, String s2, int start2) {
        if (start1 == s1.length()) return true;

        Matchable r = Char.get(s2.charAt(start2));
        IndexSet s = r.match(s1, start1);

        if (start2 == s2.length() - 1) {
            int i = s1.length() - start1;
            return s.get(i);
        } else return !s.foreach(i -> !check(s1, start1 + i, s2, start2 + 1));
    }
}
