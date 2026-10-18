package me.towdium.jecharacters.uitest.input;

import me.towdium.jecharacters.uitest.platform.ClientPlatform;

public final class ImeSupport {

    private ImeSupport() {}

    public static UnsupportedOperationException unsupported(String minecraftVersion) {
        return new UnsupportedOperationException(
            "this harness row cannot drive IME composition: its Minecraft " + minecraftVersion
                + " has no KeyboardHandler.textInput(long, String) / textEditing(long, PreeditEvent) pair to "
                + "inject a pre-edit and a commit through. The ime_compose / ime_commit / assert_focus_text / "
                + "assert_focus_preedit steps are wired for the MC 26.3 rows, and this row refuses the step "
                + "instead of skipping it"
        );
    }

    public static String rowVersion() {
        ClientPlatform platform = ClientPlatform.current();
        return platform == null ? "version" : platform.minecraftVersion();
    }
}
