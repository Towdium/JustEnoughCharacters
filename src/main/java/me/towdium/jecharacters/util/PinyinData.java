package me.towdium.jecharacters.util;

import me.towdium.jecharacters.core.JechCore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class PinyinData {
    private static String[][] data;
    private static final String[] EMPTY = new String[0];

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

        for (int i = 0; i < 41000; i++)
            if (data[i] == null) data[i] = EMPTY;

        JechCore.LOG.info("Finished loading pinyin data");
    }

    public static String[] get(char ch) {
        return data[(int) ch];
    }
}
