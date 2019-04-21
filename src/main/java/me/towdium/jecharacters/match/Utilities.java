package me.towdium.jecharacters.match;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class Utilities {
    public static boolean isChinese(char i) {
        return 0x3007 <= i && i < 0x9FA5;
    }

    public static int strCmp(String a, String b, int aStart) {
        int len = Math.min(a.length() - aStart, b.length());
        for (int i = 0; i < len; i++)
            if (a.charAt(i + aStart) != b.charAt(i)) return i;
        return len;
    }
}
