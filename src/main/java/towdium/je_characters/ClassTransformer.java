package towdium.je_characters;

import com.google.common.collect.ArrayListMultimap;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import towdium.je_characters.jei.TransformHelper;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Author: Towdium
 * Date:   2016/9/4.
 */
public class ClassTransformer implements IClassTransformer {
    public static ArrayListMultimap<String, MethodWrapper> m = ArrayListMultimap.create();
    public static Set<String> s = new HashSet<>();
    static boolean flag = false;
    static Object[] args = new Object[]{
            Type.getType("(Ljava/lang/Object;)Z"),
            new Handle(6, "towdium/je_characters/CheckHelper", "checkStr", "(Ljava/lang/String;Ljava/lang/String;)Z"),
            Type.getType("(Ljava/lang/String;)Z")
    };

    public static void init() {
        String[] blackList = JECConfig.EnumItems.ListMethodBlacklist.getProperty().getStringList();

        BiConsumer<String, MethodWrapper.EnumMatchType> putEntry = (s, t) -> {
            for (String bl : blackList) {
                if (bl.equals(s))
                    return;
            }
            try {
                MethodWrapper mw = MethodWrapper.GenMethodWrapper(t, s);
                m.put(mw.className, mw);
            } catch (IllegalArgumentException e) {
                LoadingPlugin.log.warn("[je_character] The identifier \"" + s + "\" is not in acceptable format, ignore it.");
            }
        };
        BiConsumer<JECConfig.EnumItems, MethodWrapper.EnumMatchType> putList = (l, t) -> {
            for (String str : l.getProperty().getStringList()) {
                putEntry.accept(str, t);
            }
        };

        putList.accept(JECConfig.EnumItems.ListAdditionalRegExpMatch, MethodWrapper.EnumMatchType.REG);
        putList.accept(JECConfig.EnumItems.ListAdditionalStringMatch, MethodWrapper.EnumMatchType.STR);
        putList.accept(JECConfig.EnumItems.ListDefaultRegExpMatch, MethodWrapper.EnumMatchType.REG);
        putList.accept(JECConfig.EnumItems.ListDefaultStringMatch, MethodWrapper.EnumMatchType.STR);

        Collections.addAll(ClassTransformer.s, JECConfig.EnumItems.ListDumpClass.getProperty().getStringList());
    }

    static void transformStr(MethodNode methodNode) {
        transform(
                methodNode, "java/lang/String", "contains", "towdium/je_characters/CheckHelper", "checkStr",
                "(Ljava/lang/String;Ljava/lang/String;)Z", false, Opcodes.INVOKESTATIC
        );
    }

    static void transformReg(MethodNode methodNode) {
        transform(methodNode, "java/util/regex/Pattern", "matcher", "towdium/je_characters/CheckHelper", "checkReg",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;", false, Opcodes.INVOKESTATIC
        );
    }

    static void transform(MethodNode methodNode, String owner, String name, String newOwner, String newName, String id, boolean isInterface, int op) {
        Iterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
        while (iterator.hasNext()) {
            AbstractInsnNode node = iterator.next();
            if (node instanceof MethodInsnNode && node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode insnNode = ((MethodInsnNode) node);
                if (insnNode.owner.equals(owner) && insnNode.name.equals(name)) {
                    methodNode.instructions.set(insnNode, new MethodInsnNode(op, newOwner, newName, id, isInterface));
                }
            }
            if (node instanceof InvokeDynamicInsnNode && node.getOpcode() == Opcodes.INVOKEDYNAMIC) {
                InvokeDynamicInsnNode insnNode = ((InvokeDynamicInsnNode) node);
                if (insnNode.bsmArgs[0].toString().equals("(Ljava/lang/Object;)Z") &&
                        insnNode.bsmArgs[1].toString().equals("java/lang/String.contains(Ljava/lang/CharSequence;)Z (5)")) {
                    methodNode.instructions.set(insnNode, new InvokeDynamicInsnNode(insnNode.name, insnNode.desc, insnNode.bsm, args));
                }
            }
        }
    }

    @Override
    public byte[] transform(String s, String s1, byte[] bytes) {
        if (LoadingPlugin.initialized) {
            if (ClassTransformer.s.contains(s1)) {
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(classNode, 0);
                LoadingPlugin.log.info("[je_characters] Dump methods in class \"" + s1 + "\".");
                classNode.methods.forEach(methodNode -> LoadingPlugin.log.info("[je_characters]\t" + methodNode.name));
            }
            List<MethodWrapper> mws = m.get(s1);
            if (JECConfig.EnumItems.EnableJEI.getProperty().getBoolean() && s1.equals(TransformHelper.getClassName())) {
                return TransformHelper.transform(bytes);
            }
            if (mws.size() != 0 || JECConfig.EnumItems.EnableRadicalMode.getProperty().getBoolean()) {
                ClassNode classNode = new ClassNode();
                ClassReader classReader = new ClassReader(bytes);
                classReader.accept(classNode, 0);
                if (mws.size() != 0) {
                    LoadingPlugin.log.info("[je_characters] Transforming class \"" + s1 + "\".");
                }
                mws.forEach(mw -> {
                    classNode.methods.stream().filter(methodNode -> methodNode.name.equals(mw.methodName)).
                            forEach(methodNode -> {
                                LoadingPlugin.log.info("[je_characters] Transforming method \"" + methodNode.name + "\".");
                                mw.transformer.accept(methodNode);
                                flag = true;
                            });
                    LoadingPlugin.log.info("[je_characters] " + (flag ? "Succeeded." : ("Method \"") + mw.methodName + "\" not found."));
                    flag = false;
                });
                if (JECConfig.EnumItems.EnableRadicalMode.getProperty().getBoolean()) {
                    classNode.methods.forEach(ClassTransformer::transformStr);
                }
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                classNode.accept(classWriter);
                return classWriter.toByteArray();
            }

            return bytes;
        } else {
            return bytes;
        }
    }

    public static class MethodWrapper {
        public String className;
        public String methodName;
        public Consumer<MethodNode> transformer;

        private MethodWrapper() {
        }

        public static MethodWrapper GenMethodWrapper(EnumMatchType t, String identifier) {
            try {
                String[] buf = identifier.split(":");
                MethodWrapper mw = new MethodWrapper();
                mw.className = buf[0];
                mw.methodName = buf[1];
                mw.transformer = t.getTransformer();
                return mw;
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Incorrect format.");
            }

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
                        throw new RuntimeException("Illegal Position.");
                }
            }
        }
    }
}
