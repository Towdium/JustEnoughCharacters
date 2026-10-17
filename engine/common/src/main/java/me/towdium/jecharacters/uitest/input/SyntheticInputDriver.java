package me.towdium.jecharacters.uitest.input;

import me.towdium.jecharacters.uitest.api.InputDriver;
import me.towdium.jecharacters.uitest.api.PlayerInput;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SyntheticInputDriver extends InputDriver implements KeyState.Source, CursorState.Source {

    private static final Logger logger = LogManager.getLogger("jechtest/uitest");

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
    public void moveTo(Object screen, float x, float y) {
        placeCursor(x, y);
        ScreenInput.current().mouseMoved(screen, x, y);
    }

    @Override
    public void mouseDown(Object screen, float x, float y, int button) {
        placeCursor(x, y);
        ScreenInput.current().mouseClicked(screen, x, y, button, heldModifiers());
    }

    @Override
    public void mouseUp(Object screen, float x, float y, int button) {
        placeCursor(x, y);
        ScreenInput.current().mouseReleased(screen, x, y, button, heldModifiers());
    }

    @Override
    public void mouseDownThroughHandler(Object screen, float x, float y, int button) {
        placeCursor(x, y);
        ScreenInput.current().clickThroughHandler(x, y, button, Keys.press);
    }

    @Override
    public void mouseUpThroughHandler(Object screen, float x, float y, int button) {
        placeCursor(x, y);
        ScreenInput.current().clickThroughHandler(x, y, button, Keys.release);
    }

    @Override
    public void dragTo(Object screen, float x, float y, int button, float deltaX, float deltaY) {
        placeCursor(x, y);
        ScreenInput input = ScreenInput.current();
        input.mouseMoved(screen, x, y);
        input.mouseDragged(screen, x, y, button, heldModifiers(), deltaX, deltaY);
    }

    @Override
    public void scroll(Object screen, float x, float y, double amount) {
        placeCursor(x, y);
        ScreenInput.current().mouseScrolled(screen, x, y, amount);
    }

    @Override
    public void keyDown(Object screen, int keyCode, int scanCode, int modifiers) {
        heldKeys.add(keyCode);
        ScreenInput.current().keyPressed(
            screen, keyCode, effectiveScanCode(keyCode, scanCode), modifiers | heldModifiers()
        );
    }

    @Override
    public void keyUp(Object screen, int keyCode, int scanCode, int modifiers) {
        ScreenInput.current().keyReleased(
            screen, keyCode, effectiveScanCode(keyCode, scanCode), modifiers | heldModifiers()
        );
        heldKeys.remove(keyCode);
    }

    @Override
    public void charTyped(Object screen, int codePoint, int modifiers) {
        ScreenInput.current().charTyped(screen, codePoint, modifiers);
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
        ScreenInput input = ScreenInput.current();
        Object screen = input.currentScreen();
        if (screen != null) {
            input.mouseMoved(screen, pointerX, pointerY);
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
        Object handler = Minecraft.getInstance().mouseHandler;
        ScreenInput input = ScreenInput.current();
        if (handler == null || !input.mouseGrabbed(handler)) {
            return;
        }
        if (!input.releaseMouseGrab(handler)) {
            logger.warn("[uitest] MouseHandlerAccessor is not applied; the mouse grab stays in place");
        }
    }
}
