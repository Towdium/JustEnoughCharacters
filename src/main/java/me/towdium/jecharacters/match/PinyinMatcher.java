package me.towdium.jecharacters.match;

import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.match.Utilities.IndexSet;
import me.towdium.jecharacters.match.matchables.Char;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class PinyinMatcher {
    static final Pattern p = Pattern.compile("a");
    public static boolean verbose = false;

    @SuppressWarnings("unused")
    public static Matcher matcher(Pattern test, CharSequence name) {
        if (Utilities.isChinese(name)) {
            String testS = test.toString();
            String nameS = name.toString();
            if (testS.startsWith(".*")) testS = testS.substring(2);
            if (testS.endsWith(".*")) testS = testS.substring(0, testS.length() - 2);
            boolean ret = check(nameS, testS);
            return ret ? p.matcher("a") : p.matcher("");
        } else return test.matcher(name);
    }

    // s1.contains(s2)
    @SuppressWarnings("unused")
    public static boolean contains(String s1, CharSequence s2) {
        boolean ret;
        if (Utilities.isChinese(s1)) ret = check(s1, s2.toString());
        else ret = s1.contains(s2);
        return ret;
    }

    public static void refresh() {
        Char.refresh();
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
        if (verbose) JechCore.LOG.info("Full: " + s1 + ", Test: " + s2.toString() + ", -> " + b + '.');
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
