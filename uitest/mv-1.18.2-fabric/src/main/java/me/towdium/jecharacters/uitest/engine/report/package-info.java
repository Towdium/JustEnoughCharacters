/**
 * The run report: plain mutable data written out as JSON, text and JUnit XML.
 *
 * <p>Nothing here touches Minecraft, so the writer and the XML shape are unit-testable without a
 * client; the engine fills the model from the client side.
 */
@org.jspecify.annotations.NullMarked
package me.towdium.jecharacters.uitest.engine.report;
