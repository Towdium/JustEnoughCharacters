package me.towdium.jecharacters.uitest.input;

import net.minecraft.client.util.InputMappings;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import me.towdium.jecharacters.uitest.api.InputDriver;
import me.towdium.jecharacters.uitest.api.PlayerInput;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHelper;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyntheticInputDriver extends InputDriver implements KeyState.Source, CursorState.Source {

    private static final Logger logger = LoggerFactory.getLogger("jechtest/uitest");

    private final IntSet heldKeys = new IntOpenHashSet();

    private final WorldPlayerInput playerInput = new WorldPlayerInput(this);

    private float pointerX = parked;
    private float pointerY = parked;
    private boolean installed;

    @Override
    public void install() {
        heldKeys.clear();
        pointerX = parked;
        pointerY = parked;
        KeyState.setSource(this);
        RawInputGate.block(this);
        if (usesVirtualCursor()) {
            CursorState.setSource(this);
        }
        installed = true;
        dropMouseGrabWithoutWarping();
    }

    @Override
    public void uninstall() {
        installed = false;
        try {
            playerInput.releaseAll();
        } catch (RuntimeException e) {
            logger.warn("[uitest] releasing the embodied player input failed", e);
        }
        CursorState.clearSource(this);
        KeyState.clearSource(this);
        RawInputGate.unblock(this);
        heldKeys.clear();
        pointerX = parked;
        pointerY = parked;
    }

    @Override
    public PlayerInput playerInput() {
        return playerInput;
    }

    void markKeyHeld(int keyCode) {
        heldKeys.add(keyCode);
    }

    void markKeyReleased(int keyCode) {
        heldKeys.remove(keyCode);
    }

    @Override
    public boolean usesVirtualCursor() {
        return true;
    }

    @Override
    public void placeCursor(float xGui, float yGui) {
        setPointer(xGui, yGui);
        refreshHover();
    }

    @Override
    public boolean isKeyDown(int keyCode) {
        return heldKeys.contains(keyCode);
    }

    @Override
    public float cursorX() {
        return pointerX;
    }

    @Override
    public float cursorY() {
        return pointerY;
    }

    @Override
    public void moveTo(Screen screen, float x, float y) {
        placeCursor(x, y);
        screen.mouseMoved(x, y);
    }

    @Override
    public void mouseDown(Screen screen, float x, float y, int button) {
        placeCursor(x, y);
        screen.mouseClicked(x, y, button);
    }

    @Override
    public void mouseUp(Screen screen, float x, float y, int button) {
        placeCursor(x, y);
        screen.mouseReleased(x, y, button);
    }

    @Override
    public void dragTo(Screen screen, float x, float y, int button, float deltaX, float deltaY) {
        placeCursor(x, y);
        screen.mouseMoved(x, y);
        screen.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public void scroll(Screen screen, float x, float y, double amount) {
        placeCursor(x, y);
        screen.mouseScrolled(x, y, amount);
    }

    @Override
    public void keyDown(Screen screen, int keyCode, int scanCode, int modifiers) {
        heldKeys.add(keyCode);
        screen.keyPressed(keyCode, effectiveScanCode(keyCode, scanCode), modifiers | heldModifiers());
    }

    @Override
    public void keyUp(Screen screen, int keyCode, int scanCode, int modifiers) {
        screen.keyReleased(keyCode, effectiveScanCode(keyCode, scanCode), modifiers | heldModifiers());
        heldKeys.remove(keyCode);
    }

    @Override
    public void charTyped(Screen screen, int codePoint, int modifiers) {
        screen.charTyped((char) codePoint, modifiers);
    }

    @Override
    public boolean installed() {
        return installed;
    }

    protected void setPointer(float xGui, float yGui) {
        pointerX = xGui;
        pointerY = yGui;
    }

    protected void refreshHover() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.mouseMoved(pointerX, pointerY);
        }
    }

    private int heldModifiers() {
        int modifiers = 0;
        IntIterator iterator = heldKeys.iterator();
        while (iterator.hasNext()) {
            modifiers |= Keys.modifierMaskOf(iterator.nextInt());
        }
        return modifiers;
    }

    private static int effectiveScanCode(int keyCode, int scanCode) {
        return scanCode > 0 ? scanCode : Keys.scanCodeOf(keyCode);
    }

    protected void dropMouseGrabWithoutWarping() {
        Minecraft minecraft = Minecraft.getInstance();
        MouseHelper mouseHandler = minecraft.mouseHandler;
        if (mouseHandler == null || !mouseHandler.isMouseGrabbed()) {
            return;
        }
        MouseHandlerAccessor accessor = mouseHandler instanceof MouseHandlerAccessor
                ? (MouseHandlerAccessor) mouseHandler : null;
        if (accessor == null) {
            logger.warn("[uitest] MouseHandlerAccessor is not applied; the mouse grab stays in place");
            return;
        }
        long window = minecraft.getWindow().getWindow();
        double[] cursorXOut = new double[1];
        double[] cursorYOut = new double[1];
        GLFW.glfwGetCursorPos(window, cursorXOut, cursorYOut);
        accessor.setMouseGrabbed(false);
        InputMappings.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_NORMAL, cursorXOut[0], cursorYOut[0]);
    }
}
