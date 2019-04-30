package me.towdium.jecharacters.match;

import io.netty.util.collection.CharObjectHashMap;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import me.towdium.jecharacters.match.matchables.Char;
import me.towdium.jecharacters.match.matchables.Pinyin;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static me.towdium.jecharacters.match.Utilities.isChinese;
import static me.towdium.jecharacters.match.Utilities.strCmp;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class PinyinTree {
    Node root = new NMap();

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
        void get(IntSet ret, String s, int index);

        void get(IntSet ret);

        Node put(String name, int id, int index);

        int countSlice();

        int countMap();
    }

    public static class NSlice implements Node {
        Node exit = new NMap();
        String name;
        int start, end;

        public void get(IntSet ret, String s, int index) {
            get(s, index, 0, ret);
        }

        @Override
        public void get(IntSet ret) {
            exit.get(ret);
        }

        protected void get(String s, int index, int start, IntSet ret) {
            if (this.start + start == end) exit.get(ret, s, index);
            else if (index == s.length()) exit.get(ret);
            else {
                char ch = name.charAt(this.start + start);
                Char.get(ch).match(s, index).foreach(i -> {
                    get(s, index + i, start + 1, ret);
                    return true;
                });
            }
        }

        @Override
        public Node put(String name, int id, int index) {
            if (this.name == null) {
                this.name = name;
                start = index;
                end = name.length();
                exit = exit.put(name, id, end);
                return this;
            } else {
                int length = end - start;
                int match = strCmp(this.name, name, start, index, length);
                if (match >= length) exit = exit.put(name, id, index + length);
                else {
                    NSlice half = new NSlice();
                    half.name = this.name;
                    half.start = start + match + 1;
                    half.end = end;
                    half.exit = exit;
                    NMap insert = new NMap();
                    insert = insert.put(name, id, index + match);
                    insert.put(this.name.charAt(start + match), half.start == half.end ? exit : half);
                    end = start + match;
                    exit = insert;
                }
                return start == end ? exit : this;
            }
        }

        @Override
        public int countSlice() {
            return 1 + exit.countSlice();
        }

        @Override
        public int countMap() {
            return exit.countMap();
        }
    }

    public static class NMap implements Node {
        Map<Character, Node> exact = new CharObjectHashMap<>();
        Map<Pinyin, List<Character>> pinyin = new IdentityHashMap<>();
        IntSet leaves = new IntOpenHashSet();

        public void get(IntSet ret, String s, int index) {
            if (s.length() == index) get(ret);
            else {
                Map<Node, IntSet> m = new IdentityHashMap<>();
                Node ch = exact.get(s.charAt(index));
                if (ch != null) m.computeIfAbsent(ch, k -> new IntOpenHashSet()).add(1);

                pinyin.forEach((p, cs) -> p.match(s, index).foreach(offset -> {
                    for (Character c : cs) m.computeIfAbsent(exact.get(c), k -> new IntOpenHashSet()).add(offset);
                    return true;
                }));
                m.forEach((n, is) -> {
                    for (int i : is) n.get(ret, s, index + i);
                });
            }
        }

        public void get(IntSet ret) {
            ret.addAll(leaves);
            exact.forEach((p, n) -> n.get(ret));
        }

        public NMap put(String name, int id, int index) {
            if (index == name.length()) {
                leaves.add(id);
            } else {
                char ch = name.charAt(index);
                Node sub = exact.get(ch);
                if (sub == null) put(ch, sub = new NSlice());
                sub = sub.put(name, id, index + 1);
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
            if (isChinese(ch)) {
                for (String str : PinyinData.get(ch))
                    pinyin.computeIfAbsent(Pinyin.get(str), i -> new CharArrayList()).add(ch);
            }
        }
    }
}
