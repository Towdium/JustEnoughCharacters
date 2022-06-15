package me.towdium.hecharacters.transform.transformers;

import me.towdium.hecharacters.HechConfig;
import me.towdium.hecharacters.match.PinyinMatcher;
import me.towdium.hecharacters.match.Utilities;
import me.towdium.hecharacters.transform.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Towdium
 * Date:   12/06/17
 */
public class TransformerRegExp extends Transformer.Configurable {
    static final Pattern p = Pattern.compile("a");

    public TransformerRegExp() {
        reload();
    }

    @Override
    protected String[] getDefault() {
        return HechConfig.listDefaultRegExp;
    }

    @Override
    protected String[] getAdditional() {
        return HechConfig.listAdditionalRegExp;
    }

    @Override
    protected String getName() {
        return "regular expression";
    }

    @Override
    protected void transform(MethodNode n) {
        Transformer.transformInvoke(
                n, "java/util/regex/Pattern", "matcher", "me/towdium/hecharacters/transform/transformers/TransformerRegExp", "matcher",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                false, Opcodes.INVOKESTATIC, null, null
        );
        Transformer.transformInvoke(
                n, "java/lang/String", "matches", "me/towdium/hecharacters/transform/transformers/TransformerRegExp", "matches",
                "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                false, Opcodes.INVOKESTATIC, "(Ljava/lang/Object;)Z", "(Ljava/lang/String;)Z"
        );
    }

    @SuppressWarnings("unused")
    public static Matcher matcher(Pattern test, CharSequence name) {
        if (Utilities.isChinese(name)) {
            boolean ret = PinyinMatcher.contains(name.toString(), trim(test.toString()));
            return ret ? p.matcher("a") : p.matcher("");
        } else return test.matcher(name);
    }

    @SuppressWarnings("unused")
    public static boolean matches(String s1, CharSequence s2) {
        return PinyinMatcher.contains(s1, trim(s2.toString()));
    }

    private static String trim(String s) {
        boolean start = s.startsWith(".*");
        boolean end = s.endsWith(".*");
        if (start || end) {
            if (start && end && s.length() < 4) end = false;
            s = s.substring(start ? 2 : 0, s.length() - (end ? 2 : 0));
        }
        return s;
    }
}
