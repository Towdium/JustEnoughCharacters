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

/**
 * The classes the harness rewrites, and what it does to each. This is the whole seam table of the
 * 1.12.2 line; the 1.21.1 line implements the same contract with eleven Mixins (six input-seam
 * ones and the accessors, two recorder taps) and needs none of this.
 *
 * <table>
 *   <caption>The seam table</caption>
 *   <tr><th>1.12.2 target</th><th>What is rewritten</th><th>1.21.1 equivalent</th></tr>
 *   <tr>
 *     <td>every game class that polls {@code org.lwjgl.input.Keyboard}</td>
 *     <td>its {@code Keyboard} reads are retargeted at
 *         {@link me.towdium.jecharacters.uitest.input.LwjglKeyboardSeam}: {@code isKeyDown},
 *         {@code next}, {@code getEventCharacter/getEventKey/getEventKeyState/getEventNanoseconds},
 *         {@code isRepeatEvent}. {@code Minecraft} (both keyboard paths),
 *         {@code GuiScreen} ({@code handleKeyboardInput} and its four modifier helpers),
 *         {@code KeyBinding}, {@code GameSettings} and the modded screens are the readers of
 *         record.</td>
 *     <td>{@code KeyboardHandlerMixin}, {@code InputConstantsMixin}, {@code ScreenModifierMixin}</td>
 *   </tr>
 *   <tr>
 *     <td>every game class that polls {@code org.lwjgl.input.Mouse}</td>
 *     <td>the same treatment through
 *         {@link me.towdium.jecharacters.uitest.input.LwjglMouseSeam} for the fourteen mouse reads
 *         vanilla performs</td>
 *     <td>{@code MouseHandlerMixin} plus the two accessors</td>
 *   </tr>
 *   <tr>
 *     <td>{@code me.towdium.jecharacters.util.Match}</td>
 *     <td>a record at the return of each comparison the JECh adapters are rewritten onto</td>
 *     <td>{@code MatchMixin}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code Match$FakeTree}, {@code Match$FakeArray},
 *         {@code JechJeiPlugin$JechSearchIndex}</td>
 *     <td>a record of the JEI-facing search: this line's JEI integration goes through those three,
 *         not through {@code Match}, so they are this line's
 *         {@code JechSearchStorage} equivalent</td>
 *     <td>{@code JechSearchStorageMixin}</td>
 *   </tr>
 * </table>
 *
 * <p><b>Why the input seam is a reader rewrite and not a LWJGL rewrite.</b> Rewriting
 * {@code org.lwjgl.input.Keyboard} itself would be the one-place fix, but it is impossible on this
 * line: launchwrapper 1.12's {@code LaunchClassLoader} hardcodes {@code org.lwjgl.} among its
 * class-loader exclusions and, for any name under that prefix, {@code findClass} delegates straight
 * to the parent loader — so the class is never defined by the loader that runs coremod
 * transformers, and no coremod can ever see it. Confirmed in a real boot: the coremod loads, the
 * {@code Match} taps fire, and the LWJGL classes arrive untransformed. Retargeting the readers
 * needs no special access and no loader of its own, so the seam moved one level up instead of one
 * level down.
 *
 * <p>Every rewrite is optional: a target that is absent (Had Enough Items not loaded, say) is
 * reported and skipped, never guessed at.
 */
public final class Seams {

    /** {@code me.towdium.jecharacters.util.Match} — the comparison funnel. */
    public static final String match = "me/towdium/jecharacters/util/Match";

    /** The legacy-JEI suffix-tree replacement inside {@code Match}. */
    public static final String fakeTree = "me/towdium/jecharacters/util/Match$FakeTree";

    /** The legacy-JEI suffix-array replacement inside {@code Match}. */
    public static final String fakeArray = "me/towdium/jecharacters/util/Match$FakeArray";

    /** The Had Enough Items search index inside JECh's JEI plugin. */
    public static final String searchIndex = "me/towdium/jecharacters/JechJeiPlugin$JechSearchIndex";

    /** The LWJGL class whose reads are retargeted (and which this transformer can never see). */
    private static final String lwjglKeyboard = "org/lwjgl/input/Keyboard";

    /** The other LWJGL class whose reads are retargeted. */
    private static final String lwjglMouse = "org/lwjgl/input/Mouse";

    private static final String recorder = "me/towdium/jecharacters/uitest/match/MatchRecorder";
    private static final String keyboardSeam = "me/towdium/jecharacters/uitest/input/LwjglKeyboardSeam";
    private static final String mouseSeam = "me/towdium/jecharacters/uitest/input/LwjglMouseSeam";

    /**
     * The harness's own classes: the seam talks to the real LWJGL on purpose, so it must never be
     * retargeted at itself.
     */
    private static final String harnessPackage = "me/towdium/jecharacters/uitest/";

    /** The seven {@code Keyboard} reads vanilla performs, as {@code name(descriptor)}. */
    private static final String[] keyboardTargets = {
        "isKeyDown(I)Z",
        "next()Z",
        "getEventCharacter()C",
        "getEventKey()I",
        "getEventKeyState()Z",
        "getEventNanoseconds()J",
        "isRepeatEvent()Z",
    };

    /** The fourteen {@code Mouse} reads vanilla performs. */
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

    /** One class's rewritten bytes and the line the coremod logs for it. */
    public static final class Result {
        public final byte[] bytes;
        public final String detail;

        Result(byte[] bytes, String detail) {
            this.bytes = bytes;
            this.detail = detail;
        }
    }

    /**
     * Rewrites one class, or returns {@code null} when this seam has nothing to do with it.
     *
     * <p>The cheap screen comes first: a class that does not name either LWJGL input class in its
     * constant pool cannot hold a read to retarget, so it is rejected on a byte scan without paying
     * for an ASM parse. That is what lets the seam cover <em>every</em> reader in the game — vanilla,
     * Forge, JEI, JECh — instead of a list of classes that would rot.
     */
    public static Result transform(String internalName, byte[] basicClass) {
        if (internalName.startsWith("org/lwjgl/") || internalName.startsWith(harnessPackage)) {
            // LWJGL itself (unreachable anyway) and the harness's own classes: both must keep
            // calling the real methods.
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
                // It mentions LWJGL but only for something the seam does not own (create, destroy,
                // setGrabbed, ...): leave the class exactly as it is.
                return null;
            }
            details.add("keyboard reads -> LwjglKeyboardSeam x" + keyboard);
            details.add("mouse reads -> LwjglMouseSeam x" + mouse);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return new Result(writer.toByteArray(), join(details));
    }

    /** Whether the class names that class anywhere in its bytes (constant pool included). */
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
