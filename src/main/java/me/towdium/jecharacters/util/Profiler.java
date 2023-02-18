package me.towdium.jecharacters.util;

import com.google.gson.Gson;
import me.towdium.jecharacters.core.JechCore;
import net.minecraftforge.common.MinecraftForge;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
public class Profiler {

    private static final JarContainer[] EMPTY_JC = new JarContainer[]{};
    private static final String[] EMPTY_STR = new String[]{};

    private static final Analyzer SUFFIX = new Analyzer.Construct(Type.SUFFIX, "net/minecraft/client/util/SuffixArray", "cgx", "cgz");
    private static final Analyzer CONTAINS = new Analyzer.Invoke(Type.CONTAINS, false, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z");
    private static final List<Analyzer> STRINGKT = Arrays.asList(
            new Analyzer.Invoke(Type.CONTAINS, true,
                    "kotlin/text/StringsKt", "contain" +
                    "s", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"),
            new Analyzer.Invoke(
                    Type.CONTAINS, true, "kotlin/text/StringsKt", "contains",
                    "(Ljava/lang/CharSequence;Ljava/lang/CharSequence)Z"
            )
    );
    private static final Analyzer EQUALS = new Analyzer.Invoke(Type.EQUALS, false, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");
    private static final Analyzer MATCHES = new Analyzer.Invoke(Type.REGEXP, false, "java/lang/String", "matches", "(Ljava/lang/String;)Z");
    private static final Analyzer REGEXP = new Analyzer.Invoke(Type.REGEXP, false, "java/util/regex/Pattern", "matcher", "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;");

    public static Report run() {
        File modDirectory = new File("mods");
        JarContainer[] jcs = scanDirectory(modDirectory).toArray(EMPTY_JC);
        Report r = new Report();
        r.jars = jcs;
        return r;
    }

    private static ArrayList<JarContainer> scanDirectory(File f) {
        File[] files = f.listFiles();
        ArrayList<JarContainer> jcs = new ArrayList<>();
        Consumer<JarContainer> callback = jcs::add;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".jar")) {
                    try (ZipFile mod = new ZipFile(file)) {
                        scanJar(mod, callback);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (file.isDirectory()) {
                    jcs.addAll(scanDirectory(file));
                }
            }
        }
        return jcs;
    }

    private static void scanJar(ZipFile f, Consumer<JarContainer> cbkJar) {
        ArrayList<String> methodsString = new ArrayList<>();
        ArrayList<String> methodsEquals = new ArrayList<>();
        ArrayList<String> methodsRegExp = new ArrayList<>();
        ArrayList<String> methodsSuffix = new ArrayList<>();
        ArrayList<String> methodsStrsKt = new ArrayList<>();
        JarContainer ret = new JarContainer();
        f.stream().forEach(entry -> {
            if (entry.getName().endsWith(".class")) {
                try (InputStream is = f.getInputStream(entry)) {
                    long size = entry.getSize() + 4;
                    if (size > Integer.MAX_VALUE) {
                        JechCore.LOG.info("Class file " + entry.getName()
                                + " in jar file " + f.getName() + " is too large, skip.");
                    } else {
                        scanClass(is, methodsString::add, methodsEquals::add, methodsRegExp::add, methodsSuffix::add, methodsStrsKt::add);
                    }
                } catch (IOException e) {
                    JechCore.LOG.info("Fail to read file " + entry.getName()
                            + " in jar file " + f.getName() + ", skip.");
                }
            } else if (entry.getName().equals("mcmod.info")) {
                Gson gson = new Gson();
                try (InputStream is = f.getInputStream(entry)) {
                    try {
                        ret.mods = gson.fromJson(new InputStreamReader(is), ModContainer[].class);
                    } catch (Exception e) {
                        ret.mods = new ModContainer[]{gson.fromJson(new InputStreamReader(is), ModContainer.class)};
                    }
                } catch (Exception e) {
                    JechCore.LOG.info("Fail to read mod info in jar file " + f.getName() + ", skip.");
                }
            }
        });
        if (!methodsString.isEmpty() || !methodsEquals.isEmpty() || !methodsRegExp.isEmpty() || !methodsSuffix.isEmpty() || !methodsStrsKt.isEmpty()) {
            ret.methodsString = methodsString.toArray(EMPTY_STR);
            ret.methodsEquals = methodsEquals.toArray(EMPTY_STR);
            ret.methodsRegExp = methodsRegExp.toArray(EMPTY_STR);
            ret.methodsSuffix = methodsSuffix.toArray(EMPTY_STR);
            ret.methodsStrsKt = methodsStrsKt.toArray(EMPTY_STR);
            cbkJar.accept(ret);
        }
    }

    private static void scanClass(InputStream is, Consumer<String> string, Consumer<String> equals, Consumer<String> regexp,
                                  Consumer<String> suffix, Consumer<String> strskt) throws IOException {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(is);
        try {
            classReader.accept(classNode, 0);
        } catch (Exception e) {
            if (classNode.name != null) {
                JechCore.LOG.info("File decoding of class " + classNode.name + " failed. Try to continue.");
            } else {
                throw new IOException(e);
            }
        }

        classNode.methods.forEach(methodNode -> {
            Iterator<AbstractInsnNode> it = methodNode.instructions.iterator();
            while (it.hasNext()) {
                AbstractInsnNode node = it.next();
                if (CONTAINS.match(node)) {
                    string.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                } else if (EQUALS.match(node)) {
                    equals.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                } else if (MATCHES.match(node) || REGEXP.match(node)) {
                    regexp.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                } else if (STRINGKT.stream().anyMatch(analyzer -> analyzer.match(node))) {
                    strskt.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                } else if (SUFFIX.match(node)) {
                    suffix.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc)
                            .replace('/', '.'));
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public static class Report {
        String version = "@VERSION@";
        String mcversion = MinecraftForge.MC_VERSION;
        String date;
        JarContainer[] jars;

        {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            date = sdf.format(new Date());
        }
    }

    private static class JarContainer {
        ModContainer[] mods;
        String[] methodsString;
        String[] methodsEquals;
        String[] methodsRegExp;
        String[] methodsSuffix;
        String[] methodsStrsKt;
    }

    @SuppressWarnings("unused")
    private static class ModContainer {
        String modid;
        String name;
        String version;
        String mcversion;
        String url;
        String[] authorList;
    }

    private static abstract class Analyzer {
        Type type;

        public Analyzer(Type type) {
            this.type = type;
        }

        public void analyze(AbstractInsnNode insn, ClassNode clazz, MethodNode method,
                            EnumMap<Type, Set<String>> methods) {
            if (match(insn)) {
                methods.get(type).add(clazz.name.replace('/', '.') + ":" + method.name + method.desc);
            }
        }

        abstract boolean match(AbstractInsnNode insn);

        private static class Invoke extends Analyzer {
            String owner;
            String name;
            String desc;
            int op;
            int tag;

            public Invoke(Type type, boolean isStatic, String owner, String name, String desc) {
                super(type);
                op = isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
                tag = isStatic ? Opcodes.H_INVOKESTATIC : Opcodes.H_INVOKEVIRTUAL;
                this.owner = owner;
                this.name = name;
                this.desc = desc;
            }

            @Override
            boolean match(AbstractInsnNode insn) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode node = (MethodInsnNode) insn;
                    return node.getOpcode() == op && node.owner.equals(owner) &&
                            node.name.equals(name) && node.desc.equals(desc);
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode din = (InvokeDynamicInsnNode) insn;
                    if (din.bsmArgs.length != 3) return false;
                    Object arg = din.bsmArgs[1];
                    if (arg instanceof Handle) {
                        Handle handle = (Handle) arg;
                        return handle.getTag() == tag && handle.getOwner().equals(owner) &&
                                handle.getName().equals(name) && handle.getDesc().equals(desc);
                    }
                }
                return false;
            }
        }

        private static class Construct extends Analyzer {
            String[] clazz;

            public Construct(Type type, String... clazz) {
                super(type);
                this.clazz = clazz;
            }

            @Override
            boolean match(AbstractInsnNode insn) {
                if (insn instanceof TypeInsnNode) {
                    TypeInsnNode tin = ((TypeInsnNode) insn);
                    return tin.getOpcode() == Opcodes.NEW && Arrays.asList(clazz).contains(tin.desc);
                } else return false;
            }
        }
    }

    enum Type {
        CONTAINS,
        EQUALS,
        REGEXP,
        SUFFIX,
        STRINGSKT
    }

}
