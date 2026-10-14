/**
 * The match recorder: the in-process evidence that JECh's search adapters really compared a query
 * against a candidate.
 *
 * <p>A scenario's assertions are about what the game <em>did</em>. For a pinyin search that means
 * the comparison the engine performed, not a screenshot of a list that happens to be filtered. The
 * recorder is where those comparisons are collected; the mixins in
 * {@code me.towdium.jecharacters.mixin.uitest} are the taps, and {@code assertMatchRecorded} is how
 * a scenario reads them back.
 */
@org.jspecify.annotations.NullMarked
package me.towdium.jecharacters.uitest.match;
