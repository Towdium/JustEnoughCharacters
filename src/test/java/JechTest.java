import it.unimi.dsi.fastutil.ints.IntSet;
import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.match.Keyboard;
import me.towdium.hecharacters.match.PinyinMatcher;
import me.towdium.hecharacters.match.PinyinTree;
import me.towdium.hecharacters.match.Utilities;
import org.junit.jupiter.api.Test;

public class HechTest {
    @Test
    public void quanpin() {
        HechConfig.keyboard = Keyboard.QUANPIN;
        Utilities.refresh();
        assert PinyinMatcher.contains("测试文本", "ceshiwenben");
        assert PinyinMatcher.contains("测试文本", "ceshiwenbe");
        assert PinyinMatcher.contains("测试文本", "ceshiwben");
        assert PinyinMatcher.contains("测试文本", "ce4shi4w2ben");
        assert !PinyinMatcher.contains("测试文本", "ce2shi4w2ben");
        assert PinyinMatcher.contains("合金炉", "hejinlu");
        assert PinyinMatcher.contains("洗矿场", "xikuangchang");
        assert PinyinMatcher.contains("流体", "liuti");
    }

    @Test
    public void daqian() {
        HechConfig.keyboard = Keyboard.DAQIAN;
        Utilities.refresh();
        assert PinyinMatcher.contains("测试文本", "hk4g4jp61p3");
        assert PinyinMatcher.contains("测试文本", "hkgjp1");
        assert PinyinMatcher.contains("錫", "vu6");
        assert PinyinMatcher.contains("物質", "j456");
    }

    @Test
    public void performance() {
        HechConfig.keyboard = Keyboard.QUANPIN;
        Utilities.refresh();
        for (int i = 1; i < 100; i++)
            PinyinMatcher.contains("测试1000一段测试文本", "0yidceshwenben");
        long t = System.currentTimeMillis();
        for (int i = 1; i < 1000000; i++)
            PinyinMatcher.contains("测试1000一段测试文本", "0yidceshwenben");
        t = System.currentTimeMillis() - t;
        System.out.println("Iterate search took " + t + " milliseconds.");
        t = System.currentTimeMillis();
        PinyinTree tree = new PinyinTree();
        for (int i = 1; i < 1000000; i++) {
            tree.put("测试" + i + "一段测试文本", i);
        }
        t = System.currentTimeMillis() - t;
        System.out.println("Tree construction took " + t + " milliseconds.");
        System.out.println("Tree uses " + tree.countSlice() + " slice nodes.");
        System.out.println("Tree uses " + tree.countMap() + " map nodes.");
        t = System.currentTimeMillis();
        tree.search("0yidceshwenben");
        t = System.currentTimeMillis() - t;
        System.out.println("Tree search took " + t + " milliseconds.");
    }

    @Test
    public void tree() {
        HechConfig.keyboard = Keyboard.QUANPIN;
        Utilities.refresh();
        PinyinTree graph = new PinyinTree();
        graph.put("测试文本", 1);
        graph.put("测试切分", 5);
        graph.put("测试切分文本", 6);
        graph.put("合金炉", 2);
        graph.put("洗矿场", 3);
        graph.put("流体", 4);

        IntSet s;
        s = graph.search("ceshiwenben");
        assert s.size() == 1 && s.contains(1);
        s = graph.search("ceshiwenbe");
        assert s.size() == 1 && s.contains(1);
        s = graph.search("ceshiwben");
        assert s.size() == 1 && s.contains(1);
        s = graph.search("ce4shi4w2ben");
        assert s.size() == 1 && s.contains(1);
        s = graph.search("ce2shi4w2ben");
        assert s.size() == 0;
        s = graph.search("hejinlu");
        assert s.size() == 1 && s.contains(2);
        s = graph.search("xikuangchang");
        assert s.size() == 1 && s.contains(3);
        s = graph.search("liuti");
        assert s.size() == 1 && s.contains(4);
        s = graph.search("ceshi");
        assert s.size() == 3 && s.contains(1) && s.contains(5);
        s = graph.search("ceshiqiefen");
        assert s.size() == 2 && s.contains(5);
        s = graph.search("ceshiqiefenw");
        assert s.size() == 1 && s.contains(6);
    }
}
