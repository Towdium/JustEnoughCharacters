import me.towdium.jecharacters.util.StringMatcher;
import org.junit.jupiter.api.Test;

public class JechTest {
    @Test
    public void quanpin1() {
        assert StringMatcher.checkStr("测试文本", "ceshiwenben");
    }

    @Test
    public void quanpin2() {
        assert StringMatcher.checkStr("测试文本", "ceshiwenbe");
    }

    @Test
    public void quanpin3() {
        assert StringMatcher.checkStr("测试文本", "ceshiwben");
    }

    @Test
    public void daqian1() {
        StringMatcher.setKeyboard(StringMatcher.enumKeyboard.DAQIAN);
        assert StringMatcher.checkStr("测试文本", "hkgujp1p");
    }

    @Test
    public void daqian2() {
        StringMatcher.setKeyboard(StringMatcher.enumKeyboard.DAQIAN);
        assert StringMatcher.checkStr("测试文本", "hkgujp1");
    }
}
