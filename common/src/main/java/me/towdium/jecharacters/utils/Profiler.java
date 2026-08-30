package me.towdium.jecharacters.utils;

import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Author: Towdium
 * Date:   14/06/17
 */
public class Profiler {

    protected static Logger LOGGER = LogManager.getLogger();

    protected static final List<Analyzer> ANALYZERS = new ArrayList<>(Arrays.asList(
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
                    Type.CONTAINS, true,
                    "org/apache/commons/lang3/StringUtils", "containsIgnoreCase",
                    "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z"
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
            new Analyzer.Invoke(
                    Type.STARTS_WITH, false,
                    "java/lang/String", "startsWith",
                    "(Ljava/lang/String;)Z"
            ),
            new Analyzer.Invoke(
                    Type.STARTS_WITH, false,
                    "java/lang/String", "startsWith",
                    "(Ljava/lang/String;I)Z"
            )
    ));

    private static final Map<Platform, String> infoFiles = new HashMap<>();

    static {
        infoFiles.put(Platform.FABRIC, "fabric.mod.json");
        infoFiles.put(Platform.NEOFORGE, "META-INF/neoforge.mods.toml");
    }

    @Nullable
    private static Profiler instance;

    private final InfoReader infoReader;

    @NotNull
    public static Profiler getInstance() {
        if (instance == null)
            throw new IllegalStateException("Profiler has not been initialized.");
        return instance;
    }

    public static Profiler init(String suffixArray) {
        if (instance == null) instance = new Profiler(suffixArray);
        return instance;
    }

    private Profiler(String suffixArray) {
        ANALYZERS.add(new Analyzer.Construct(Type.SUFFIX, suffixArray.replace('.', '/')));
        infoReader = ServiceLoader.load(InfoReader.class).findFirst().orElseThrow();
        instance = this;
    }

    public static String runAsJson() {
        return instance == null ? "" :
                new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(instance.run());
    }

    public Report run() {
        File modDirectory = new File("mods");
        Report r = new Report();
        r.jars = scanDirectory(modDirectory);
        return r;
    }

    private ArrayList<JarContainer> scanDirectory(File f) {
        File[] files = f.listFiles();
        ArrayList<JarContainer> jcs = new ArrayList<>();
        Consumer<JarContainer> callback = jcs::add;
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".jar")) {
                    try (ZipFile mod = new ZipFile(file)) {
                        scanJar(mod, callback);
                    } catch (IOException e) {
                        LOGGER.info("Fail to read jar file {}, skip.", file.getName());
                    }
                } else if (file.isDirectory()) {
                    jcs.addAll(scanDirectory(file));
                }
            }
        }
        return jcs;
    }

    private void scanJar(ZipFile f, Consumer<JarContainer> cbkJar) {
        EnumMap<Type, Set<String>> methods = new EnumMap<>(Type.class);
        for (Type t : Type.values()) methods.put(t, new TreeSet<>());

        JarContainer ret = new JarContainer();
        f.stream().forEach(entry -> {
            try (InputStream is = f.getInputStream(entry)) {
                if (entry.getName().equals(infoFiles.get(infoReader.getPlatform())))
                    ret.mods = infoReader.readInfo(is);
                else if (entry.getName().endsWith(".class")) {
                    long size = entry.getSize() + 4;
                    if (size > Integer.MAX_VALUE) {
                        LOGGER.info("Class file {} in jar file {} is too large, skip.", entry.getName(), f.getName());
                    } else scanClass(is, methods);
                } else if (entry.getName().endsWith(".jar")) {
                    scanJarInJar(is, methods);
                }
            } catch (IOException e) {
                LOGGER.info("Fail to read file {} in jar file {}, skip.", entry.getName(), f.getName());
            }
        });

        if (methods.values().stream().anyMatch(i -> !i.isEmpty())) {
            ret.contains = new ArrayList<>(methods.get(Type.CONTAINS));
            ret.regExp = new ArrayList<>(methods.get(Type.REGEXP));
            ret.suffix = new ArrayList<>(methods.get(Type.SUFFIX));
            ret.equals = new ArrayList<>(methods.get(Type.EQUALS));
            ret.startsWith = new ArrayList<>(methods.get(Type.STARTS_WITH));
            cbkJar.accept(ret);
        }
    }

    private void scanClass(InputStream is, EnumMap<Type, Set<String>> methods)
            throws IOException {
        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(is);
        try {
            classReader.accept(classNode, 0);
        } catch (Exception e) {
            if (classNode.name != null) {
                LOGGER.info("File decoding of class {} failed. Try to continue.", classNode.name);
            } else throw new IOException(e);
        }
        classNode.methods.forEach(methodNode -> {
            for (AbstractInsnNode node : methodNode.instructions) {
                ANALYZERS.forEach(i -> i.analyze(node, classNode, methodNode, methods));
            }
        });
    }

    private void scanJarInJar(InputStream is, EnumMap<Type, Set<String>> methods) throws IOException {
        ZipInputStream stream = new ZipInputStream(is);
        ZipEntry entry;
        while ((entry = stream.getNextEntry()) != null) {
            if (entry.getName().endsWith(".class")) {
                scanClass(stream, methods);
            } else if (entry.getName().endsWith(".jar")) {
                scanJarInJar(new ZipInputStream(stream), methods);
            }
        }
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
        List<String> startsWith;
    }

    @SuppressWarnings("unused")
    public static class ModContainer {
        String modid;
        String name;
        String version;

        public ModContainer(String modid, String name, String version) {
            this.modid = modid;
            this.name = name;
            this.version = version;
        }
    }

    public interface InfoReader {

        Platform getPlatform();

        ModContainer[] readInfo(InputStream is);
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
                if (insn instanceof MethodInsnNode node) {
                    return node.getOpcode() == op && node.owner.equals(owner) &&
                            node.name.equals(name) && node.desc.equals(desc);
                } else if (insn instanceof InvokeDynamicInsnNode din) {
                    if (din.bsmArgs.length != 3) return false;
                    Object arg = din.bsmArgs[1];
                    if (arg instanceof Handle handle) {
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
                if (insn instanceof TypeInsnNode tin) {
                    return tin.getOpcode() == Opcodes.NEW && tin.desc.equals(clazz);
                } else return false;
            }
        }
    }

    enum Type {
        CONTAINS,
        EQUALS,
        REGEXP,
        STARTS_WITH,
        SUFFIX
    }

    public enum Platform {
        FABRIC,
        NEOFORGE
    }
}
