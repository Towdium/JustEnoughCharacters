package me.towdium.jecharacters.match;


import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import me.towdium.jecharacters.match.matchables.Char;
import me.towdium.jecharacters.match.matchables.Pinyin;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class PinyinTree {
    Node root = new NSlice();

    public void put(String name, int identifier) {
        for (int i = 0; i < name.length(); i++) {
            root = root.put(name, identifier, i);
        }
    }

    public IntSet search(String s) {
        IntSet ret = new IntOpenHashSet();
        root.get(ret, s, 0);
        return ret;
    }

    public int countSlice() {
        return root.countSlice();
    }

    public int countMap() {
        return root.countMap();
    }

    interface Node {
        void get(IntSet ret, String name, int offset);

        void get(IntSet ret);

        Node put(String name, int identifier, int offset);

        int countSlice();

        int countMap();
    }

    public static class NSlice implements Node {
        Node exit = new NMap();
        String name;
        int start, end;

        @Override
        public void get(IntSet ret, String name, int offset) {
            get(ret, name, offset, 0);
        }

        @Override
        public void get(IntSet ret) {
            exit.get(ret);
        }

        @Override
        public Node put(String name, int identifier, int offset) {
            if (this.name == null) {
                this.name = name;
                start = offset;
                end = name.length();
                exit = exit.put(name, identifier, end);
            } else {
                int length = end - start;
                int match = Utilities.strCmp(this.name, name, start, offset, length);
                if (match >= length) exit = exit.put(name, identifier, offset + length);
                else {
                    cut(start + match);
                    exit.put(name, identifier, offset + match);
                }
            }
            return start == end ? exit : this;
        }

        @Override
        public int countSlice() {
            return 1 + exit.countSlice();
        }

        @Override
        public int countMap() {
            return exit.countMap();
        }

        private void cut(int offset) {
            NMap insert = new NMap();
            if (offset + 1 == end) insert.put(name.charAt(offset), exit);
            else {
                NSlice half = new NSlice();
                half.name = this.name;
                half.start = offset + 1;
                half.end = end;
                half.exit = exit;
                insert.put(name.charAt(offset), half);
            }
            exit = insert;
            end = offset;
        }

        private void get(IntSet ret, String name, int offset, int start) {
            if (this.start + start == end) exit.get(ret, name, offset);
            else if (offset == name.length()) exit.get(ret);
            else {
                char ch = this.name.charAt(this.start + start);
                Char.get(ch).match(name, offset).foreach(i -> {
                    get(ret, name, offset + i, start + 1);
                    return true;
                });
            }
        }
    }

    public static class NMap implements Node {
        Map<Character, Node> exact = new Char2ObjectOpenHashMap<>();
        Map<Pinyin, List<Character>> pinyin = new Object2ObjectArrayMap<>();
        IntSet leaves = new IntArraySet();

        @Override
        public void get(IntSet ret, String name, int offset) {
            if (name.length() == offset) get(ret);
            else {
                Map<Node, IntSet> m = new IdentityHashMap<>();
                Node ch = exact.get(name.charAt(offset));
                if (ch != null) m.computeIfAbsent(ch, k -> new IntOpenHashSet()).add(1);

                pinyin.forEach((p, cs) -> p.match(name, offset).foreach(i -> {
                    for (Character c : cs) m.computeIfAbsent(exact.get(c), k -> new IntOpenHashSet()).add(i);
                    return true;
                }));
                m.forEach((n, is) -> {
                    for (int i : is) n.get(ret, name, offset + i);
                });
            }
        }

        @Override
        public void get(IntSet ret) {
            ret.addAll(leaves);
            exact.forEach((p, n) -> n.get(ret));
        }

        @Override
        public NMap put(String name, int identifier, int offset) {
            if (offset == name.length()) {
                if (leaves.size() >= 16) leaves = new IntOpenHashSet(leaves);
                leaves.add(identifier);
            } else {
                char ch = name.charAt(offset);
                Node sub = exact.get(ch);
                if (sub == null) put(ch, sub = new NSlice());
                sub = sub.put(name, identifier, offset + 1);
                exact.put(ch, sub);
            }
            return this;
        }

        @Override
        public int countSlice() {
            int ret = 0;
            for (Node n : exact.values()) ret += n.countSlice();
            return ret;
        }

        @Override
        public int countMap() {
            int ret = 1;
            for (Node n : exact.values()) ret += n.countMap();
            return ret;
        }

        private void put(char ch, Node n) {
            exact.put(ch, n);
            if (Utilities.isChinese(ch)) {
                for (String str : PinyinData.get(ch))
                    pinyin.computeIfAbsent(Pinyin.get(str), i -> new CharArrayList()).add(ch);
            }
        }
    }
}
