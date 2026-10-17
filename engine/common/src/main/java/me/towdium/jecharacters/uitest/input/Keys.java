package me.towdium.jecharacters.uitest.input;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Keys {

    public static final int release = 0;

    public static final int press = 1;

    public static final int shiftMask = 0x0001;

    public static final int controlMask = 0x0002;

    public static final int altMask = 0x0004;

    public static final int superMask = 0x0008;

    public static final int space = 32;

    public static final int apostrophe = 39;

    public static final int comma = 44;

    public static final int minus = 45;

    public static final int period = 46;

    public static final int slash = 47;

    public static final int zero = 48;

    public static final int nine = 57;

    public static final int semicolon = 59;

    public static final int equals = 61;

    public static final int a = 65;

    public static final int c = 67;

    public static final int v = 86;

    public static final int x = 88;

    public static final int z = 90;

    public static final int leftBracket = 91;

    public static final int backslash = 92;

    public static final int rightBracket = 93;

    public static final int grave = 96;

    public static final int escape = 256;

    public static final int enter = 257;

    public static final int tab = 258;

    public static final int backspace = 259;

    public static final int insert = 260;

    public static final int delete = 261;

    public static final int right = 262;

    public static final int left = 263;

    public static final int down = 264;

    public static final int up = 265;

    public static final int pageUp = 266;

    public static final int pageDown = 267;

    public static final int home = 268;

    public static final int end = 269;

    public static final int capsLock = 280;

    public static final int f1 = 290;

    public static final int f12 = 301;

    public static final int kp0 = 320;

    public static final int kp9 = 329;

    public static final int kpDecimal = 330;

    public static final int kpDivide = 331;

    public static final int kpMultiply = 332;

    public static final int kpSubtract = 333;

    public static final int kpEqual = 334;

    public static final int kpEnter = 335;

    public static final int leftShift = 340;

    public static final int leftControl = 341;

    public static final int leftAlt = 342;

    public static final int leftSuper = 343;

    public static final int rightShift = 344;

    public static final int rightControl = 345;

    public static final int rightAlt = 346;

    public static final int rightSuper = 347;

    public static final int menu = 348;

    private static final Class<?> glfw = optionalClass("org.lwjgl.glfw.GLFW");

    private static final Class<?> sdl = optionalClass("org.lwjgl.sdl.SDLKeyboard");

    private static final boolean sdlKeys = sdl != null || glfw == null;

    private static final Method glfwScanCode = glfwMethod("glfwGetKeyScancode", int.class);

    private static final Method glfwKeyName = glfwMethod("glfwGetKeyName", int.class, int.class);

    private static final Map<String, Integer> named = namedKeys();

    private Keys() {}

    public static int translate(int literal) {
        if (!sdlKeys) {
            return literal;
        }
        int scancode = sdlScanCode(literal);
        if (scancode == 0) {
            throw new IllegalArgumentException("harness key " + literal + " has no SDL scancode");
        }
        return scancode;
    }

    public static int key(String name) {
        String bare = name.trim();
        if (bare.startsWith("key.keyboard.")) {
            bare = bare.substring("key.keyboard.".length());
        }
        String constant = bare.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        if (!sdlKeys) {
            Integer literal = glfwConstant("GLFW_KEY_" + constant);
            if (literal == null || literal < 1) {
                throw new IllegalArgumentException(
                    "key '" + name + "' is not a key name this harness knows"
                        + " (expected e.g. 'f', 'escape', 'left_control')"
                );
            }
            return literal;
        }
        Integer literal = named.get(constant);
        if (literal == null) {
            throw new IllegalArgumentException(
                "key '" + name + "' is not a key name this harness knows"
                    + " (expected e.g. 'f', 'escape', 'left_control')"
            );
        }
        return literal;
    }

    public static int scanCodeOf(int clientKey) {
        if (sdlKeys) {
            return Math.max(clientKey, 0);
        }
        if (glfwScanCode == null) {
            return 0;
        }
        try {
            return Math.max((Integer) glfwScanCode.invoke(null, clientKey), 0);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    public static int shortcutKey(int clientKey) {
        if (!sdlKeys) {
            return clientKey;
        }
        if (clientKey >= 4 && clientKey <= 29) {
            return clientKey + 93;
        }
        if (clientKey >= 30 && clientKey <= 38) {
            return clientKey + 19;
        }
        return switch (clientKey) {
            case 39 -> '0';
            case 40 -> '\r';
            case 41 -> 27;
            case 42 -> '\b';
            case 43 -> '\t';
            case 44 -> ' ';
            case 45 -> '-';
            case 46 -> '=';
            case 47 -> '[';
            case 48 -> ']';
            case 49 -> '\\';
            case 51 -> ';';
            case 52 -> '\'';
            case 53 -> '`';
            case 54 -> ',';
            case 55 -> '.';
            case 56 -> '/';
            case 79 -> 0x4000004F;
            case 80 -> 0x40000050;
            case 81 -> 0x40000051;
            case 82 -> 0x40000052;
            case 88 -> 0x40000058;
            case 103 -> 0x40000067;
            case 224 -> 0x400000E0;
            case 225 -> 0x400000E1;
            case 226 -> 0x400000E2;
            case 227 -> 0x400000E3;
            case 228 -> 0x400000E4;
            case 229 -> 0x400000E5;
            case 230 -> 0x400000E6;
            case 231 -> 0x400000E7;
            default -> 0;
        };
    }

    public static int modifierMaskOf(int clientKey) {
        if (sdlKeys) {
            return switch (clientKey) {
                case 225, 229 -> shiftMask;
                case 224, 228 -> controlMask;
                case 226, 230 -> altMask;
                case 227, 231 -> superMask;
                default -> 0;
            };
        }
        return switch (clientKey) {
            case leftShift, rightShift -> shiftMask;
            case leftControl, rightControl -> controlMask;
            case leftAlt, rightAlt -> altMask;
            case leftSuper, rightSuper -> superMask;
            default -> 0;
        };
    }

    public static int modifierBits(int mask) {
        if (!sdlKeys) {
            return mask;
        }
        int bits = 0;
        if ((mask & shiftMask) != 0) {
            bits |= 0x0001;
        }
        if ((mask & controlMask) != 0) {
            bits |= 0x0040;
        }
        if ((mask & altMask) != 0) {
            bits |= 0x0100;
        }
        if ((mask & superMask) != 0) {
            bits |= 0x0400;
        }
        return bits;
    }

    public static boolean isModifier(int clientKey) {
        return modifierMaskOf(clientKey) != 0;
    }

    public static String name(int clientKey) {
        if (!sdlKeys) {
            if (glfwKeyName == null) {
                return Integer.toString(clientKey);
            }
            try {
                String name = (String) glfwKeyName.invoke(null, clientKey, 0);
                return name == null ? Integer.toString(clientKey) : name;
            } catch (ReflectiveOperationException e) {
                return Integer.toString(clientKey);
            }
        }
        return sdlName(clientKey);
    }

    public static boolean isPrintable(int codePoint) {
        return codePoint >= 32 && codePoint != 127;
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method glfwMethod(String name, Class<?>... parameters) {
        if (glfw == null) {
            return null;
        }
        try {
            return glfw.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Integer glfwConstant(String name) {
        if (glfw == null) {
            return null;
        }
        try {
            Field field = glfw.getField(name);
            Object value = field.get(null);
            return value instanceof Integer code ? code : null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    private static int sdlScanCode(int literal) {
        if (literal >= a && literal <= z) {
            return literal - a + 4;
        }
        if (literal >= zero && literal <= nine) {
            return literal == zero ? 39 : literal - '1' + 30;
        }
        if (literal >= f1 && literal <= f12) {
            return literal - f1 + 58;
        }
        if (literal >= kp0 && literal <= kp9) {
            return literal == kp0 ? 98 : literal - kp0 + 88;
        }
        return switch (literal) {
            case space -> 44;
            case apostrophe -> 52;
            case comma -> 54;
            case minus -> 45;
            case period -> 55;
            case slash -> 56;
            case semicolon -> 51;
            case equals -> 46;
            case leftBracket -> 47;
            case backslash -> 49;
            case rightBracket -> 48;
            case grave -> 53;
            case escape -> 41;
            case enter -> 40;
            case tab -> 43;
            case backspace -> 42;
            case insert -> 73;
            case delete -> 76;
            case right -> 79;
            case left -> 80;
            case down -> 81;
            case up -> 82;
            case pageUp -> 75;
            case pageDown -> 78;
            case home -> 74;
            case end -> 77;
            case capsLock -> 57;
            case kpDecimal -> 99;
            case kpDivide -> 84;
            case kpMultiply -> 85;
            case kpSubtract -> 86;
            case kpEqual -> 103;
            case kpEnter -> 88;
            case menu -> 101;
            case leftControl -> 224;
            case leftShift -> 225;
            case leftAlt -> 226;
            case leftSuper -> 227;
            case rightControl -> 228;
            case rightShift -> 229;
            case rightAlt -> 230;
            case rightSuper -> 231;
            default -> 0;
        };
    }

    private static String sdlName(int scancode) {
        if (scancode >= 4 && scancode <= 29) {
            return String.valueOf((char) ('a' + scancode - 4));
        }
        if (scancode >= 30 && scancode <= 38) {
            return String.valueOf((char) ('1' + scancode - 30));
        }
        if (scancode >= 58 && scancode <= 69) {
            return "f" + (scancode - 58 + 1);
        }
        if (scancode >= 89 && scancode <= 97) {
            return "kp_" + (scancode - 89 + 1);
        }
        return switch (scancode) {
            case 39 -> "0";
            case 40 -> "enter";
            case 41 -> "escape";
            case 42 -> "backspace";
            case 43 -> "tab";
            case 44 -> "space";
            case 45 -> "minus";
            case 46 -> "equal";
            case 47 -> "left_bracket";
            case 48 -> "right_bracket";
            case 49 -> "backslash";
            case 51 -> "semicolon";
            case 52 -> "apostrophe";
            case 53 -> "grave";
            case 54 -> "comma";
            case 55 -> "period";
            case 56 -> "slash";
            case 57 -> "caps_lock";
            case 73 -> "insert";
            case 74 -> "home";
            case 75 -> "page_up";
            case 76 -> "delete";
            case 77 -> "end";
            case 78 -> "page_down";
            case 79 -> "right";
            case 80 -> "left";
            case 81 -> "down";
            case 82 -> "up";
            case 88 -> "kp_enter";
            case 98 -> "kp_0";
            case 99 -> "kp_decimal";
            case 101 -> "menu";
            case 103 -> "kp_equal";
            case 224 -> "left_control";
            case 225 -> "left_shift";
            case 226 -> "left_alt";
            case 227 -> "left_super";
            case 228 -> "right_control";
            case 229 -> "right_shift";
            case 230 -> "right_alt";
            case 231 -> "right_super";
            default -> Integer.toString(scancode);
        };
    }

    private static Map<String, Integer> namedKeys() {
        Map<String, Integer> keys = new HashMap<>();
        for (int i = 0; i < 26; i++) {
            keys.put(String.valueOf((char) ('A' + i)), a + i);
        }
        for (int i = 0; i < 10; i++) {
            keys.put(Integer.toString(i), zero + i);
        }
        for (int i = 0; i < 12; i++) {
            keys.put("F" + (i + 1), f1 + i);
        }
        for (int i = 0; i < 10; i++) {
            keys.put("KP_" + i, kp0 + i);
        }
        keys.put("SPACE", space);
        keys.put("APOSTROPHE", apostrophe);
        keys.put("COMMA", comma);
        keys.put("MINUS", minus);
        keys.put("PERIOD", period);
        keys.put("SLASH", slash);
        keys.put("SEMICOLON", semicolon);
        keys.put("EQUAL", equals);
        keys.put("LEFT_BRACKET", leftBracket);
        keys.put("BACKSLASH", backslash);
        keys.put("RIGHT_BRACKET", rightBracket);
        keys.put("GRAVE_ACCENT", grave);
        keys.put("ESCAPE", escape);
        keys.put("ENTER", enter);
        keys.put("TAB", tab);
        keys.put("BACKSPACE", backspace);
        keys.put("INSERT", insert);
        keys.put("DELETE", delete);
        keys.put("RIGHT", right);
        keys.put("LEFT", left);
        keys.put("DOWN", down);
        keys.put("UP", up);
        keys.put("PAGE_UP", pageUp);
        keys.put("PAGE_DOWN", pageDown);
        keys.put("HOME", home);
        keys.put("END", end);
        keys.put("CAPS_LOCK", capsLock);
        keys.put("KP_DECIMAL", kpDecimal);
        keys.put("KP_DIVIDE", kpDivide);
        keys.put("KP_MULTIPLY", kpMultiply);
        keys.put("KP_SUBTRACT", kpSubtract);
        keys.put("KP_EQUAL", kpEqual);
        keys.put("KP_ENTER", kpEnter);
        keys.put("LEFT_SHIFT", leftShift);
        keys.put("LEFT_CONTROL", leftControl);
        keys.put("LEFT_ALT", leftAlt);
        keys.put("LEFT_SUPER", leftSuper);
        keys.put("RIGHT_SHIFT", rightShift);
        keys.put("RIGHT_CONTROL", rightControl);
        keys.put("RIGHT_ALT", rightAlt);
        keys.put("RIGHT_SUPER", rightSuper);
        keys.put("MENU", menu);
        return Map.copyOf(keys);
    }
}
