package me.towdium.jecharacters.utils;

import com.google.gson.Gson;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import static me.towdium.jecharacters.JustEnoughCharacters.logger;

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
        ArrayList<String> string = new ArrayList<>();
        ArrayList<String> regExp = new ArrayList<>();
        ArrayList<String> suffix = new ArrayList<>();
        ArrayList<String> strsKt = new ArrayList<>();
        JarContainer ret = new JarContainer();
        f.stream().forEach(entry -> {
            if (entry.getName().endsWith(".class")) {
                try (InputStream is = f.getInputStream(entry)) {
                    long size = entry.getSize() + 4;
                    if (size > Integer.MAX_VALUE) {
                        logger.info("Class file " + entry.getName() + " in jar file " + f.getName() + " is too large, skip.");
                    } else scanClass(is, string::add, regExp::add, suffix::add, strsKt::add);
                } catch (IOException e) {
                    logger.info("Fail to read file " + entry.getName() + " in jar file " + f.getName() + ", skip.");
                }
            } else if (entry.getName().equals("mcmod.info")) {
                Gson gson = new Gson();
                try (InputStream is = f.getInputStream(entry)) {
                    ret.mods = gson.fromJson(new InputStreamReader(is), ModContainer[].class);
                    // ret.mods = new ModContainer[]{gson.fromJson(new InputStreamReader(is), ModContainer.class)};
                } catch (Exception e) {
                    logger.info("Fail to read mod info in jar file " + f.getName() + ", skip.");
                }
            }
        });
        if (!string.isEmpty() || !regExp.isEmpty() || !suffix.isEmpty() || !strsKt.isEmpty()) {
            ret.string = string.toArray(EMPTY_STR);
            ret.regExp = regExp.toArray(EMPTY_STR);
            ret.suffix = suffix.toArray(EMPTY_STR);
            ret.strsKt = strsKt.toArray(EMPTY_STR);
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
                logger.info("File decoding of class " + classNode.name + " failed. Try to continue.");
            } else throw new IOException(e);
        }

        classNode.methods.forEach(methodNode -> {
            Iterator<AbstractInsnNode> it = methodNode.instructions.iterator();
            while (it.hasNext()) {
                AbstractInsnNode node = it.next();
                if (node instanceof MethodInsnNode) {
                    MethodInsnNode mNode = ((MethodInsnNode) node);
                    if (mNode.getOpcode() == Opcodes.INVOKEVIRTUAL && mNode.owner.equals("java/lang/String")
                            && mNode.name.equals("contains") && mNode.desc.equals("(Ljava/lang/CharSequence;)Z")) {
                        string.accept((classNode.name + ":" + methodNode.name + methodNode.desc).replace('/', '.'));
                        break;
                    } else if (mNode.getOpcode() == Opcodes.INVOKEVIRTUAL && mNode.owner.equals("java/lang/String")
                            && mNode.name.equals("matches") && mNode.desc.equals("(Ljava/lang/String;)Z")) {
                        regexp.accept((classNode.name + ":" + methodNode.name + methodNode.desc).replace('/', '.'));
                        break;
                    } else if (mNode.getOpcode() == Opcodes.INVOKEVIRTUAL && mNode.owner.equals("java/util/regex/Pattern")
                            && mNode.name.equals("matcher")
                            && mNode.desc.equals("(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;")) {
                        regexp.accept((classNode.name + ":" + methodNode.name + methodNode.desc).replace('/', '.'));
                        break;
                    } else if (mNode.getOpcode() == Opcodes.INVOKESTATIC && mNode.owner.equals("kotlin/text/StringsKt")
                            && mNode.name.equals("contains") && mNode.desc.equals("(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z")) {
                        strskt.accept((classNode.name + ":" + methodNode.name + methodNode.desc).replace('/', '.'));
                        break;
                    } else if (mNode.getOpcode() == Opcodes.INVOKESTATIC && mNode.owner.equals("kotlin/text/StringsKt")
                            && mNode.name.equals("contains") && mNode.desc.equals("(Ljava/lang/CharSequence;Ljava/lang/CharSequence)Z")) {
                        strskt.accept((classNode.name + ":" + methodNode.name + methodNode.desc).replace('/', '.'));
                        break;
                    }
                } else if (node instanceof TypeInsnNode) {
                    TypeInsnNode tNode = ((TypeInsnNode) node);
                    if (tNode.getOpcode() == Opcodes.NEW && tNode.desc.equals("net/minecraft/client/util/SuffixArray")) {
                        suffix.accept((classNode.name + ":" + methodNode.name + ":" + methodNode.desc).replace('/', '.'));
                        break;
                    }
                }
            }
        });
    }

    public static class Report {
        JarContainer[] jars;
    }

    private static class JarContainer {
        ModContainer[] mods;
        String[] string;
        String[] regExp;
        String[] suffix;
        String[] strsKt;
    }

    private static class ModContainer {
        String modid;
        String name;
        String version;
    }
}
