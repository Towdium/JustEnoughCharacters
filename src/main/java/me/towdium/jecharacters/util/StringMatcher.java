package me.towdium.jecharacters.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import me.towdium.jecharacters.core.JechCore;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class StringMatcher {
    static final HanyuPinyinOutputFormat FORMAT;
    static final Pattern p = Pattern.compile("a");
    public static boolean verbose = false;

    private static enumKeyboard keyboard = enumKeyboard.DAQIAN;
    private static boolean zh2z = false;
    private static boolean sh2s = false;
    private static boolean ch2c = false;
    private static boolean ing2in = false;
    private static boolean ang2an = false;
    private static boolean eng2en = false;
    private static boolean v2u = false;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

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
            if (testS.startsWith(".*") && testS.endsWith(".*"))
                testS = testS.substring(2, testS.length() - 2);
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

        if (verbose) JechCore.LOG.info("Full: " + s1 + ", Test: " + s2.toString() + ", -> " + ret + '.');

        return ret;
    }

    public static void setKeyboard(enumKeyboard keyboard) {
        StringMatcher.keyboard = keyboard;
    }

    public static void setZh2z(boolean zh2z) {
        StringMatcher.zh2z = zh2z;
    }

    public static void setSh2s(boolean sh2s) {
        StringMatcher.sh2s = sh2s;
    }

    public static void setCh2c(boolean ch2c) {
        StringMatcher.ch2c = ch2c;
    }

    public static void setIng2in(boolean ing2in) {
        StringMatcher.ing2in = ing2in;
    }

    public static void setAng2an(boolean ang2an) {
        StringMatcher.ang2an = ang2an;
    }

    public static void setEng2en(boolean eng2en) {
        StringMatcher.eng2en = eng2en;
    }

    public static void setV2u(boolean v2u) {
        StringMatcher.v2u = v2u;
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
        } else {
            b = s1.contains(s2);
        }

        return b;
    }

    private static boolean checkChinese(String s1, int start1, String s2, int start2) {
        if (start1 == s1.length()) return true;

        CharRep r = CharRep.get(s2.charAt(start2));
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

    private interface CharPattern {
        IndexSet match(String str, int start);
    }

    public enum enumKeyboard {
        QUANPIN, DAQIAN;

        String[] separate(String s) {
            if (this == DAQIAN) {
                String str = Mappings.PHONETIC_SPELL.get(s);
                if (str != null) s = str;
            }

            if (s.startsWith("a") || s.startsWith("e") || s.startsWith("i")
                    || s.startsWith("o") || s.startsWith("u")) {
                return new String[]{"", s, ""};
            } else {
                int i = s.length() > 2 && s.charAt(1) == 'h' ? 2 : 1;
                return new String[]{s.substring(0, i), s.substring(i), ""};
            }
        }

        String keys(String s) {
            if (this == QUANPIN) return s;
            else {
                String symbol = Mappings.PHONETIC_SYMBOL.get(s);
                if (symbol == null)
                    throw new RuntimeException("Unrecognized element: " + s);
                StringBuilder builder = new StringBuilder();
                for (char c : symbol.toCharArray()) builder.append(Mappings.KEYBOARD_DAQIAN.get(c));
                return builder.toString();
            }
        }
    }

    private interface CharRep {
        static CharRep get(Character ch) {
            if (isCharacter(ch)) return CharRepMul.get(ch);
            else return CharRepSin.get(ch);
        }

        IndexSet match(String str, int start);
    }

    private static class CharRepSin implements CharRep {
        private static final CharRepSin INSTANCE = new CharRepSin();
        private Character ch;

        private CharRepSin() {
        }

        private static CharRepSin get(Character ch) {
            INSTANCE.ch = ch;
            return INSTANCE;
        }

        @Override
        public IndexSet match(String str, int start) {
            return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE;
        }
    }

    private static class CharRepMul implements CharRep {
        private static final CharRepMul[] CACHE = new CharRepMul[41000];
        private static final int START = 0x3007;
        private static final int END = 0x9FA5;

        static {
            for (int i = START; i <= END; i++) {
                CACHE[i] = genRep((char) i);
            }
        }

        private CharPattern[] patterns = new CharPattern[0];

        private CharRepMul() {
        }

        static CharRepMul get(Character ch) {
            return CACHE[ch];
        }

        private static CharRepMul genRep(Character ch) {
            CharRepMul p = new CharRepMul();
            ArrayList<CharPattern> patterns = new ArrayList<>();
            patterns.add(new RawPattern(ch));
            p.patterns = patterns.toArray(p.patterns);
            String[] pinyin;
            try {
                pinyin = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                JechCore.LOG.warn("Exception when generating pattern for \"" + ch + "\"");
                return p;
            }
            if (pinyin == null) return p;

            HashSet<String> set = new HashSet<>();
            for (String s : pinyin)
                if (s != null) set.add(s);

            for (String s : set) {
                if (s != null)
                    patterns.add(PinyinPattern.get(s));
            }
            p.patterns = patterns.toArray(p.patterns);
            return p;
        }

        @Override
        public IndexSet match(String str, int start) {
            IndexSet ret = new IndexSet();
            for (CharPattern p : patterns)
                ret.merge(p.match(str, start));
            return ret;
        }
    }

    private static class PinyinPattern implements CharPattern {
        private static LoadingCache<String, PinyinPattern> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
                .build(new CacheLoader<String, PinyinPattern>() {
                    @Override
                    @ParametersAreNonnullByDefault
                    public PinyinPattern load(String str) {
                        return new PinyinPattern(str);
                    }
                });

        private ElementPattern initial;
        private ElementPattern finale;
        private ElementPattern tone;

        static PinyinPattern get(String str) {
            return cache.getUnchecked(str);
        }

        public PinyinPattern(String str) {
            String[] elements = keyboard.separate(str);
            initial = ElementPattern.get(elements[0]);
            finale = ElementPattern.get(elements[1]);
            tone = ElementPattern.get(elements[2]);
        }

        @Override
        public IndexSet match(String str, int start) {
            IndexSet ret = new IndexSet(0x1);
            ret = initial.match(str, ret, start);
            ret.merge(finale.match(str, ret, start));
            ret = tone.match(str, ret, start);
            return ret;
        }

        static private class ElementPattern {
            private static LoadingCache<String, ElementPattern> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
                    .build(new CacheLoader<String, ElementPattern>() {
                        @Override
                        @ParametersAreNonnullByDefault
                        public ElementPattern load(String str) {
                            return new ElementPattern(str);
                        }
                    });

            String[] strs;

            public ElementPattern(String s) {
                HashSet<String> ret = new HashSet<>();
                ret.add(s);

                if (ch2c && s.startsWith("c")) Collections.addAll(ret, "c", "ch");
                if (sh2s && s.startsWith("s")) Collections.addAll(ret, "s", "sh");
                if (zh2z && s.startsWith("z")) Collections.addAll(ret, "z", "zh");
                if (v2u && s.startsWith("v"))
                    ret.add("u" + s.substring(1));
                if ((ang2an && s.endsWith("ang"))
                        || (eng2en && s.endsWith("eng"))
                        || (ing2in && s.endsWith("ing")))
                    ret.add(s.substring(0, s.length() - 1));
                if ((ang2an && s.endsWith("an"))
                        || (s.endsWith("en") && eng2en)
                        || (s.endsWith("in") && ing2in))
                    ret.add(s + 'g');
                strs = ret.stream().map(keyboard::keys).toArray(String[]::new);
            }

            static ElementPattern get(String str) {
                return cache.getUnchecked(str);
            }

            IndexSet match(String source, IndexSet idx, int start) {
                IndexSet ret = new IndexSet();
                idx.foreach(i -> {
                    for (String str : strs) {
                        int size = strCmp(source, str, i + start);
                        if (i + start + size == source.length()) ret.set(i + size);  // ending match
                        else if (size == str.length()) ret.set(i + size);  // full match
                    }
                    return true;
                });
                return ret;
            }
        }
    }

    private static class RawPattern implements CharPattern {
        private Character ch;

        RawPattern(Character ch) {
            this.ch = ch;
        }

        @Override
        public IndexSet match(String str, int start) {
            return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE;
        }
    }

    private static class IndexSet {
        static final IndexSet ONE = new IndexSet(0x2);
        static final IndexSet NONE = new IndexSet(0x0);

        int value = 0x0;

        IndexSet() {
        }

        IndexSet(int value) {
            this.value = value;
        }

        public void set(int index) {
            int i = 0x1 << index;
            this.value |= i;
        }

        public boolean get(int index) {
            int i = 0x1 << index;
            return (value & i) != 0;
        }

        public void merge(IndexSet s) {
            value |= s.value;
        }

        public boolean foreach(Predicate<Integer> p) {
            int v = value;
            for (int i = 0; i < 7; i++) {
                if ((v & 0x1) == 0x1 && !p.test(i)) return false;
                v >>= 1;
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            foreach(i -> {
                builder.append(i);
                builder.append(", ");
                return true;
            });
            if (builder.length() != 0) {
                builder.delete(builder.length() - 2, builder.length());
                return builder.toString();
            } else return "0";
        }
    }
}
