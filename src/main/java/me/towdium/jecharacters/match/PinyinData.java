package me.towdium.jecharacters.match;

import me.towdium.jecharacters.core.JechCore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class PinyinData {
    private static String[][] data;
    private static final String[] EMPTY = new String[0];
    private static String[][] extra;

    static {
        JechCore.LOG.info("Starting loading pinyin data");

        data = new String[41000][];
        String resourceName = "/assets/jecharacters/pinyin.txt";
        BufferedReader br = new BufferedReader(new InputStreamReader(
                PinyinData.class.getResourceAsStream(resourceName), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                char ch = line.charAt(0);
                String sounds = line.substring(3);
                data[ch] = sounds.split(", ");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        data[0x9FCF] = new String[]{"mai4"};   // 钅麦
        data[0x9FD4] = new String[]{"ge1"};    // 钅哥
        data[0x9FED] = new String[]{"ni3"};    // 钅尔
        data[0x9FEC] = new String[]{"tian2"};  // 石田
        data[0x9FEB] = new String[]{"ao4"};    // 奥气

        for (int i = 0; i < 41000; i++)
            if (data[i] == null) data[i] = EMPTY;

        // https://github.com/CFPAOrg/Minecraft-Mod-Language-Package/blob/main/config/fontmap.txt
        extra = new String[256][];
        extra[0] = new String[]{"lu2"};   // 钅卢
        extra[1] = new String[]{"du4"};   // 钅杜
        extra[2] = new String[]{"xi3"};   // 钅喜
        extra[3] = new String[]{"bo1"};   // 钅波
        extra[4] = new String[]{"hei1"};  // 钅黑
        extra[6] = new String[]{"da2"};   // 钅达
        extra[7] = new String[]{"lun2"};  // 钅仑
        extra[10] = new String[]{"fu1"};  // 钅夫
        extra[12] = new String[]{"li4"};  // 钅立

        for (int i = 0; i < 256; i++)
            if (extra[i] == null) extra[i] = EMPTY;

        JechCore.LOG.info("Finished loading pinyin data");
    }

    public static String[] get(char ch) {
        if (ch < 0xA000) {
            return data[ch];
        } else {
            return extra[ch - 0xE900];
        }
    }
}
