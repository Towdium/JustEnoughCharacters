package me.towdium.jecharacters.match;

import java.util.function.Predicate;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public class IndexSet {
    static final IndexSet ONE = new IndexSet(0x2);
    static final IndexSet NONE = new IndexSet(0x0);

    int value = 0x0;

    IndexSet() {
    }

    IndexSet(int value) {
        this.value = value;
    }

    public void set(int index) {
        int i = 0x1 << index;
        value |= i;
    }

    public boolean get(int index) {
        int i = 0x1 << index;
        return (value & i) != 0;
    }

    public void merge(IndexSet s) {
        value = value == 0x1 ? s.value : (value |= s.value);
    }

    public boolean foreach(Predicate<Integer> p) {
        int v = value;
        for (int i = 0; i < 7; i++) {
            if ((v & 0x1) == 0x1 && !p.test(i)) return false;
            v >>= 1;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        foreach(i -> {
            builder.append(i);
            builder.append(", ");
            return true;
        });
        if (builder.length() != 0) {
            builder.delete(builder.length() - 2, builder.length());
            return builder.toString();
        } else return "0";
    }
}