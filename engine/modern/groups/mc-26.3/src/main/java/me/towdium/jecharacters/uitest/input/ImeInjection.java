package me.towdium.jecharacters.uitest.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.PreeditEvent;

import java.lang.reflect.Field;
import java.util.List;

public final class ImeInjection {

    private ImeInjection() {}

    public static void compose(String preedit) {
        PreeditEvent event = new PreeditEvent(preedit, preedit.length(), List.of(preedit), 0);
        RawInputGate.runSynthetic(
            () -> Minecraft.getInstance().keyboardHandler.textEditing(window(), event)
        );
    }

    public static void commit(String text) {
        RawInputGate.runSynthetic(
            () -> Minecraft.getInstance().keyboardHandler.textInput(window(), text)
        );
    }

    public static String focusedText() {
        Object focused = focusedWidget();
        if (!(focused instanceof EditBox box)) {
            throw new IllegalStateException(
                "the focused widget is a " + focused.getClass().getName() + ", which carries no text; "
                    + "the ime steps read the text of a focused EditBox"
            );
        }
        return box.getValue();
    }

    public static boolean focusedPreeditOverlay() {
        Object focused = focusedWidget();
        for (Class<?> type = focused.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!IMEPreeditOverlay.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    return field.get(focused) != null;
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(
                        "the IME pre-edit overlay field of " + type.getName() + " is not readable", e
                    );
                }
            }
        }
        throw new IllegalStateException(
            "the focused widget is a " + focused.getClass().getName()
                + ", which carries no IME pre-edit overlay field"
        );
    }

    private static Object focusedWidget() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            throw new IllegalStateException("no screen is open, so no widget can hold the IME composition");
        }
        Object focused = screen.getFocused();
        if (focused == null) {
            throw new IllegalStateException(
                "the " + screen.getClass().getSimpleName() + " screen has no focused widget, so an IME "
                    + "composition has nowhere to go"
            );
        }
        return focused;
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().handle();
    }
}
