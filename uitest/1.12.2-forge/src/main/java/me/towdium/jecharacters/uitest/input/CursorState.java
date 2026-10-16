package me.towdium.jecharacters.uitest.input;

import java.util.concurrent.ConcurrentLinkedQueue;

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

    public static boolean isControlling() {
        return controlling;
    }

    public static void setControlling(boolean value) {
        controlling = value;
        if (!value) clear();
    }

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

    public static void moveTo(int newX, int newY) {
        dx += newX - x;
        dy += newY - y;
        x = newX;
        y = newY;
    }

    public static void button(int button, boolean state) {
        queue.add(new MouseEvent(button, state, x, y, 0, 0, 0));
    }

    public static void click(int button) {
        button(button, true);
        button(button, false);
    }

    public static void wheel(int notches) {
        queue.add(new MouseEvent(-1, false, x, y, 0, 0, notches * 120));
    }

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

    public static int queued() {
        return queue.size();
    }

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

    public static int x() {
        return x;
    }

    public static int y() {
        return y;
    }

    public static int dx() {
        return dx;
    }

    public static int dy() {
        return dy;
    }

    public static boolean isDown(int button) {
        return held[button & 7];
    }

    private static final boolean[] held = new boolean[8];

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
