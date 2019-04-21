package me.towdium.jecharacters.match;

import java.util.HashMap;

public enum Keyboard {

    QUANPIN, DAQIAN;

    private static HashMap<String, String> PHONETIC_SYMBOL = new java.util.HashMap<String, String>() {{
        put("a", "ㄚ");
        put("o", "ㄛ");
        put("e", "ㄜ");
        put("er", "ㄦ");
        put("ai", "ㄞ");
        put("ei", "ㄟ");
        put("ao", "ㄠ");
        put("ou", "ㄡ");
        put("an", "ㄢ");
        put("en", "ㄣ");
        put("ang", "ㄤ");
        put("eng", "ㄥ");
        put("ong", "ㄨㄥ");
        put("i", "ㄧ");
        put("ia", "ㄧㄚ");
        put("iao", "ㄧㄠ");
        put("ie", "ㄧㄝ");
        put("iu", "ㄧㄡ");
        put("ian", "ㄧㄢ");
        put("in", "ㄧㄣ");
        put("iang", "ㄧㄤ");
        put("ing", "ㄧㄥ");
        put("iong", "ㄩㄥ");
        put("u", "ㄨ");
        put("ua", "ㄨㄚ");
        put("uo", "ㄨㄛ");
        put("uai", "ㄨㄞ");
        put("ui", "ㄨㄟ");
        put("uan", "ㄨㄢ");
        put("un", "ㄨㄣ");
        put("uang", "ㄨㄤ");
        put("ueng", "ㄨㄥ");
        put("uen", "ㄩㄣ");
        put("v", "ㄩ");
        put("ve", "ㄩㄝ");
        put("van", "ㄩㄢ");
        put("vang", "ㄩㄤ");
        put("vn", "ㄩㄣ");
        put("b", "ㄅ");
        put("p", "ㄆ");
        put("m", "ㄇ");
        put("f", "ㄈ");
        put("d", "ㄉ");
        put("t", "ㄊ");
        put("n", "ㄋ");
        put("l", "ㄌ");
        put("g", "ㄍ");
        put("k", "ㄎ");
        put("h", "ㄏ");
        put("j", "ㄐ");
        put("q", "ㄑ");
        put("x", "ㄒ");
        put("zh", "ㄓ");
        put("ch", "ㄔ");
        put("sh", "ㄕ");
        put("r", "ㄖ");
        put("z", "ㄗ");
        put("c", "ㄘ");
        put("s", "ㄙ");
        put("w", "ㄨ");
        put("y", "ㄧ");
        put("1", "1");
        put("2", "2");
        put("3", "3");
        put("4", "4");
        put("0", "");
        put("", "");
    }};

    private static HashMap<String, String> PHONETIC_SPELL = new HashMap<String, String>() {{
        put("yi", "i");
        put("you", "iu");
        put("yin", "in");
        put("ye", "ie");
        put("ying", "ing");
        put("wu", "u");
        put("wen", "un");
        put("yu", "v");
        put("yue", "ve");
        put("yuan", "van");
        put("yun", "vn");
        put("ju", "jv");
        put("jue", "jve");
        put("juan", "jvan");
        put("jun", "jvn");
        put("qu", "qv");
        put("que", "qve");
        put("quan", "qvan");
        put("qun", "qvn");
        put("xu", "xv");
        put("xue", "xve");
        put("xuan", "xvan");
        put("xun", "xvn");
        put("shi", "sh");
        put("si", "s");
        put("chi", "ch");
        put("ci", "c");
        put("zhi", "zh");
        put("zi", "z");
        put("ri", "r");
    }};
    private static HashMap<Character, String> KEYBOARD_DAQIAN = new HashMap<Character, String>() {{
        put('ㄇ', "a");
        put('ㄖ', "b");
        put('ㄏ', "c");
        put('ㄎ', "d");
        put('ㄍ', "e");
        put('ㄑ', "f");
        put('ㄕ', "g");
        put('ㄘ', "h");
        put('ㄛ', "i");
        put('ㄨ', "j");
        put('ㄜ', "k");
        put('ㄠ', "l");
        put('ㄩ', "m");
        put('ㄙ', "n");
        put('ㄟ', "o");
        put('ㄣ', "p");
        put('ㄆ', "q");
        put('ㄐ', "r");
        put('ㄋ', "s");
        put('ㄔ', "t");
        put('ㄧ', "u");
        put('ㄒ', "v");
        put('ㄊ', "w");
        put('ㄌ', "x");
        put('ㄗ', "y");
        put('ㄈ', "z");
        put('ㄅ', "1");
        put('ㄉ', "2");
        put('ㄓ', "5");
        put('ㄚ', "8");
        put('ㄞ', "9");
        put('ㄢ', "0");
        put('ㄦ', "-");
        put('ㄤ', ";");
        put('ㄝ', ",");
        put('ㄡ', ".");
        put('ㄥ', "/");
        put('1', " ");
        put('2', "6");
        put('3', "3");
        put('4', "4");
    }};

    public static Keyboard get(int n) {
        switch (n) {
            case 0:
                return QUANPIN;
            case 1:
                return DAQIAN;
            default:
                throw new RuntimeException("Unacceptable identifier: " + n + ".");
        }
    }

    String[] separate(String s) {
        if (this == DAQIAN) {
            String str = PHONETIC_SPELL.get(s.substring(0, s.length() - 1));
            if (str != null) s = str + s.charAt(s.length() - 1);
        }

        if (s.startsWith("a") || s.startsWith("e") || s.startsWith("i")
                || s.startsWith("o") || s.startsWith("u")) {
            return new String[]{"", s.substring(0, s.length() - 1), s.substring(s.length() - 1)};
        } else {
            int i = s.length() > 2 && s.charAt(1) == 'h' ? 2 : 1;
            return new String[]{s.substring(0, i), s.substring(i, s.length() - 1), s.substring(s.length() - 1)};
        }
    }

    String keys(String s) {
        if (this == QUANPIN) return s;
        else {
            String symbol = PHONETIC_SYMBOL.get(s);
            if (symbol == null)
                throw new RuntimeException("Unrecognized element: " + s);
            StringBuilder builder = new StringBuilder();
            for (char c : symbol.toCharArray()) builder.append(KEYBOARD_DAQIAN.get(c));
            return builder.toString();
        }
    }
}
