package towdium.je_characters.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import towdium.je_characters.core.JechCore;

import java.util.ArrayList;
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
    static boolean b;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    private static boolean containsChinese(CharSequence s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (isCharacter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static Matcher checkReg(Pattern test, CharSequence name) {
        if (containsChinese(name)) {
            if (JechCore.DEBUG)
                JechCore.LOG.info("RegExp:");
            return checkChinese(name.toString(), test.toString()) ? p.matcher("a") : p.matcher("");
        } else
            return test.matcher(name);
    }

    // s1.contains(s2)
    public static boolean checkStr(String s1, CharSequence s2) {
        if (containsChinese(s1)) {
            if (JechCore.DEBUG)
                JechCore.LOG.info("String:");
            return checkChinese(s1, s2.toString());
        } else
            return s1.contains(s2);
    }

    private static boolean isCharacter(int i) {
        return 0x3007 <= i && i < 0x9FA5;
    }

    private static boolean checkChinese(String s1, CharSequence s2) {
        boolean b;
        if (s2 instanceof String && containsChinese(s1)) {
            if (s2.toString().isEmpty()) {
                b = true;
            } else {
                b = false;
                for (int i = 0; i < s1.length(); i++) {
                    if (checkChinese(s2.toString(), 0, s1, i))
                        b = true;
                }
            }
        } else {
            b = s1.contains(s2);
        }

        if (JechCore.DEBUG) {
            JechCore.LOG.info("s1: " + s1 + ", s2: " + s2 + ", -> " + b + '.');
        }

        return b;
    }

    private static boolean checkChinese(String s1, int start1, String s2, int start2) {
        b = false;

        if (start1 == s1.length()) {
            return true;
        }

        CharRep r = CharRep.get(s2.charAt(start2));
        IndexSet s = r.match(s1, start1);

        if (start2 == s2.length() - 1) {
            int i = s1.length() - start1;
            s.foreach(j -> {
                if (i == j) {
                    b = true;
                    return false;
                } else {
                    return true;
                }
            });
            return b;
        }

        s.foreach(i -> {
            if (checkChinese(s1, start1 + i, s2, start2 + 1)) {
                b = true;
                return false;
            } else {
                return true;
            }
        });
        return b;
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
        static final CharRepSin INSTANCE = new CharRepSin();
        Character ch;

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

    public static class CharRepMul implements CharRep {
        static final CharRepMul[] CACHE = new CharRepMul[41000];
        static final int START = 0x3007;
        static final int END = 0x9FA5;

        static {
            for (int i = START; i <= END; i++) {
                CACHE[i] = genRep((char) i);
            }
        }

        ArrayList<CharPattern> patterns = new ArrayList<>();

        private CharRepMul() {
        }

        private static CharRepMul get(Character ch) {
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

            for (String s : pinyin) {
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

    public static class PinyinPattern implements CharPattern {
        static LoadingCache<String, PinyinPattern> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
                .maximumWeight(16).weigher((Weigher<String, PinyinPattern>) (key, value) -> 1)
                .build(new CacheLoader<String, PinyinPattern>() {
                    @Override
                    public PinyinPattern load(String str) {
                        return genPattern(str);
                    }
                });
        String str;
        TIntSet slices = new TIntHashSet(5);

        private PinyinPattern() {
        }

        private static PinyinPattern get(String str) {
            return cache.getUnchecked(str);
        }

        private static PinyinPattern genPattern(String str) {
            PinyinPattern p = new PinyinPattern();
            p.str = str;
            if (str.isEmpty())
                return p;
            else
                p.slices.add(1);

            if (str.length() > 1 && str.charAt(1) == 'h')
                p.slices.add(2);

            p.slices.add(str.length());
            return p;
        }

        @Override
        public IndexSet match(String str, int start) {
            int match = strCmp(str, this.str, start);
            IndexSet ret = new IndexSet();

            slices.forEach(value -> {
                if (value <= match)
                    ret.set(value);
                return true;
            });

            if (match == str.length() - start)
                ret.set(match);

            return ret;
        }
    }

    private static class RawPattern implements CharPattern {
        Character ch;

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
            int i = 0x1 << index;
            this.value |= i;
        }

        private void merge(IndexSet s) {
            value |= s.value;
        }

        private void foreach(Predicate<Integer> p) {
            int v = value >> 1;
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
