package me.towdium.jecharacters.fabric;

import me.towdium.jecharacters.config.JechConfigFabric;

public class ModConfigImpl {



    public static void register() {
        JechConfigFabric.register();
    }

    public static void loadConfig() {
        JechConfigFabric.loadConfig();
    }


    public static void reload() {
        loadConfig();
    }

    public static void save() {
        JechConfigFabric.save();
    }
}
