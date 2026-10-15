package me.towdium.jecharacters.uitest.input;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The harness's pointer state on the 1.12.2 line.
 *
 * <p>1.21.1's {@code MouseHandlerMixin} answers {@code MouseHandler}'s position and button fields;
 * this line has no {@code MouseHandler}, only {@code org.lwjgl.input.Mouse} read directly by
 * {@code GuiScreen.handleMouseInput} and by {@code Minecraft.runTickMouse}. The harness's coremod
 * rewrites those LWJGL read methods to answer from here while {@link #isControlling()} is true.
 *
 * <p>Coordinates are the ones {@code Mouse.getEventX()/getEventY()} would return: display pixels,
 * measured from the top-left of the window. {@code GuiScreen} scales them itself
 * ({@code x * width / mc.displayWidth}), so a scenario that means a scaled-screen point has to
 * convert before queueing — {@link #clickAt} does that with the factors it is given.
 */
public final class CursorState {

    private static volatile boolean controlling;

    private static int x;
    private static int y;
    private static int dx;
    private static int dy;

    private static final ConcurrentLinkedQueue<MouseEvent> queue = new ConcurrentLinkedQueue<MouseEvent>();

    private static int eventButton = -1;
    private static boolean eventButtonState;
    private static int eventX;
    private static int eventY;
    private static int eventDX;
    private static int eventDY;
    private static int eventDWheel;

    private CursorState() {}

    /** Whether the harness owns pointer input right now. */
    public static boolean isControlling() {
        return controlling;
    }

    /** Hands pointer input to the harness (or back to the real device). */
    public static void setControlling(boolean value) {
        controlling = value;
        if (!value) clear();
    }

    /** Empties the queue and forgets the forged position. */
    public static void clear() {
        queue.clear();
        eventButton = -1;
        eventButtonState = false;
        eventX = 0;
        eventY = 0;
        eventDX = 0;
        eventDY = 0;
        eventDWheel = 0;
    }

    /** Places the pointer, in display pixels. */
    public static void moveTo(int newX, int newY) {
        dx += newX - x;
        dy += newY - y;
        x = newX;
        y = newY;
    }

    /** Queues a button press or release at the current position. */
    public static void button(int button, boolean state) {
        queue.add(new MouseEvent(button, state, x, y, 0, 0, 0));
    }

    /** Queues a button press and release at the current position. */
    public static void click(int button) {
        button(button, true);
        button(button, false);
    }

    /** Queues a scroll of {@code notches} notches at the current position. */
    public static void wheel(int notches) {
        queue.add(new MouseEvent(-1, false, x, y, 0, 0, notches * 120));
    }

    /** Advances to the next queued pointer event; false when the queue is empty. */
    public static boolean next() {
        MouseEvent event = queue.poll();
        if (event == null) return false;
        eventButton = event.button;
        eventButtonState = event.state;
        eventX = event.x;
        eventY = event.y;
        eventDX = event.dx;
        eventDY = event.dy;
        eventDWheel = event.dwheel;
        return true;
    }

    /** How many queued pointer events the game has not consumed yet. */
    public static int queued() {
        return queue.size();
    }

    /** The event state vanilla's {@code Mouse.getEventButton()} would answer. */
    public static int eventButton() {
        return eventButton;
    }

    public static boolean eventButtonState() {
        return eventButtonState;
    }

    public static int eventX() {
        return eventX;
    }

    public static int eventY() {
        return eventY;
    }

    public static int eventDX() {
        return eventDX;
    }

    public static int eventDY() {
        return eventDY;
    }

    public static int eventDWheel() {
        return eventDWheel;
    }

    /** The absolute pointer position, in display pixels. */
    public static int x() {
        return x;
    }

    public static int y() {
        return y;
    }

    /** The pointer movement since the last {@link #next()}, in display pixels. */
    public static int dx() {
        return dx;
    }

    public static int dy() {
        return dy;
    }

    /** Whether a button is held (the held-button view). */
    public static boolean isDown(int button) {
        return held[button & 7];
    }

    private static final boolean[] held = new boolean[8];

    /** Marks a button as held or released by the harness. */
    public static void setDown(int button, boolean value) {
        held[button & 7] = value;
    }

    private static final class MouseEvent {
        final int button;
        final boolean state;
        final int x;
        final int y;
        final int dx;
        final int dy;
        final int dwheel;

        MouseEvent(int button, boolean state, int x, int y, int dx, int dy, int dwheel) {
            this.button = button;
            this.state = state;
            this.x = x;
            this.y = y;
            this.dx = dx;
            this.dy = dy;
            this.dwheel = dwheel;
        }
    }
}
