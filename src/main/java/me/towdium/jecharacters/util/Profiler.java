package me.towdium.jecharacters.util;

import com.google.gson.Gson;
import me.towdium.jecharacters.core.JechCore;
import net.minecraftforge.common.MinecraftForge;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

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
        ArrayList<String> methodsRegExp = new ArrayList<>();
        ArrayList<String> methodsSuffix = new ArrayList<>();
        ArrayList<String> methodsStrsKt = new ArrayList<>();
        Wrapper<ModContainer[]> mods = new Wrapper<>(null);
        f.stream().forEach(entry -> {
            if (entry.getName().endsWith(".class")) {
                try (InputStream is = f.getInputStream(entry)) {
                    long size = entry.getSize() + 4;
                    if (size > Integer.MAX_VALUE) {
                        JechCore.LOG.info("Class file " + entry.getName()
                                + " in jar file " + f.getName() + " is too large, skip.");
                    } else {
                        scanClass(is, methodsString::add, methodsRegExp::add, methodsSuffix::add, methodsStrsKt::add);
                    }
                } catch (IOException e) {
                    JechCore.LOG.info("Fail to read file " + entry.getName()
                            + " in jar file " + f.getName() + ", skip.");
                }
            } else if (entry.getName().equals("mcmod.info")) {
                Gson gson = new Gson();
                try (InputStream is = f.getInputStream(entry)) {
                    try {
                        mods.v = gson.fromJson(new InputStreamReader(is), ModContainer[].class);
                    } catch (Exception e) {
                        mods.v = new ModContainer[]{gson.fromJson(new InputStreamReader(is), ModContainer.class)};
                    }
                } catch (Exception e) {
                    JechCore.LOG.info("Fail to read mod info in jar file " + f.getName() + ", skip.");
                }
            }
        });
        if (!methodsString.isEmpty() || !methodsRegExp.isEmpty() || !methodsSuffix.isEmpty() || !methodsStrsKt.isEmpty()) {
            JarContainer ret = new JarContainer();
            ret.methodsString = methodsString.toArray(EMPTY_STR);
            ret.methodsRegExp = methodsRegExp.toArray(EMPTY_STR);
            ret.methodsSuffix = methodsSuffix.toArray(EMPTY_STR);
            ret.methodsStrsKt = methodsStrsKt.toArray(EMPTY_STR);
            ret.mods = mods.v;
            cbkJar.accept(ret);
        }
    }

    private static void scanClass(InputStream is, Consumer<String> string, Consumer<String> regexp,
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
                if (node instanceof MethodInsnNode) {
                    MethodInsnNode mNode = ((MethodInsnNode) node);
                    if (mNode.getOpcode() == Opcodes.INVOKEVIRTUAL && mNode.owner.equals("java/lang/String")
                            && mNode.name.equals("contains") && mNode.desc.equals("(Ljava/lang/CharSequence;)Z")) {
                        string.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                        break;
                    } else if (mNode.getOpcode() == Opcodes.INVOKEVIRTUAL && mNode.owner.equals("java/util/regex/Pattern")
                            && mNode.name.equals("matcher")
                            && mNode.desc.equals("(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;")) {
                        regexp.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                        break;
                    } else if (mNode.getOpcode() == Opcodes.INVOKESTATIC && mNode.owner.equals("kotlin/text/StringsKt")
                            && mNode.name.equals("contains") && mNode.desc.equals("(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z")) {
                        strskt.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                        break;
                    }
                } else if (node instanceof TypeInsnNode) {
                    TypeInsnNode tNode = ((TypeInsnNode) node);
                    if (tNode.getOpcode() == Opcodes.NEW && (
                            tNode.desc.equals("net/minecraft/client/util/SuffixArray")
                                    || tNode.desc.equals("cgx")) || tNode.desc.equals("cgz")) {
                        suffix.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc)
                                .replace('/', '.'));
                        break;
                    }
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
}
