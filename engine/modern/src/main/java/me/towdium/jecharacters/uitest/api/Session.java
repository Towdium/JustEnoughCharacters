package me.towdium.jecharacters.uitest.api;

public enum Session {

    shared,

    reenter,

    fresh;

    public static Session fromString(String raw) {
        for (Session tier : values()) {
            if (tier.name().equalsIgnoreCase(raw.trim())) return tier;
        }
        return shared;
    }
}
