package towdium.je_characters;

import gnu.trove.impl.Constants;
import gnu.trove.map.TCharObjectMap;
import gnu.trove.map.TObjectByteMap;
import gnu.trove.map.hash.TCharObjectHashMap;
import gnu.trove.map.hash.TObjectByteHashMap;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static towdium.je_characters.CheckCore.*;

/**
 * Author: Towdium
 * Date:   17-4-14.
 */
public class CheckHelper {
    static Cache cache = new Cache();
    static final byte TRUE = (byte) (Constants.DEFAULT_BYTE_NO_ENTRY_VALUE + 1);
    static final byte FALSE = (byte) (Constants.DEFAULT_BYTE_NO_ENTRY_VALUE + 2);

    public static Matcher checkReg(Pattern test, CharSequence name) {
        if (containsChinese(name))
            return cache.check(name.toString(), test.toString()) ? p.matcher("a") : p.matcher("");
        else
            return test.matcher(name);
    }

    // s1.contains(s2)
    public static boolean checkStr(String s1, CharSequence s2) {
        if (containsChinese(s1) && s2 instanceof String)
            return cache.check(s1, s2.toString());
        else
            return s1.contains(s2);
    }

    // s2 as tree, s1 as value
    static class Cache {
        Entry root = new Entry(0, null);

        public boolean check(String s1, String s2) {
            return root.check(s1, s2);
        }

        static class Entry {
            int level;
            Entry parent;
            TCharObjectHashMap<Entry> subEntries;
            TObjectByteMap<String> cached;

            public Entry(int level, Entry parent) {
                this.level = level;
                this.parent = parent;
                subEntries = new TCharObjectHashMap<>(30);
                int len = 1024 >> level;
                cached = new TObjectByteHashMap<>(len > Constants.DEFAULT_CAPACITY ? len : Constants.DEFAULT_CAPACITY);
            }

            // chinese strings only
            public boolean check(String s1, String s2) {
                if (s2.length() == level) {
                    byte b = cached.get(s1);
                    if (b == TRUE)
                        return true;
                    else if (b == FALSE)
                        return false;
                    else {
                        if (level == 0 || (parent.check(s1, s2.substring(0, s2.length()-1)) && checkChinese(s1, s2))) {
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
        }
    }
}
