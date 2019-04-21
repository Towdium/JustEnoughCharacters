package me.towdium.jecharacters.match;

import io.netty.util.collection.CharObjectHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static me.towdium.jecharacters.match.Utilities.isChinese;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class PinyinTree {
    Node root = new Node();

    public void put(String name, int id) {
        for (int i = 0; i < name.length(); i++) {
            root.put(name, id, i);
        }
    }

    public IntSet search(String s) {
        IntSet ret = new IntOpenHashSet();
        root.get(s, 0, ret);
        return ret;
    }

    public static class Node {
        Map<Character, Node> exact = new CharObjectHashMap<>();
        Map<PinyinPattern, List<Node>> pinyin = new IdentityHashMap<>();
        IntSet leaves = new IntOpenHashSet();

        public void get(String s, int index, IntSet ret) {
            if (s.length() == index) {
                ret.addAll(leaves);
                exact.forEach((p, n) -> n.get(s, index, ret));
            } else {
                Map<Node, IntSet> m = new IdentityHashMap<>();
                Node ch = exact.get(s.charAt(index));
                if (ch != null) m.computeIfAbsent(ch, k -> new IntOpenHashSet()).add(1);

                pinyin.forEach((p, ns) -> p.match(s, index).foreach(offset -> {
                    for (Node n : ns) m.computeIfAbsent(n, k -> new IntOpenHashSet()).add(offset);
                    return true;
                }));
                m.forEach((n, is) -> {
                    for (int i : is) n.get(s, index + i, ret);
                });
            }
        }

        public void put(String name, int id, int index) {
            if (index == name.length()) leaves.add(id);
            else {
                char ch = name.charAt(index);
                Node sub = exact.get(ch);
                if (sub == null) {
                    exact.put(ch, sub = new Node());
                    if (isChinese(ch)) {
                        for (String str : PinyinData.get(ch))
                            pinyin.computeIfAbsent(PinyinPattern.get(str), i -> new ArrayList<>()).add(sub);
                    }
                }
                sub.put(name, id, index + 1);
            }
        }
    }


}
