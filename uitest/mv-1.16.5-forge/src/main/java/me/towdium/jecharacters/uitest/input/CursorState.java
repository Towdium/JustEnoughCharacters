package me.towdium.jecharacters.uitest.input;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class CursorState {

    public interface Source {

        float cursorX();

        float cursorY();
    }

    private static volatile @Nullable Source source;

    private CursorState() {}

    public static void setSource(Source newSource) {
        source = newSource;
    }

    public static void clearSource(Source owner) {
        if (source == owner) {
            source = null;
        }
    }

    public static @Nullable Source source() {
        return source;
    }

    public static boolean isInstalled() {
        return source != null;
    }

    public static float cursorX() {
        Source current = source;
        return current != null ? current.cursorX() : 0f;
    }

    public static float cursorY() {
        Source current = source;
        return current != null ? current.cursorY() : 0f;
    }

    public static double toPhysicalX(float guiX) {
        MainWindow window = window();
        return guiToPhysical(guiX, window.getScreenWidth(), window.getGuiScaledWidth());
    }

    public static double toPhysicalY(float guiY) {
        MainWindow window = window();
        return guiToPhysical(guiY, window.getScreenHeight(), window.getGuiScaledHeight());
    }

    public static float toGuiX(double physicalX) {
        MainWindow window = window();
        return (float) physicalToGui(physicalX, window.getScreenWidth(), window.getGuiScaledWidth());
    }

    public static float toGuiY(double physicalY) {
        MainWindow window = window();
        return (float) physicalToGui(physicalY, window.getScreenHeight(), window.getGuiScaledHeight());
    }

    public static double guiToPhysical(double gui, int screenExtent, int guiScaledExtent) {
        return gui * screenExtent / Math.max(1, guiScaledExtent);
    }

    public static double physicalToGui(double physical, int screenExtent, int guiScaledExtent) {
        return physical * Math.max(1, guiScaledExtent) / Math.max(1, screenExtent);
    }

    private static MainWindow window() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow();
    }
}
