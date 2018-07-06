package me.towdium.jecharacters.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.core.JechCore;

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

    public static void refresh() {
        CharacterMul.refresh();
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
        return b;
    }

    private static boolean checkChinese(String s1, int start1, String s2, int start2) {
        if (start1 == s1.length()) return true;

        Character r = Character.get(s2.charAt(start2));
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

    private interface Character {
        static Character get(char ch) {
            if (isCharacter(ch)) return CharacterMul.get(ch);
            else return CharacterSin.get(ch);
        }

        IndexSet match(String str, int start);
    }

    private static class CharacterSin implements Character {
        private static final CharacterSin INSTANCE = new CharacterSin();
        private char ch;

        private CharacterSin() {
        }

        private static CharacterSin get(char ch) {
            INSTANCE.ch = ch;
            return INSTANCE;
        }

        @Override
        public IndexSet match(String str, int start) {
            return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE;
        }
    }

    private static class CharacterMul implements Character {
        private static final CharacterMul[] CACHE = new CharacterMul[41000];
        private static final int START = 0x3007;
        private static final int END = 0x9FA5;

        static {
            for (int i = START; i <= END; i++) {
                CACHE[i] = genRep((char) i);
            }
        }

        private Instance[] patterns = new Instance[0];

        private CharacterMul() {
        }

        static CharacterMul get(char ch) {
            return CACHE[ch];
        }

        private static CharacterMul genRep(char ch) {
            CharacterMul p = new CharacterMul();
            ArrayList<Instance> patterns = new ArrayList<>();
            patterns.add(new InstanceRaw(ch));
            p.patterns = patterns.toArray(p.patterns);
            String[] pinyin = PinyinData.get(ch);
            for (String s : pinyin) patterns.add(InstancePinyin.get(s));
            p.patterns = patterns.toArray(p.patterns);
            return p;
        }

        public static void refresh() {
            InstancePinyin.refresh();
        }

        @Override
        public IndexSet match(String str, int start) {
            IndexSet ret = new IndexSet();
            for (Instance p : patterns)
                ret.merge(p.match(str, start));
            return ret;
        }

        private interface Instance {
            IndexSet match(String str, int start);
        }

        private static class InstanceRaw implements Instance {
            private char ch;

            InstanceRaw(char ch) {
                this.ch = ch;
            }

            @Override
            public IndexSet match(String str, int start) {
                return str.charAt(start) == ch ? IndexSet.ONE : IndexSet.NONE;
            }
        }

        private static class InstancePinyin implements Instance {
            private static LoadingCache<String, InstancePinyin> cache = CacheBuilder.newBuilder().concurrencyLevel(1)
                    .build(new CacheLoader<String, InstancePinyin>() {
                        @Override
                        @ParametersAreNonnullByDefault
                        public InstancePinyin load(String str) {
                            return new InstancePinyin(str);
                        }
                    });

            private Phoneme initial;
            private Phoneme finale;
            private Phoneme tone;

            public InstancePinyin(String str) {
                set(str);
            }

            static InstancePinyin get(String str) {
                return cache.getUnchecked(str);
            }

            public static void refresh() {
                Phoneme.refresh();
                cache.asMap().forEach((s, p) -> p.set(s));
            }

            private void set(String str) {
                String[] elements = JechConfig.keyboard.separate(str);
                initial = Phoneme.get(elements[0]);
                finale = Phoneme.get(elements[1]);
                tone = Phoneme.get(elements[2]);
            }

            @Override
            public IndexSet match(String str, int start) {
                IndexSet ret = new IndexSet(0x1);
                ret = initial.match(str, ret, start);
                ret.merge(finale.match(str, ret, start));
                ret.merge(tone.match(str, ret, start));
                return ret;
            }

            private static class Phoneme {
                private static LoadingCache<String, Phoneme> cache = buildCache();

                String[] strs;

                public Phoneme(String str) {
                    HashSet<String> ret = new HashSet<>();
                    ret.add(str);

                    if (JechConfig.enableFuzzyCh2c && str.startsWith("c")) Collections.addAll(ret, "c", "ch");
                    if (JechConfig.enableFuzzySh2s && str.startsWith("s")) Collections.addAll(ret, "s", "sh");
                    if (JechConfig.enableFuzzyZh2z && str.startsWith("z")) Collections.addAll(ret, "z", "zh");
                    if (JechConfig.enableFuzzyU2v && str.startsWith("v"))
                        ret.add("u" + str.substring(1));
                    if ((JechConfig.enableFuzzyAng2an && str.endsWith("ang"))
                            || (JechConfig.enableFuzzyEng2en && str.endsWith("eng"))
                            || (JechConfig.enableFuzzyIng2in && str.endsWith("ing")))
                        ret.add(str.substring(0, str.length() - 1));
                    if ((JechConfig.enableFuzzyAng2an && str.endsWith("an"))
                            || (str.endsWith("en") && JechConfig.enableFuzzyEng2en)
                            || (str.endsWith("in") && JechConfig.enableFuzzyIng2in))
                        ret.add(str + 'g');
                    strs = ret.stream().map(JechConfig.keyboard::keys).toArray(String[]::new);
                }

                public static Phoneme get(String str) {
                    return cache.getUnchecked(str);
                }

                public static void refresh() {
                    cache = buildCache();
                }

                private static LoadingCache<String, Phoneme> buildCache() {
                    return CacheBuilder.newBuilder().concurrencyLevel(1)
                            .build(new CacheLoader<String, Phoneme>() {
                                @Override
                                @ParametersAreNonnullByDefault
                                public Phoneme load(String str) {
                                    return new Phoneme(str);
                                }
                            });
                }

                IndexSet match(String source, IndexSet idx, int start) {
                    if (strs.length == 1 && strs[0].isEmpty()) return new IndexSet(idx.value);
                    else {
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
