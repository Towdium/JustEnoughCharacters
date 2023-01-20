package me.towdium.jecharacters.mixins.utils;

import com.mojang.datafixers.util.Function6;
import me.towdium.jecharacters.mixins.config.JechMixinPlugin;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public class MixinGenerator {

    public static void generate(Path jarPath) {
        if (jarPath == null) {
            new ClassEntry(null,null);
            return;
        }
        List<ClassEntry> classEntries = new ArrayList<>();

        generateMixinClasses(classEntries, MixinGenerator::generateSuffix, "GeneratedSuffixMixin", true, FeedFetcher.suffix);
        generateMixinClasses(classEntries, MixinGenerator::generateContains, "GeneratedContainsMixin", false, FeedFetcher.contains);
        generateMixinClasses(classEntries, MixinGenerator::generateEquals, "GeneratedEqualsMixin", false, FeedFetcher.equals);
        generateMixinClasses(classEntries, MixinGenerator::generateRegExp, "GeneratedRegexMixin", false, FeedFetcher.regexp);

        if (!classEntries.isEmpty()) {
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(jarPath)) {

                for (ClassEntry entry : classEntries) {
                    out.putArchiveEntry(new ZipArchiveEntry("me/towdium/jecharacters/mixins/updated/" + entry.location.getName()));
                    out.write(entry.bytes());
                    out.closeArchiveEntry();
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void generateMixinClasses(
            List<ClassEntry> classEntries,
            Function6<String, String, String, String, Boolean, Boolean, byte[]> generator,
            String basedName,
            boolean remap,
            Map<String, Boolean> data
    ) {
        int index = 0;
        for (Map.Entry<String, Boolean> entry : data.entrySet()) {
            String[] decode = entry.getKey().split(":");
            if (decode.length == 2) {
                String simpleName = basedName + index;
                String fullName = "me/towdium/jecharacters/mixins/updated/" + simpleName;
                byte[] classBytes = generator.apply(simpleName, fullName, decode[0], decode[1], remap, entry.getValue());
                classEntries.add(new ClassEntry(new File(simpleName + ".class"), classBytes));
                JechMixinPlugin.MIXIN_CLASSES.add("updated." + simpleName);
                index++;
            }
        }
    }

    private static byte[] generateSuffix(
            String simpleName,
            String className,
            String targetClass,
            String targetMethod,
            boolean remap,
            boolean isStatic
    ) {
        ClassWriter cw = generateMixinWithTemplate(simpleName, className, targetClass, remap);

        generateMixinMethod(
                cw,
                className,
                "redirectConstructor",
                "()Lnet/minecraft/client/searchtree/SuffixArray;",
                targetMethod,
                "net/minecraft/client/searchtree/SuffixArray",
                remap,
                isStatic,
                "me/towdium/jecharacters/utils/Match",
                "createFakeArray",
                "()Lnet/minecraft/client/searchtree/SuffixArray;",
                true,
                null, null,
                null, null
        );

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateContains(
            String simpleName,
            String className,
            String targetClass,
            String targetMethod,
            boolean remap,
            boolean isStatic
    ) {
        ClassWriter cw = generateMixinWithTemplate(simpleName, className, targetClass, remap);

        generateMixinMethod(
                cw,
                className,
                "redirectContains",
                "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                targetMethod,
                "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z",
                remap,
                isStatic,
                "me/towdium/jecharacters/utils/Match",
                "contains",
                "(Ljava/lang/String;Ljava/lang/CharSequence;)Z",
                false,
                "haystack", "Ljava/lang/String;",
                "needle", "Ljava/lang/CharSequence;"
        );

        generateMixinMethod(
                cw,
                className,
                "redirectContainsKt1",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                targetMethod,
                "Lkotlin/text/StringsKt;contains(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                remap,
                isStatic,
                "me/towdium/jecharacters/utils/Match",
                "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z",
                false,
                "haystack", "Ljava/lang/String;",
                "needle", "Ljava/lang/CharSequence;",
                "ignoreCase", "Z"
        );

        generateMixinMethod(
                cw,
                className,
                "redirectContainsKt2",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z",
                targetMethod,
                "Lkotlin/text/StringsKt;contains(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z",
                remap,
                isStatic,
                "me/towdium/jecharacters/utils/Match",
                "contains",
                "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z",
                false,
                "haystack", "Ljava/lang/String;",
                "needle", "Ljava/lang/CharSequence;"
        );

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateEquals(
            String simpleName,
            String className,
            String targetClass,
            String targetMethod,
            boolean remap,
            boolean isStatic
    ) {
        ClassWriter cw = generateMixinWithTemplate(simpleName, className, targetClass, remap);

        generateMixinMethod(
                cw,
                className,
                "redirectEquals",
                "(Ljava/lang/String;Ljava/lang/Object;)Z",
                targetMethod,
                "Ljava/lang/String;equals(Ljava/lang/Object;)Z",
                remap,
                isStatic,
                "me/towdium/jecharacters/utils/Match",
                "equals",
                "(Ljava/lang/String;Ljava/lang/Object;)Z",
                false,
                "string", "Ljava/lang/String;",
                "object", "Ljava/lang/Object;"
        );

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateRegExp(
            String simpleName,
            String className,
            String targetClass,
            String targetMethod,
            boolean remap,
            boolean isStatic
    ) {
        ClassWriter cw = generateMixinWithTemplate(simpleName, className, targetClass, remap);

        generateMixinMethod(
                cw,
                className,
                "redirectRegx",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                targetMethod,
                "Ljava/util/regex/Pattern;matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                remap,
                isStatic,
                "me/towdium/jecharacters/utils/Match",
                "matcher",
                "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;",
                true,
                "pattern", "Ljava/util/regex/Pattern;",
                "sequence", "Ljava/lang/CharSequence;"
        );

        generateMixinMethod(
                cw,
                className,
                "redirectRegx",
                "(Ljava/lang/String;Ljava/lang/String;)Z",
                targetMethod,
                "Ljava/lang/String;matches(Ljava/lang/String;)Z",
                remap,
                isStatic,
                "me/towdium/jecharacters/utils/Match",
                "matches",
                "(Ljava/lang/String;Ljava/lang/String;)Z",
                false,
                "s1", "Ljava/lang/String;",
                "s2", "Ljava/lang/String;"
        );

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static ClassWriter generateMixinWithTemplate(
            String simpleName,
            String className,
            String targetClass,
            boolean remap
    ) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V17, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object", null);
        cw.visitSource(simpleName + ".java", null);
        {
            AnnotationVisitor av0 = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Pseudo;", false);
            av0.visitEnd();
        }
        {
            AnnotationVisitor av0 = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
            {
                AnnotationVisitor av1 = av0.visitArray("targets");
                av1.visit(null, targetClass);
                av1.visitEnd();
            }
            av0.visit("remap", remap);
            av0.visitEnd();
        }

        {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitLineNumber(16, label0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            Label label1 = new Label();
            mv.visitLabel(label1);
            mv.visitLocalVariable("this", "L" + className + ";", null, label0, label1, 0);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        return cw;
    }

    private static void generateMixinMethod(
            ClassWriter cw,
            String className,
            String methodName,
            String methodDesc,
            String targetMethod,
            String atTarget,
            boolean remap,
            boolean isStatic,
            String owner,
            String name,
            String desc,
            boolean returnObj,
            String paramName1,
            String paramType1,
            String paramName2,
            String paramType2
    ) {
        generateMixinMethod(
                cw,
                className,
                methodName,
                methodDesc,
                targetMethod,
                atTarget,
                remap,
                isStatic,
                owner,
                name,
                desc,
                returnObj,
                paramName1, paramType1,
                paramName2, paramType2,
                null, null
        );
    }

    private static void generateMixinMethod(
            ClassWriter cw,
            String className,
            String methodName,
            String methodDesc,
            String targetMethod,
            String atTarget,
            boolean remap,
            boolean isStatic,
            String owner,
            String name,
            String desc,
            boolean returnObj,
            String paramName1,
            String paramType1,
            String paramName2,
            String paramType2,
            String paramName3,
            String paramType3
    ) {
        int access = isStatic ? ACC_PRIVATE | ACC_STATIC : ACC_PRIVATE;
        MethodVisitor mv = cw.visitMethod(access, methodName, methodDesc, null, null);
        {
            AnnotationVisitor av0 = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Redirect;", true);
            {
                AnnotationVisitor av1 = av0.visitArray("method");
                av1.visit(null, targetMethod);
                av1.visitEnd();
            }
            {
                AnnotationVisitor av1 = av0.visitAnnotation("at", "Lorg/spongepowered/asm/mixin/injection/At;");
                av1.visit("value", "INVOKE");
                av1.visit("target", atTarget);
                av1.visit("remap", remap);
                av1.visitEnd();
            }
            av0.visitEnd();
        }
        mv.visitCode();
        Label label0 = new Label();
        mv.visitLabel(label0);
        mv.visitLineNumber(27, label0);

        int based = isStatic ? 0 : 1;

        if (paramName1 != null || !isStatic) {
            mv.visitVarInsn(ALOAD, based);
        }
        if (paramName2 != null) {
            mv.visitVarInsn(ALOAD, based + 1);
        }
        if (paramName3 != null) {
            mv.visitVarInsn(ALOAD, based + 2);
        }
        mv.visitMethodInsn(INVOKESTATIC, owner, name, desc, false);
        if (returnObj) {
            mv.visitInsn(ARETURN);
        } else {
            mv.visitInsn(IRETURN);
        }
        Label label1 = new Label();
        mv.visitLabel(label1);
        if (!isStatic) {
            mv.visitLocalVariable("this", "L" + className + ";", null, label0, label1, 0);
        }
        if (paramName1 != null) {
            mv.visitLocalVariable(paramName1, paramType1, null, label0, label1, based);
        }
        if (paramName2 != null) {
            mv.visitLocalVariable(paramName2, paramType2, null, label0, label1, based + 1);
        }
        if (paramName3 != null) {
            mv.visitLocalVariable(paramName3, paramType3, null, label0, label1, based + 2);

        }
        mv.visitEnd();
    }

    private record ClassEntry(File location, byte[] bytes) {

    }

}
