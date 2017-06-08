package towdium.je_characters;

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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class CheckHelper {
    static final HanyuPinyinOutputFormat FORMAT;
    static final Pattern p = Pattern.compile("a");
    static WrapBoolean b = new WrapBoolean(false);

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    public static boolean containsChinese(CharSequence s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (isCharacter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static Matcher checkReg(Pattern test, CharSequence name) {
        if (containsChinese(name))
            return checkChinese(name.toString(), test.toString()) ? p.matcher("a") : p.matcher("");
        else
            return test.matcher(name);
    }

    // s1.contains(s2)
    public static boolean checkStr(String s1, CharSequence s2) {
        if (containsChinese(s1))
            return checkChinese(s1, s2.toString());
        else
            return s1.contains(s2);
    }

    public static boolean isCharacter(int i) {
        return 0x3007 <= i && i < 0x9FA5;
    }

    public static boolean checkChinese(String s1, CharSequence s2) {
        if (s2 instanceof String && containsChinese(s1)) {
            if (s2.toString().isEmpty())
                return true;

            for (int i = 0; i < s1.length(); i++) {
                if (checkChinese(s2.toString(), 0, s1, i))
                    return true;
            }
            return false;
        } else {
            return s1.contains(s2);
        }
    }

    public static boolean checkChinese(String s1, int start1, String s2, int start2) {
        if (start1 == s1.length()) {
            return true;
        }

        CharRep r = CharRep.get(s2.charAt(start2));
        TIntSet s = r.match(s1, start1);


        if (start2 == s2.length() - 1) {
            b.b = false;
            int i = s1.length() - start1;
            s.forEach(j -> {
                if (i == j) {
                    b.b = true;
                    return false;
                } else {
                    return true;
                }
            });
            return b.b;
        }

        b.b = false;
        s.forEach(i -> {
            if (checkChinese(s1, start1 + i, s2, start2 + 1)) {
                b.b = true;
                return false;
            } else {
                return true;
            }
        });
        return b.b;
    }

    private static int strCmp(String a, String b, int aStart) {
        int len = Math.min(a.length() - aStart, b.length());
        for (int i = 0; i < len; i++) {
            if (a.charAt(i + aStart) != b.charAt(i))
                return i;
        }
        return len;
    }

    public interface CharPattern {

        TIntSet match(String str, int start);

        class Constants {
            static final TIntSet ZERO = new TIntHashSet();
            static final TIntSet ONE = new TIntHashSet();

            static {
                ONE.add(1);
            }
        }
    }

    static class WrapBoolean {
        public boolean b;

        public WrapBoolean(boolean b) {
            this.b = b;
        }
    }

    public static class CharRep {
        static LoadingCache<Character, CharRep> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
                .maximumSize(20000).build(new CacheLoader<Character, CharRep>() {
                    @Override
                    public CharRep load(@NotNull Character ch) {
                        return genRep(ch);
                    }
                });
        ArrayList<CharPattern> patterns = new ArrayList<>();

        private CharRep() {
        }

        public static CharRep get(Character ch) {
            return cache.getUnchecked(ch);
        }

        private static CharRep genRep(Character ch) {
            CharRep p = new CharRep();
            p.patterns.add(new RawPattern(ch));
            if (isCharacter(ch)) {
                String[] pinyin;
                try {
                    pinyin = PinyinHelper.toHanyuPinyinStringArray(ch, FORMAT);
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    LoadingPlugin.log.warn("Exception when generating pattern for \"" + ch + "\"");
                    return p;
                }

                if (pinyin == null)
                    return p;

                for (String s : pinyin) {
                    if (s != null)
                        p.patterns.add(PinyinPattern.get(s));
                }
            }
            return p;
        }

        public TIntSet match(String str, int start) {
            TIntSet ret = new TIntHashSet();  // TODO
            patterns.forEach(pat -> ret.addAll(pat.match(str, start)));
            return ret;
        }
    }

    public static class PinyinPattern implements CharPattern {
        static LoadingCache<String, PinyinPattern> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
                .maximumWeight(16).weigher((Weigher<String, PinyinPattern>) (key, value) -> 1)
                .build(new CacheLoader<String, PinyinPattern>() {
                    @Override
                    public PinyinPattern load(@NotNull String str) {
                        return genPattern(str);
                    }
                });
        String str;
        TIntSet slices = new TIntHashSet(5);

        private PinyinPattern() {
        }

        public static PinyinPattern get(String str) {
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
        public TIntSet match(String str, int start) {
            int match = strCmp(str, this.str, start);
            TIntSet ret = new TIntHashSet(5); // TODO

            slices.forEach(value -> {
                if (value <= match)
                    ret.add(value);
                return true;
            });

            if (match == str.length() - start)
                ret.add(match);

            return ret;
        }
    }

    public static class RawPattern implements CharPattern {
        Character ch;

        public RawPattern(Character ch) {
            this.ch = ch;
        }

        @Override
        public TIntSet match(String str, int start) {
            return str.charAt(start) == ch ? Constants.ONE : Constants.ZERO;
        }
    }
}
