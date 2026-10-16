package me.towdium.jecharacters.uitest.input;

import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHelper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerEmbodiment {

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

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
        MouseHelper handler = Minecraft.getInstance().mouseHandler;
        if (handler == null || handler.isMouseGrabbed()) {
            return;
        }
        MouseHandlerAccessor accessor = handler instanceof MouseHandlerAccessor
                ? (MouseHandlerAccessor) handler : null;
        if (accessor == null) {
            logger.warn("[uitest] MouseHandlerAccessor is not applied; the camera cannot turn");
            return;
        }
        accessor.setMouseGrabbed(true);
    }

    public static void releaseLook() {
        if (!isLive()) {
            return;
        }
        MouseHelper handler = Minecraft.getInstance().mouseHandler;
        if (handler == null || !handler.isMouseGrabbed()) {
            return;
        }
        MouseHandlerAccessor accessor = handler instanceof MouseHandlerAccessor
                ? (MouseHandlerAccessor) handler : null;
        if (accessor != null) {
            accessor.setMouseGrabbed(false);
        }
    }
}
