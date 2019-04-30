package me.towdium.jecharacters.match;

import me.towdium.jecharacters.match.Utilities.IndexSet;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public interface Matchable {
    IndexSet match(String str, int start);
}
