package me.towdium.jecharacters.uitest.input;

import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.jspecify.annotations.Nullable;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public final class PlayerEmbodiment {

    private static final Logger logger = LogManager.getLogger("jechtest/uitest");

    private static volatile @Nullable Object owner;

    private PlayerEmbodiment() {}

    public static void activate(Object newOwner) {
        owner = newOwner;
    }

    public static void deactivate(Object currentOwner) {
        if (owner == currentOwner) {
            owner = null;
        }
    }

    public static boolean isLive() {
        return owner != null;
    }

    public static boolean isOwnedBy(Object candidate) {
        return owner == candidate;
    }

    public static void takeLook() {
        MouseHandler handler = Minecraft.getInstance().mouseHandler;
        if (handler == null || handler.isMouseGrabbed()) {
            return;
        }
        if (!(handler instanceof MouseHandlerAccessor)) {
            logger.warn("[uitest] MouseHandlerAccessor is not applied; the camera cannot turn");
            return;
        }
        ((MouseHandlerAccessor) handler).setMouseGrabbed(true);
    }

    public static void releaseLook() {
        if (!isLive()) {
            return;
        }
        MouseHandler handler = Minecraft.getInstance().mouseHandler;
        if (handler == null || !handler.isMouseGrabbed()) {
            return;
        }
        if (handler instanceof MouseHandlerAccessor) {
            ((MouseHandlerAccessor) handler).setMouseGrabbed(false);
        }
    }
}
