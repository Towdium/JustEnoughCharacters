package me.towdium.hecharacters.match;

import me.towdium.hecharacters.match.Utilities.IndexSet;

/**
 * Author: Towdium
 * Date: 21/04/19
 */
public interface Matchable {
    IndexSet match(String str, int start);
}
