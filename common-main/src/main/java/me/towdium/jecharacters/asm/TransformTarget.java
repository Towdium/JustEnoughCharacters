package me.towdium.jecharacters.asm;

import me.towdium.jecharacters.annotations.MethodsReturnNonnullByDefault;
import me.towdium.jecharacters.annotations.ParametersAreNonnullByDefault;
import org.objectweb.asm.tree.MethodNode;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransformTarget {

    private final String owner;
    private final String methodName;
    private final String methodDesc;

    public TransformTarget(String owner, String methodName, String methodDesc) {
        this.owner = owner;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
    }

    public static TransformTarget of(String target) {
        target = target.replace(".", "/");
        String[] split = target.split(":", 2);
        String owner = split[0];
        String methodName = split[1].substring(0, split[1].indexOf("("));
        String methodDesc = split[1].substring(split[1].indexOf("("));
        return new TransformTarget(owner, methodName, methodDesc);
    }

    public String getOwner() {
        return owner;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public boolean matches(String owner, MethodNode methodNode) {
        return this.owner.equals(owner) & methodNode.name.equals(methodName)
                && methodNode.desc.equals(methodDesc);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransformTarget that = (TransformTarget) o;

        if (!owner.equals(that.owner)) return false;
        if (!methodName.equals(that.methodName)) return false;
        return methodDesc.equals(that.methodDesc);
    }

    @Override
    public int hashCode() {
        int result = owner.hashCode();
        result = 31 * result + methodName.hashCode();
        result = 31 * result + methodDesc.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TransformTarget{"
                + "owner='"
                + owner
                + '\''
                + ", methodName='"
                + methodName
                + '\''
                + ", methodDesc='"
                + methodDesc
                + '\''
                + '}';
    }
}
