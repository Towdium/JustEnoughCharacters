import me.towdium.jecharacters.JechConfig;
import me.towdium.jecharacters.util.Keyboard;
import me.towdium.jecharacters.util.StringMatcher;
import org.junit.jupiter.api.Test;

public class JechTest {
    @Test
    public void quanpin() {
        JechConfig.keyboard = Keyboard.QUANPIN;
        StringMatcher.refresh();
        assert StringMatcher.checkStr("测试文本", "ceshiwenben");
        assert StringMatcher.checkStr("测试文本", "ceshiwenbe");
        assert StringMatcher.checkStr("测试文本", "ceshiwben");
        assert StringMatcher.checkStr("测试文本", "ce4shi4w2ben");
        assert !StringMatcher.checkStr("测试文本", "ce2shi4w2ben");
        assert StringMatcher.checkStr("合金炉", "hejinlu");
        assert StringMatcher.checkStr("洗矿场", "xikuangchang");
        assert StringMatcher.checkStr("流体", "liuti");
    }

    @Test
    public void daqian() {
        JechConfig.keyboard = Keyboard.DAQIAN;
        StringMatcher.refresh();
        assert StringMatcher.checkStr("测试文本", "hk4g4jp61p3");
        assert StringMatcher.checkStr("测试文本", "hkgjp1");
        assert StringMatcher.checkStr("錫", "vu6");
        assert StringMatcher.checkStr("物質", "j456");
    }

    @Test
    public void performance() {
        for (int i = 1; i < 100; i++)
            StringMatcher.checkStr("一段测试文本", "yidceshwenben");
        long t0 = System.currentTimeMillis();
        for (int i = 1; i < 3000000; i++)
            StringMatcher.checkStr("一段测试文本", "yidceshwenben");
        long td = System.currentTimeMillis() - t0;
        System.out.println("Took " + td + " milliseconds.");
    }
}
