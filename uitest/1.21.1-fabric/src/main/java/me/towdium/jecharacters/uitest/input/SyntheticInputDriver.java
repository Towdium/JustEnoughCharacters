package me.towdium.jecharacters.uitest.input;

import com.mojang.blaze3d.platform.InputConstants;
import me.towdium.jecharacters.mixin.uitest.MouseHandlerAccessor;
import me.towdium.jecharacters.uitest.api.InputDriver;
import me.towdium.jecharacters.uitest.api.PlayerInput;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a live {@code Screen} with synthetic input, without moving the real pointer.
 *
 * <p>Every gesture is dispatched to the screen the harness is testing, in GUI-scaled space, after
 * aiming: {@link #placeCursor} stores the position and refreshes hover, because on a CloudLib
 * {@code BasicScreen} hover is event-driven — {@code Scene} recomputes {@code hovered} and fires
 * enter/leave only inside its {@code mouseMoved}, and vanilla only reaches that method when the
 * accumulated cursor delta is non-zero <em>and</em> the window is focused. A stationary synthetic
 * pointer would otherwise hover nothing at all.
 *
 * <p>This class is also the {@link KeyState} and {@link CursorState} source it installs, so both
 * seams can be cleared owner-safely: {@code clearSource(owner)} only clears when the owner is
 * still the installed one.
 *
 * <p>{@link #install()} registers the {@link KeyState} source (so held modifiers are visible to
 * CloudLib, which reads them through vanilla's GLFW-based {@code hasControlDown}), the
 * {@link CursorState} source (so {@code ScreenUtil}, the scene drag bookkeeping and the render
 * mouse arguments all agree), blocks {@link RawInputGate}, and drops any mouse grab left over
 * from the world — without warping the pointer to the window centre.
 */
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
        // every step below is unconditional and non-throwing, so a half-installed driver cannot
        // leave the real keyboard or mouse dead
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

    /** Records a held key without dispatching anything, for the in-world gesture path. */
    void markKeyHeld(int keyCode) {
        heldKeys.add(keyCode);
    }

    /** Drops a recorded hold without dispatching anything. */
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
        screen.mouseScrolled(x, y, 0.0, amount);
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

    /** Whether {@link #install()} currently holds the seams. */
    @Override
    public boolean installed() {
        return installed;
    }

    /** Stores a GUI-scaled pointer position without dispatching anything. */
    protected void setPointer(float xGui, float yGui) {
        pointerX = xGui;
        pointerY = yGui;
    }

    /** Refreshes hover at the stored pointer position on the live screen. */
    protected void refreshHover() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.mouseMoved(pointerX, pointerY);
        }
    }

    private int heldModifiers() {
        int modifiers = 0;
        IntIterator iterator = heldKeys.intIterator();
        while (iterator.hasNext()) {
            modifiers |= Keys.modifierMaskOf(iterator.nextInt());
        }
        return modifiers;
    }

    private static int effectiveScanCode(int keyCode, int scanCode) {
        return scanCode > 0 ? scanCode : Keys.scanCodeOf(keyCode);
    }

    /**
     * Releases a grabbed mouse pointer without the warp {@code MouseHandler.releaseMouse} would
     * perform, so the first screen of an interactive run does not yank the real pointer to the
     * window centre. Vanilla's {@code releaseMouse} body is guarded by {@code mouseGrabbed}, so
     * clearing the flag leaves the handler consistent.
     */
    protected void dropMouseGrabWithoutWarping() {
        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouseHandler = minecraft.mouseHandler;
        if (mouseHandler == null || !mouseHandler.isMouseGrabbed()) {
            return;
        }
        if (!(mouseHandler instanceof MouseHandlerAccessor accessor)) {
            // a hard cast would throw out of install() and leave every seam installed, which is
            // worse than a grabbed pointer: the real input would be dead for the rest of the run
            logger.warn("[uitest] MouseHandlerAccessor is not applied; the mouse grab stays in place");
            return;
        }
        long window = minecraft.getWindow().getWindow();
        double[] cursorXOut = new double[1];
        double[] cursorYOut = new double[1];
        GLFW.glfwGetCursorPos(window, cursorXOut, cursorYOut);
        accessor.setMouseGrabbed(false);
        InputConstants.grabOrReleaseMouse(window, GLFW.GLFW_CURSOR_NORMAL, cursorXOut[0], cursorYOut[0]);
    }
}
