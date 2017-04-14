package towdium.je_characters;

import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectByteMap;
import gnu.trove.map.hash.TCharObjectHashMap;
import gnu.trove.map.hash.TObjectByteHashMap;
import towdium.je_characters.jei.TransformHelper;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static towdium.je_characters.CheckCore.*;

/**
 * Author: Towdium
 * Date:   17-4-14.
 */
public class CheckHelper {
    static final byte TRUE = (byte) (Constants.DEFAULT_BYTE_NO_ENTRY_VALUE + 1);
    static final byte FALSE = (byte) (Constants.DEFAULT_BYTE_NO_ENTRY_VALUE + 2);

    static Cache standard = new Cache();
    static Cache cache = standard;
    static ArrayList<String> base = new ArrayList<>();
    public static Consumer<String> addBase = s -> base.add(s);

    public static Matcher checkReg(Pattern test, CharSequence name) {
        if (containsChinese(name))
            return checkStr(name.toString(), test.toString()) ? p.matcher("a") : p.matcher("");
        else
            return test.matcher(name);
    }

    public static void foreachChar(Consumer<Character> func) {
        for (char c = 'a'; c <= 'z'; c++) {
            func.accept(c);
        }
    }

    // s1.contains(s2)
    public static boolean checkStr(String s1, CharSequence s2) {
        if (containsChinese(s1) && s2 instanceof String) {
            if (containsChinese(s2)) {
                if (isPureChinese(s2)) {
                    return s1.contains(s2);
                } else {
                    return checkChinese(s1, s2.toString());
                }
            } else {
                return cache.check(s1, s2.toString());
            }
        } else
            return s1.contains(s2);
    }

    public static void buildingMode(boolean b) {
        if (b) {
            cache = standard;
        } else {
            cache = new Cache(standard);
        }
    }

    // s2 as tree, s1 as value
    static class Cache {

        Entry root;
        int count = 0;

        public Cache() {
            root = new Entry(0, null);
        }

        public Cache(Cache c) {
            root = new Entry(c.root, null);
        }

        public boolean check(String s1, String s2) {
            clean();
            return root.check(s1, s2.toLowerCase());
        }

        public void clean() {
            count++;
            if (count % 2048 == 1024) {
                ArrayList<Character> toRemove = new ArrayList<>();

                root.subEntries.forEachEntry((ch, entry) -> {
                    if (TransformHelper.withJei) {
                        Entry inStandard = standard.root.subEntries.get(ch);

                        if (entry.cached.size() > (inStandard == null ? 0 : inStandard.cached.size() + 100) *
                                JECConfig.EnumItems.IntCleanThreshold.getProperty().getInt()) {
                            toRemove.add(ch);
                        }
                        return true;
                    } else {
                        if (entry.cached.size() > 30000)
                            toRemove.add(ch);
                        return true;
                    }
                });

                toRemove.forEach(character -> {
                    Entry inStandard = TransformHelper.withJei ? standard.root.subEntries.get(character) : null;
                    if (inStandard == null) {
                        root.subEntries.remove(character);
                    } else {
                        LoadingPlugin.log.info("Cleaning cache of " + character + ".");
                        root.subEntries.put(character, new Entry(inStandard, root));
                    }
                });
            }
        }

        static class Entry {
            int level;
            int count = 0;
            Entry parent;
            TCharObjectHashMap<Entry> subEntries;
            TObjectByteMap<String> cached;

            public Entry(Entry e, Entry parent) {
                this.level = e.level;
                this.parent = parent;
                subEntries = new TCharObjectHashMap<>(60);
                cached = new TObjectByteHashMap<>(e.cached.size());
                cached.putAll(e.cached);
                e.subEntries.forEachEntry((ch, entry) -> {
                    subEntries.put(ch, new Entry(entry, this));
                    return true;
                });
            }

            public Entry(int level, Entry parent) {
                this.level = level;
                this.parent = parent;
                subEntries = new TCharObjectHashMap<>(60);
                int len = 1024 >> level;
                cached = new TObjectByteHashMap<>(len > Constants.DEFAULT_CAPACITY ? len : Constants.DEFAULT_CAPACITY);
            }

            // s1 chinese strings only, s2 english only
            public boolean check(String s1, String s2) {
                ++count;
                if (s2.length() == 0) {
                    return true;
                } else if (s2.length() == level) {
                    byte b = cached.get(s1);
                    if (b == TRUE)
                        return true;
                    else if (b == FALSE)
                        return false;
                    else {
                        if (parent.fastCheck(s1) && checkChinese(s1, s2)) {
                            cached.put(s1, TRUE);
                            return true;
                        } else {
                            cached.put(s1, FALSE);
                            return false;
                        }
                    }
                } else {
                    Entry entry = subEntries.get(s2.charAt(level));
                    if (entry == null) {
                        entry = new Entry(level + 1, this);
                        subEntries.put(s2.charAt(level), entry);
                    }
                    return entry.check(s1, s2);
                }
            }

            private boolean fastCheck(String s) {
                return level == 0 || cached.containsKey(s);
            }
        }
    }
}
