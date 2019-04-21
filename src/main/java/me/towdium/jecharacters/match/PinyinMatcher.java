package me.towdium.jecharacters.match;

import me.towdium.jecharacters.core.JechCore;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class PinyinMatcher {
    static final Pattern p = Pattern.compile("a");
    public static boolean verbose = false;

    private static boolean containsChinese(CharSequence s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (isCharacter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    public static Matcher checkReg(Pattern test, CharSequence name) {
        if (containsChinese(name)) {
            String testS = test.toString();
            String nameS = name.toString();
            if (testS.startsWith(".*")) testS = testS.substring(2);
            if (testS.endsWith(".*")) testS = testS.substring(0, testS.length() - 2);
            boolean ret = checkChinese(nameS, testS);
            return ret ? p.matcher("a") : p.matcher("");
        } else
            return test.matcher(name);
    }

    // s1.contains(s2)
    @SuppressWarnings("unused")
    public static boolean checkStr(String s1, CharSequence s2) {
        boolean ret;
        if (containsChinese(s1)) ret = checkChinese(s1, s2.toString());
        else ret = s1.contains(s2);
        return ret;
    }

    public static void refresh() {
        CharMul.refresh();
    }

    private static boolean isCharacter(int i) {
        return 0x3007 <= i && i < 0x9FA5;
    }

    private static boolean checkChinese(String s1, CharSequence s2) {
        boolean b;
        if (s2 instanceof String) {
            if (s2.toString().isEmpty()) {
                b = true;
            } else {
                b = false;
                for (int i = 0; i < s1.length(); i++) {
                    if (checkChinese(s2.toString(), 0, s1, i)) {
                        b = true;
                        break;
                    }
                }
            }
        } else b = s1.contains(s2);
        if (verbose) JechCore.LOG.info("Full: " + s1 + ", Test: " + s2.toString() + ", -> " + b + '.');
        return b;
    }

    private static boolean checkChinese(String s1, int start1, String s2, int start2) {
        if (start1 == s1.length()) return true;

        Matchable r = getCharacter(s2.charAt(start2));
        IndexSet s = r.match(s1, start1);

        if (start2 == s2.length() - 1) {
            int i = s1.length() - start1;
            return s.get(i);
        } else return !s.foreach(i -> !checkChinese(s1, start1 + i, s2, start2 + 1));
    }

    private static int strCmp(String a, String b, int aStart) {
        int len = Math.min(a.length() - aStart, b.length());
        for (int i = 0; i < len; i++)
            if (a.charAt(i + aStart) != b.charAt(i)) return i;
        return len;
    }

    static Matchable getCharacter(char ch) {
        if (isCharacter(ch)) return CharMul.get(ch);
        else return CharSin.get(ch);
    }

    private static class CharSin implements Matchable {
        private static final CharSin INSTANCE = new CharSin();
        private char ch;

        private CharSin() {
        }

        private static CharSin get(char ch) {
            INSTANCE.ch = ch;
            return INSTANCE;
        }

        @Override
        public IndexSet match(String str, int start) {
            return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE;
        }
    }

    private static class CharMul implements Matchable {
        private static CharMul[] cache = new CharMul[41000];
        private static final int START = 0x3007;
        private static final int END = 0x9FA5;

        static {
            JechCore.LOG.info("Starting generating pattern");
            for (int i = START; i <= END; i++) cache[i] = genRep((char) i);
            JechCore.LOG.info("Finished generating pattern");
        }

        private Matchable[] patterns = new Matchable[0];

        private CharMul() {
        }

        static CharMul get(char ch) {
            return cache[ch];
        }

        private static CharMul genRep(char ch) {
            CharMul p = new CharMul();
            ArrayList<Matchable> patterns = new ArrayList<>();
            patterns.add(new InstanceRaw(ch));
            p.patterns = patterns.toArray(p.patterns);
            String[] pinyin = PinyinData.get(ch);
            for (String s : pinyin) patterns.add(PinyinPattern.get(s));
            p.patterns = patterns.toArray(p.patterns);
            return p;
        }

        public static void refresh() {
            PinyinPattern.refresh();
        }

        @Override
        public IndexSet match(String str, int start) {
            IndexSet ret = new IndexSet();
            for (Matchable p : patterns)
                ret.merge(p.match(str, start));
            return ret;
        }

        private static class InstanceRaw implements Matchable {
            private char ch;

            InstanceRaw(char ch) {
                this.ch = ch;
            }

            @Override
            public IndexSet match(String str, int start) {
                return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE;
            }
        }
    }
}
