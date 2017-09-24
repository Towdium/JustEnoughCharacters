package me.towdium.jecharacters.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

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
    static boolean sharedBoolean;

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
        }
        else
            return test.matcher(name);
    }

    // s1.contains(s2)
    @SuppressWarnings("unused")
    public static boolean checkStr(String s1, CharSequence s2) {
        boolean ret;

        if (containsChinese(s1)) ret = checkChinese(s1, s2.toString());
        else ret = s1.contains(s2);

        if (verbose) JechCore.LOG.info("Full: " + s1 + ", Test: " + s2.toString() + ", -> " + sharedBoolean + '.');

        return ret;
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
        sharedBoolean = false;

        if (start1 == s1.length()) {
            return true;
        }

        CharRep r = CharRep.get(s2.charAt(start2));
        IndexSet s = r.match(s1, start1);

        if (start2 == s2.length() - 1) {
            int i = s1.length() - start1;
            s.foreach(j -> {
                if (i == j) {
                    sharedBoolean = true;
                    return false;
                } else {
                    return true;
                }
            });
            return sharedBoolean;
        } else {
            s.foreach(i -> {
                if (checkChinese(s1, start1 + i, s2, start2 + 1)) {
                    sharedBoolean = true;
                    return false;
                } else {
                    return true;
                }
            });
            return sharedBoolean;
        }
    }

    private static int strCmp(String a, String b, int aStart) {
        int len = Math.min(a.length() - aStart, b.length());
        for (int i = 0; i < len; i++) {
            if (a.charAt(i + aStart) != b.charAt(i))
                return i;
        }
        return len;
    }

    private interface CharPattern {
        IndexSet match(String str, int start);
    }

    private interface CharRep {
        static CharRep get(Character ch) {
            if (isCharacter(ch)) {
                return CharRepMul.get(ch);
            } else {
                return CharRepSin.get(ch);
            }
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
            return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.ZERO;
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

        private ArrayList<CharPattern> patterns = new ArrayList<>();

        private CharRepMul() {
        }

        static CharRepMul get(Character ch) {
            return CACHE[ch];
        }

        private static CharRepMul genRep(Character ch) {
            CharRepMul p = new CharRepMul();
            p.patterns.add(new RawPattern(ch));
            String[] pinyin;
            try {
                pinyin = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                JechCore.LOG.warn("Exception when generating pattern for \"" + ch + "\"");
                return p;
            }

            if (pinyin == null)
                return p;

            HashSet<String> set = new HashSet<>();
            Collections.addAll(set, pinyin);

            for (String s : set) {
                if (s != null)
                    p.patterns.add(PinyinPattern.get(s));
            }
            return p;
        }

        @Override
        public IndexSet match(String str, int start) {
            IndexSet ret = new IndexSet();
            patterns.forEach(pat -> ret.merge(pat.match(str, start)));
            return ret;
        }
    }

    private static class PinyinPattern implements CharPattern {
        private static LoadingCache<String, PinyinPattern> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
                .maximumWeight(16).weigher((Weigher<String, PinyinPattern>) (key, value) -> 1)
                .build(new CacheLoader<String, PinyinPattern>() {
                    @Override
                    public PinyinPattern load(String str) {
                        return new PinyinPattern(str);
                    }
                });
        private FuzzyMatcher fInitial;
        private FuzzyMatcher fFinal;

        private PinyinPattern(String str) {
            int size = str.length() >= 2 && str.charAt(1) == 'h' ? 2 : 1;

            fInitial = FuzzyMatcher.get(str.substring(0, size));
            fFinal = FuzzyMatcher.get(str.substring(size));
        }

        static PinyinPattern get(String str) {
            return cache.getUnchecked(str);
        }

        @Override
        public IndexSet match(String str, int start) {
            IndexSet ret = fInitial.match(str, start);
            new IndexSet(ret.value).foreach(i -> {

                fFinal.match(str, start + i).foreach(j -> {
                    ret.set(i + j);
                    return true;
                });
                return true;
            });
            return ret;
        }

        private static class FuzzyMatcher {
            private static boolean zh2z = JechConfig.EnumItems.EnableFuzzyInitialZhToZ.getProperty().getBoolean();
            private static boolean sh2s = JechConfig.EnumItems.EnableFuzzyInitialShToS.getProperty().getBoolean();
            private static boolean ch2c = JechConfig.EnumItems.EnableFuzzyInitialChToC.getProperty().getBoolean();
            private static boolean ing2in = JechConfig.EnumItems.EnableFuzzyFinalIngToIn.getProperty().getBoolean();
            private static boolean ang2an = JechConfig.EnumItems.EnableFuzzyFinalAngToAn.getProperty().getBoolean();
            private static boolean eng2en = JechConfig.EnumItems.EnableFuzzyFinalEngToEn.getProperty().getBoolean();
            private static boolean v2u = JechConfig.EnumItems.EnableFuzzyFinalUToV.getProperty().getBoolean();

            private static LoadingCache<String, FuzzyMatcher> cache =
                    CacheBuilder.newBuilder().concurrencyLevel(1).maximumWeight(16)
                            .weigher((Weigher<String, FuzzyMatcher>) (key, value) -> 1)
                            .build(new CacheLoader<String, FuzzyMatcher>() {
                                @Override
                                public FuzzyMatcher load(String str) {
                                    return new FuzzyMatcher(str);
                                }
                            });

            private HashSet<String> set = new HashSet<>();

            private FuzzyMatcher(String s) {
                set.add(s);

                if (ch2c && s.startsWith("c")) Collections.addAll(set, "c", "ch");
                if (sh2s && s.startsWith("s")) Collections.addAll(set, "s", "sh");
                if (zh2z && s.startsWith("z")) Collections.addAll(set, "z", "zh");
                if (v2u && s.startsWith("v"))
                    set.add("u" + s.substring(1));
                if ((ang2an && s.endsWith("ang"))
                        || (eng2en && s.endsWith("eng"))
                        || (ing2in && s.endsWith("ing")))
                    set.add(s.substring(0, s.length() - 1));
                if ((ang2an && s.endsWith("an"))
                        || (s.endsWith("en") && eng2en)
                        || (s.endsWith("in") && ing2in))
                    set.add(s + 'g');
            }

            static FuzzyMatcher get(String s) {
                return cache.getUnchecked(s);
            }

            IndexSet match(String s, int i) {
                IndexSet ret = new IndexSet();
                for (String str : set) {
                    int size = strCmp(s, str, i);
                    if (i + size == s.length())
                        ret.set(size);
                    if (size == str.length())
                        ret.set(size);
                }
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
            return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.ZERO;
        }
    }

    private static class IndexSet {
        static final IndexSet ONE = new IndexSet(0x1);
        static final IndexSet ZERO = new IndexSet(0x0);

        int value = 0x0;

        IndexSet() {
        }

        IndexSet(int value) {
            this.value = value;
        }

        private void set(int index) {
            int i = 0x1 << (index - 1);
            this.value |= i;
        }

        private void merge(IndexSet s) {
            value |= s.value;
        }

        private void foreach(Predicate<Integer> p) {
            int v = value;
            for (int i = 1; i < 8; i++) {
                if ((v & 0x1) == 0x1) {
                    if (!p.test(i))
                        break;
                }
                v >>= 1;
            }
        }
    }
}
