package me.towdium.jecharacters.utils;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.gson.Gson;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static me.towdium.jecharacters.JustEnoughCharacters.logger;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
public class Profiler {
    private static final Analyzer[] ANALYZERS = new Analyzer[]{
            new Analyzer.Construct(Type.SUFFIX, "net/minecraft/client/util/SuffixArray"),
            new Analyzer.Invoke(
                    Type.CONTAINS, false, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z"
            ),
            new Analyzer.Invoke(
                    Type.CONTAINS, true, "kotlin/text/StringsKt", "contains",
                    "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"
            ),
            new Analyzer.Invoke(
                    Type.CONTAINS, true, "kotlin/text/StringsKt", "contains",
                    "(Ljava/lang/CharSequence;Ljava/lang/CharSequence)Z"
            ),
            new Analyzer.Invoke(
                    Type.EQUALS, false, "java/lang/String", "equals",
                    "(Ljava/lang/Object;)Z"
            ),
            new Analyzer.Invoke(
                    Type.REGEXP, false, "java/lang/String", "matches",
                    "(Ljava/lang/String;)Z"
            ),
            new Analyzer.Invoke(
                    Type.REGEXP, false, "java/util/regex/Pattern", "matcher",
                    "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;"
            ),
    };

    public static Report run() {
        File modDirectory = new File("mods");
        Report r = new Report();
        r.jars = scanDirectory(modDirectory);
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

    private static ModContainer[] readInfoOld(InputStream is) {
        Gson gson = new Gson();
        try {
            return gson.fromJson(new InputStreamReader(is), ModContainer[].class);
        } catch (Exception e) {
            return new ModContainer[]{gson.fromJson(new InputStreamReader(is), ModContainer.class)};
        }
    }

    private static ModContainer[] readInfoNew(InputStream is) {
        Path p = null;
        try {
            p = Files.createTempFile("jecharacters", ".toml");
            Files.copy(is, p, REPLACE_EXISTING);
            FileConfig c = FileConfig.of(p);
            c.load();
            Collection<Config> mods = c.get("mods");
            return mods.stream().map(i -> {
                ModContainer mc = new ModContainer();
                mc.modid = i.get("modId");
                mc.name = i.get("displayName");
                mc.version = i.get("version");
                return mc;
            }).toArray(ModContainer[]::new);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (p != null && !p.toFile().delete()) {
                logger.info("Failed to delete temp file.");
            }
        }
    }

    private static void scanJar(ZipFile f, Consumer<JarContainer> cbkJar) {
        EnumMap<Type, Set<String>> methods = new EnumMap<>(Type.class);
        for (Type t : Type.values()) methods.put(t, new TreeSet<>());

        JarContainer ret = new JarContainer();
        f.stream().forEach(entry -> {
            try (InputStream is = f.getInputStream(entry)) {
                if (entry.getName().equals("META-INF/mods.toml")) ret.mods = readInfoNew(is);
                else if (entry.getName().equals("mcmod.info")) ret.mods = readInfoOld(is);
                else if (entry.getName().endsWith(".class")) {
                    long size = entry.getSize() + 4;
                    if (size > Integer.MAX_VALUE) {
                        logger.info("Class file " + entry.getName() + " in jar file " + f.getName() + " is too large, skip.");
                    } else scanClass(is, methods);
                }
            } catch (IOException e) {
                logger.info("Fail to read file " + entry.getName() + " in jar file " + f.getName() + ", skip.");
            }
        });

        if (methods.values().stream().anyMatch(i -> !i.isEmpty())) {
            ret.contains = new ArrayList<>(methods.get(Type.CONTAINS));
            ret.regExp = new ArrayList<>(methods.get(Type.REGEXP));
            ret.suffix = new ArrayList<>(methods.get(Type.SUFFIX));
            ret.equals = new ArrayList<>(methods.get(Type.EQUALS));
            cbkJar.accept(ret);
        }
    }

    private static void scanClass(InputStream is, EnumMap<Type, Set<String>> methods)
            throws IOException {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(is);
        try {
            classReader.accept(classNode, 0);
        } catch (Exception e) {
            if (classNode.name != null) {
                logger.info("File decoding of class " + classNode.name + " failed. Try to continue.");
            } else throw new IOException(e);
        }
        classNode.methods.forEach(methodNode -> {
            for (AbstractInsnNode node : methodNode.instructions) {
                Arrays.stream(ANALYZERS).forEach(i -> i.analyze(node, classNode, methodNode, methods));
            }
        });
    }

    public static class Report {
        List<JarContainer> jars;
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static class JarContainer {
        ModContainer[] mods;
        List<String> contains;
        List<String> regExp;
        List<String> suffix;
        List<String> equals;
    }

    @SuppressWarnings("unused")
    private static class ModContainer {
        String modid;
        String name;
        String version;
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
            String clazz;

            public Construct(Type type, String clazz) {
                super(type);
                this.clazz = clazz;
            }

            @Override
            boolean match(AbstractInsnNode insn) {
                if (insn instanceof TypeInsnNode) {
                    TypeInsnNode tin = ((TypeInsnNode) insn);
                    return tin.getOpcode() == Opcodes.NEW && tin.desc.equals(clazz);
                } else return false;
            }
        }
    }

    enum Type {
        CONTAINS,
        EQUALS,
        REGEXP,
        SUFFIX
    }
}
