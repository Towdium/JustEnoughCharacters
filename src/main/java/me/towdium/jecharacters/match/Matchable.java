package me.towdium.jecharacters.match;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public interface Matchable {
    IndexSet match(String str, int start);
}
