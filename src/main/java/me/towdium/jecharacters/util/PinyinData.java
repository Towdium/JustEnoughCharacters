package me.towdium.jecharacters.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class PinyinData {
    private static String[][] data;

    static {
        data = new String[41000][];
        String resourceName = "/assets/jecharacters/pinyin.txt";
        BufferedReader br = new BufferedReader(new InputStreamReader(
                PinyinData.class.getResourceAsStream(resourceName)));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                String hex = line.substring(0, 4);
                String sounds = line.substring(6);
                data[Integer.parseInt(hex, 16)] = sounds.split(", ");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < 41000; i++)
            if (data[i] == null) data[i] = new String[0];
    }

    public static String[] get(char ch) {
        return data[(int) ch];
    }
}
