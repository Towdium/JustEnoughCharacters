package me.towdium.jecharacters.mixin.uitest;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The harness's writes into {@code MouseHandler}: the grabbed flag, the accumulated turn deltas, and
 * the two handler bodies a scripted in-world gesture has to reach.
 *
 * <p>{@code mouseGrabbed} is written directly rather than through {@code grabMouse()}, whose body
 * warps the real pointer to the window centre and captures the OS cursor
 * ({@code MouseHandler.java:369-385}).
 *
 * <p>{@code xpos/ypos} are written for one thing only: a click delivered through
 * {@link #invokeOnPress} <em>at a chosen point</em> — the handler route
 * {@link me.towdium.jecharacters.uitest.input.SyntheticInputDriver#mouseDownThroughHandler} takes,
 * which is the only click route a mod listening to Forge's screen events can hear. {@code onPress}
 * reads those two <em>fields</em> ({@code MouseHandler.java:79-80}), not the getters the
 * virtual-cursor seam covers, so a handler-route click has to place the pointer through the fields
 * too or it would land wherever the OS pointer happens to be. Reads stay the getters' business:
 * nothing in the harness reads the fields.
 *
 * <p>{@code accumulatedDX/DY} are what vanilla itself turns with. {@code onMove} adds the per-frame
 * pointer delta to them ({@code MouseHandler.java:252-253}), {@code handleAccumulatedMovement}
 * consumes them once per frame and zeroes them ({@code :262-296}), and {@code turnPlayer} scales
 * them into {@code player.turn} ({@code :298-331}). Writing them therefore turns the player through
 * the true mouse-look path — window-focus gate, inversion, cinematic branch and all.
 *
 * <p>{@code onPress} is the whole in-world click path: NeoForge's {@code InputEvent.MouseButton.Pre}
 * (which is where Nimbus's crosshair routing consumes a click on a pointed panel), then the
 * {@code KeyMapping} the attack/use/pick binds are read from
 * ({@code MouseHandler.java:87-137}). {@code onScroll} is the hotbar wheel. Both are private, and
 * both are reached through {@link me.towdium.jecharacters.uitest.input.RawInputGate}'s synthetic
 * bypass, because the harness's own gate cancels them at HEAD when raw input is blocked.
 */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("mouseGrabbed")
    void setMouseGrabbed(boolean grabbed);

    @Accessor("xpos")
    void setXpos(double x);

    @Accessor("ypos")
    void setYpos(double y);

    @Accessor("accumulatedDX")
    double getAccumulatedDx();

    @Accessor("accumulatedDX")
    void setAccumulatedDx(double delta);

    @Accessor("accumulatedDY")
    double getAccumulatedDy();

    @Accessor("accumulatedDY")
    void setAccumulatedDy(double delta);

    @Invoker("onPress")
    void invokeOnPress(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    void invokeOnScroll(long window, double xOffset, double yOffset);
}
