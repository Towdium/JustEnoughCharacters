package towdium.je_characters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: towdium
 * Date:   06/11/16
 */

public class CheckHelperAdditions {
    static final Pattern p = Pattern.compile("a");

    public static Matcher checkReg(Pattern test, CharSequence name) {
        return CheckHelper.check(name.toString(), test.toString()) ? p.matcher("a") : p.matcher("");
    }
}
