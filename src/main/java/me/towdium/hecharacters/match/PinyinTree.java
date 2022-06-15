package me.towdium.hecharacters.match;

import it.unimi.dsi.fastutil.chars.*;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.towdium.hecharacters.match.matchables.Char;
import me.towdium.hecharacters.match.matchables.Pinyin;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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

    public static void refresh() {
        Glue.refresh();
    }

    static class Glue {
        Map<Pinyin, CharSet> map = new Object2ObjectArrayMap<>();
        Char2ObjectMap<Set<Pinyin>> index;
        static List<WeakReference<Glue>> instances = new ArrayList<>();

        public Char2ObjectMap<IntSet> get(String name, int offset) {
            Char2ObjectMap<IntSet> ret = new Char2ObjectArrayMap<>();
            BiConsumer<Pinyin, CharSet> add = (p, cs) -> p.match(name, offset).foreach(i -> {
                for (char c : cs) ret.computeIfAbsent(c, k -> new IntArraySet()).add(i);
                return true;
            });

            if (index != null) {
                index.computeIfPresent(name.charAt(offset), (b, s) -> {
                    s.forEach(p -> add.accept(p, map.get(p)));
                    return s;
                });
            } else map.forEach(add);
            return ret;
        }

        public void put(char ch) {
            if (!Utilities.isChinese(ch)) return;
            for (Pinyin p : Pinyin.get(ch)) {
                map.compute(p, (py, cs) -> {
                    if (cs == null) {
                        cs = new CharArraySet();
                        if (index != null) index.computeIfAbsent(py.start(),
                                c -> new ObjectOpenHashSet<>()).add(py);
                    } else if (cs.size() >= 16 && cs instanceof CharArraySet)
                        cs = new CharOpenHashSet(cs);
                    cs.add(ch);
                    return cs;
                });
            }
            if (map.size() >= 16 && index == null) {
                map = new Object2ObjectOpenHashMap<>(map);
                instances.add(new WeakReference<>(this));
                index();
            }
        }

        public void index() {
            index = new Char2ObjectOpenHashMap<>();
            map.forEach((p, cs) -> index.computeIfAbsent(p.start(),
                    c -> new ObjectOpenHashSet<>()).add(p));
        }

        static void refresh() {
            instances = instances.stream().filter(i -> {
                Glue g = i.get();
                if (g != null) g.index();
                return g != null;
            }).collect(Collectors.toList());
        }
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
        Char2ObjectMap<Node> children; // = new Char2ObjectOpenHashMap<>();
        Glue glue;
        IntSet leaves = new IntArraySet();

        @Override
        public void get(IntSet ret, String name, int offset) {
            if (name.length() == offset) get(ret);
            else if (children != null && glue != null) {
                Node n = children.get(name.charAt(offset));
                if (n != null) n.get(ret, name, offset + 1);
                glue.get(name, offset).forEach((c, is) -> {
                    for (int i : is) children.get(c).get(ret, name, offset + i);
                });
            }
        }

        @Override
        public void get(IntSet ret) {
            ret.addAll(leaves);
            if (children != null) children.forEach((p, n) -> n.get(ret));
        }

        @Override
        public NMap put(String name, int identifier, int offset) {
            if (offset == name.length()) {
                if (leaves.size() >= 16 && leaves instanceof IntArraySet)
                    leaves = new IntOpenHashSet(leaves);
                leaves.add(identifier);
            } else {
                init();
                char ch = name.charAt(offset);
                Node sub = children.get(ch);
                if (sub == null) put(ch, sub = new NSlice());
                sub = sub.put(name, identifier, offset + 1);
                children.put(ch, sub);
            }
            return this;
        }

        @Override
        public int countSlice() {
            int ret = 0;
            if (children != null) for (Node n : children.values()) ret += n.countSlice();
            return ret;
        }

        @Override
        public int countMap() {
            int ret = 1;
            if (children != null) for (Node n : children.values()) ret += n.countMap();
            return ret;
        }

        private void put(char ch, Node n) {
            init();
            if (children.size() >= 16 && children instanceof Char2ObjectArrayMap)
                children = new Char2ObjectOpenHashMap<>(children);
            children.put(ch, n);
            glue.put(ch);
        }

        private void init() {
            if (children == null || glue == null) {
                children = new Char2ObjectArrayMap<>();
                glue = new Glue();
            }
        }
    }
}
