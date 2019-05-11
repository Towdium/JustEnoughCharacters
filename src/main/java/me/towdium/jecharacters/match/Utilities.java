package me.towdium.jecharacters.match;

import me.towdium.jecharacters.match.matchables.Pinyin;

import java.util.function.Predicate;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class Utilities {

    public static boolean isChinese(CharSequence s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (isChinese(s.charAt(i))) return true;
        }
        return false;
    }

    public static boolean isChinese(char i) {
        return 0x3007 <= i && i < 0x9FA5;
    }

    public static int strCmp(String a, String b, int aStart) {
        return strCmp(a, b, aStart, 0, Integer.MAX_VALUE);
    }

    public static int strCmp(String a, String b, int aStart, int bStart, int max) {
        int len = Math.min(a.length() - aStart, b.length() - bStart);
        len = Math.min(len, max);
        for (int i = 0; i < len; i++)
            if (a.charAt(i + aStart) != b.charAt(i + bStart)) return i;
        return len;
    }

    public static void refresh() {
        Pinyin.refresh();
        PinyinTree.refresh();
    }

    public static class IndexSet {
        public static final IndexSet ONE = new IndexSet(0x2);
        public static final IndexSet NONE = new IndexSet(0x0);

        int value = 0x0;

        public IndexSet() {
        }

        public IndexSet(IndexSet set) {
            value = set.value;
        }

        public IndexSet(int value) {
            this.value = value;
        }

        public void set(int index) {
            int i = 0x1 << index;
            value |= i;
        }

        public boolean get(int index) {
            int i = 0x1 << index;
            return (value & i) != 0;
        }

        public void merge(IndexSet s) {
            value = value == 0x1 ? s.value : (value |= s.value);
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
