import it.unimi.dsi.fastutil.ints.IntSet;
import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.match.Keyboard;
import me.towdium.jecharacters.match.PinyinMatcher;
import me.towdium.jecharacters.match.PinyinTree;
import org.junit.jupiter.api.Test;

public class JechTest {
    @Test
    public void quanpin() {
        JechConfig.keyboard = Keyboard.QUANPIN;
        PinyinMatcher.refresh();
        assert PinyinMatcher.checkStr("测试文本", "ceshiwenben");
        assert PinyinMatcher.checkStr("测试文本", "ceshiwenbe");
        assert PinyinMatcher.checkStr("测试文本", "ceshiwben");
        assert PinyinMatcher.checkStr("测试文本", "ce4shi4w2ben");
        assert !PinyinMatcher.checkStr("测试文本", "ce2shi4w2ben");
        assert PinyinMatcher.checkStr("合金炉", "hejinlu");
        assert PinyinMatcher.checkStr("洗矿场", "xikuangchang");
        assert PinyinMatcher.checkStr("流体", "liuti");
    }

    @Test
    public void daqian() {
        JechConfig.keyboard = Keyboard.DAQIAN;
        PinyinMatcher.refresh();
        assert PinyinMatcher.checkStr("测试文本", "hk4g4jp61p3");
        assert PinyinMatcher.checkStr("测试文本", "hkgjp1");
        assert PinyinMatcher.checkStr("錫", "vu6");
        assert PinyinMatcher.checkStr("物質", "j456");
    }

    @Test
    public void performance() {
        for (int i = 1; i < 100; i++)
            PinyinMatcher.checkStr("一段测试文本", "yidceshwenben");
        long t = System.currentTimeMillis();
        for (int i = 1; i < 300000; i++)
            PinyinMatcher.checkStr("一段测试文本", "yidceshwenben");
        t = System.currentTimeMillis() - t;
        System.out.println("Iterate took " + t + " milliseconds.");
        t = System.currentTimeMillis();
        PinyinTree tree = new PinyinTree();
        for (int i = 1; i < 300000; i++) {
            tree.put("测试" + i + "文本", i);
        }
        t = System.currentTimeMillis() - t;
        System.out.println("Index took " + t + " milliseconds.");
        t = System.currentTimeMillis();
        tree.search("文本");
        t = System.currentTimeMillis() - t;
        System.out.println("Search took " + t + " milliseconds.");

    }

    @Test
    public void graph() {
        PinyinTree graph = new PinyinTree();
        graph.put("测试文本", 1);
        graph.put("合金炉", 2);
        graph.put("洗矿场", 3);
        graph.put("流体", 4);

        IntSet s;
//        s = graph.search("ceshiwenben");
//        assert s.size() == 1 && s.contains("测试文本");
        s = graph.search("ceshiwenbe");
        assert s.size() == 0;
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
    }


//    @Test
//    public void performance() {
//        for (int i = 1; i < 100; i++)
//            PinyinMatcher.checkStr("一段测试文本", "yidceshwenben");
//        long t0 = System.currentTimeMillis();
//        for (int i = 1; i < 3000000; i++)
//            PinyinMatcher.checkStr("一段测试文本", "yidceshwenben");
//        long td = System.currentTimeMillis() - t0;
//        System.out.println("Took " + td + " milliseconds.");
//    }
}
