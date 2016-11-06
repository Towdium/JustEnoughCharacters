package towdium.je_characters;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.HashSet;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class CheckHelper {
    static final HanyuPinyinOutputFormat FORMAT;
    static final int[] ZERO = new int[] {0};
    static final int[] ONE = new int[] {1};

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    public static boolean check(String s1, String s2) {
        for(int i = s1.length() - 1; i >= 0; i--) {
            if(isCharacter(s1.charAt(i))) {
                return checkChinese(s1, s2);
            }
        }
        return s1.contains(s2);
    }

    public static boolean checkChinese(String s1, String s2) {
        Object[] format = getFormat(s1);
        for(int i = 0; i < format.length; i++) {
            if(checkChinese(format, s2, i, 0)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkChinese(Object[] format, String str, int indexFormat, int indexStr) {
        if (indexStr == str.length()) {
            return true;
        }
        if (indexFormat == format.length) {
            return false;
        }
        int[] result = checkRepresentation(format[indexFormat], str, indexStr);
        for(int i : result) {
            if (i != 0 && checkChinese(format, str, indexFormat + 1, indexStr + i)) {
                return true;
            }
        }
        return false;
    }

    public static int[] checkRepresentation(Object rep, String str, int index) {
        if(rep instanceof Integer) {
            return str.charAt(index) == ((Integer) rep) ? ONE : ZERO;
        } else if (rep instanceof Object[]) {
            Object[] strs = (Object[]) rep;
            HashSet<Integer> ret = new HashSet<Integer>();
            int extra = str.length() - index;
            for (Object obj : strs) {
                String astr = (String) obj;
                if(astr.length() >= extra && astr.startsWith(str.substring(index))) {
                    return new int[] {extra};
                }
                if(str.startsWith(astr, index)) {
                    ret.add(astr.length());
                }
            }
            return ret.isEmpty() ? ZERO : toArray(ret.toArray());
        } else {
            return ZERO;
        }
    }

    public static int[] toArray(Object[] objs) {
        int[] ret = new int[objs.length];
        int count = -1;
        for(Object o : objs) {
            if(o instanceof Integer) {
                ret[++count] = ((Integer) o);
            }
        }
        return ret;
    }

    public static boolean isCharacter(int i) {
        return 19968 <= i && i <40623;
    }

    public static Object[] getFormat(String s) {
        int len = s.length();
        Object[] ret = new Object[len];
        for(int a = 0; a < len; a++) {
            int ch = s.charAt(a);
            ret[a] = getCharRepresentation(ch);
        }
        return ret;
    }

    public static Object getCharRepresentation(int ch) {
        if (isCharacter(ch)) {
            String[] pinyin;
            try {
                pinyin = PinyinHelper.toHanyuPinyinStringArray((char)ch, FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination badHanyuPinyinOutputFormatCombination) {
                badHanyuPinyinOutputFormatCombination.printStackTrace();
                return ch;
            }
            HashSet<String> ret = new HashSet<String>();
            if (pinyin == null) return ch;
            for(String s : pinyin) {
                ret.add(getConsonant(s));
                //ret.add(s.substring(0,1));
                ret.add(s);
            }
            return ret.toArray();

        } else {
            return ch;
        }
    }

    public static String getConsonant(String s) {
        if(s.length() >= 2 && s.charAt(1) == 'h') {
            return s.substring(0, 2);
        } else if (s.length() > 1) {
            return s.substring(0, 1);
        } else {
            return s;
        }
    }
}
