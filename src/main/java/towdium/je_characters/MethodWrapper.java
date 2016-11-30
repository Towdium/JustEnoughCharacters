package towdium.je_characters;

import org.objectweb.asm.tree.MethodNode;

import java.util.function.Consumer;

/**
 * Author: towdium
 * Date:   29/11/16
 */
public class MethodWrapper {
    public String className;
    public String methodName;
    public Consumer<MethodNode> transformer;

    private MethodWrapper() {
    }

    public static MethodWrapper GenMethodWrapper(EnumMatchType t, String identifier) {
        String[] buf = identifier.split(":");
        MethodWrapper mw = new MethodWrapper();
        mw.className = buf[0];
        mw.methodName = buf[1];
        mw.transformer = t.getTransformer();
        return mw;
    }

    public enum EnumMatchType {
        STR, REG;

        public Consumer<MethodNode> getTransformer() {
            switch (this) {
                case STR:
                    return ClassTransformer::transformStr;
                case REG:
                    return ClassTransformer::transformReg;
                default:
                    return methodNode -> {
                    };
            }
        }
    }
}
