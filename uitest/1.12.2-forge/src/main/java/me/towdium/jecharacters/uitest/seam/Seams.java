package me.towdium.jecharacters.uitest.seam;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Seams {

    public static final String match = "me/towdium/jecharacters/util/Match";

    public static final String fakeTree = "me/towdium/jecharacters/util/Match$FakeTree";

    public static final String fakeArray = "me/towdium/jecharacters/util/Match$FakeArray";

    public static final String searchIndex = "me/towdium/jecharacters/JechJeiPlugin$JechSearchIndex";

    private static final String lwjglKeyboard = "org/lwjgl/input/Keyboard";

    private static final String lwjglMouse = "org/lwjgl/input/Mouse";

    private static final String recorder = "me/towdium/jecharacters/uitest/match/MatchRecorder";
    private static final String keyboardSeam = "me/towdium/jecharacters/uitest/input/LwjglKeyboardSeam";
    private static final String mouseSeam = "me/towdium/jecharacters/uitest/input/LwjglMouseSeam";

    private static final String harnessPackage = "me/towdium/jecharacters/uitest/";

    private static final String[] keyboardTargets = {
        "isKeyDown(I)Z",
        "next()Z",
        "getEventCharacter()C",
        "getEventKey()I",
        "getEventKeyState()Z",
        "getEventNanoseconds()J",
        "isRepeatEvent()Z",
    };

    private static final String[] mouseTargets = {
        "isButtonDown(I)Z",
        "next()Z",
        "getEventButton()I",
        "getEventButtonState()Z",
        "getEventDX()I",
        "getEventDY()I",
        "getEventX()I",
        "getEventY()I",
        "getEventDWheel()I",
        "getEventNanoseconds()J",
        "getX()I",
        "getY()I",
        "getDX()I",
        "getDY()I",
    };

    private static final Set<String> keyboardReads = new HashSet<String>(Arrays.asList(keyboardTargets));
    private static final Set<String> mouseReads = new HashSet<String>(Arrays.asList(mouseTargets));

    private Seams() {}

    public static final class Result {
        public final byte[] bytes;
        public final String detail;

        Result(byte[] bytes, String detail) {
            this.bytes = bytes;
            this.detail = detail;
        }
    }

    public static Result transform(String internalName, byte[] basicClass) {
        if (internalName.startsWith("org/lwjgl/") || internalName.startsWith(harnessPackage)) {
            return null;
        }
        List<String> details = new ArrayList<String>();
        boolean jechClass = internalName.equals(match) || internalName.equals(fakeTree)
                || internalName.equals(fakeArray) || internalName.equals(searchIndex);
        if (!jechClass && !mentions(basicClass, lwjglKeyboard) && !mentions(basicClass, lwjglMouse)) {
            return null;
        }
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);

        if (internalName.equals(match)) {
            inject(node, "contains", "(Ljava/lang/String;Ljava/lang/CharSequence;)Z", "recordContains", details);
            inject(node, "contains", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z", "recordContainsSeq", details);
            inject(node, "contains", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z", "recordContainsCase", details);
            inject(node, "equals", "(Ljava/lang/String;Ljava/lang/Object;)Z", "recordEquals", details);
            inject(node, "matches", "(Ljava/lang/String;Ljava/lang/String;)Z", "recordMatches", details);
            inject(node, "rank", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)I", "recordRank", details);
            inject(node, "matcher", "(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;", "recordMatcher", details);
        } else if (internalName.equals(fakeTree)) {
            inject(node, "put", "(Ljava/lang/String;I)V", "rememberIndex", details);
            inject(node, "search", "(Ljava/lang/String;)Lit/unimi/dsi/fastutil/ints/IntSet;", "recordIndexSearch", details);
        } else if (internalName.equals(fakeArray)) {
            inject(node, "search", "(Ljava/lang/String;)Ljava/util/List;", "recordListSearch", details);
        } else if (internalName.equals(searchIndex)) {
            inject(node, "put", "(Ljava/lang/String;Ljava/lang/Object;)V", "rememberValue", details);
            inject(node, "getSearchResults", "(Ljava/lang/String;Ljava/util/Set;)V", "recordSetSearch", details);
        } else {
            int keyboard = Rewriter.redirect(node, lwjglKeyboard, keyboardSeam, keyboardReads);
            int mouse = Rewriter.redirect(node, lwjglMouse, mouseSeam, mouseReads);
            if (keyboard + mouse == 0) {
                return null;
            }
            details.add("keyboard reads -> LwjglKeyboardSeam x" + keyboard);
            details.add("mouse reads -> LwjglMouseSeam x" + mouse);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return new Result(writer.toByteArray(), join(details));
    }

    private static boolean mentions(byte[] basicClass, String internalName) {
        String text = new String(basicClass, java.nio.charset.StandardCharsets.ISO_8859_1);
        return text.contains(internalName);
    }

    private static void inject(ClassNode node, String name, String desc, String hook, List<String> details) {
        MethodNode method = Rewriter.find(node, name, desc);
        if (method == null) {
            details.add(name + desc + " absent");
            return;
        }
        details.add(name + desc + " -> " + hook + " x" + Rewriter.injectAtReturns(method, recorder, hook));
    }

    private static String join(List<String> details) {
        StringBuilder ret = new StringBuilder();
        for (String detail : details) {
            if (ret.length() > 0) ret.append(", ");
            ret.append(detail);
        }
        return ret.toString();
    }
}
