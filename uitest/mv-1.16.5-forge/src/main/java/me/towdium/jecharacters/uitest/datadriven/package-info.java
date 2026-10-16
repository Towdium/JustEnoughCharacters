/**
 * The declarative scenario layer: scenarios written as JSON instead of Java.
 *
 * <p>A contributor who wants to add a scenario — a new keystroke path, a new search box, a new
 * command — should not have to write Java to do it. The same step vocabulary the Java builder
 * exposes is available as step names in a JSON file, and every step's parameters are validated when
 * the scenario is defined, so a typo is a named failure rather than a scenario that quietly does
 * nothing.
 *
 * <p>Two places are scanned: {@code uitest/scenarios/*.json} inside any loaded mod (so the harness
 * ships scenarios in the repository) and {@code <game dir>/jech-scenarios/*.json} (so a scenario can
 * be tried out without touching a checkout). A file that cannot be read is registered as a scenario
 * that fails when it runs, naming the file and the problem — never dropped silently.
 *
 * <p>The step vocabulary and each step's parameter schema are documented for authors in
 * {@code src/uitest/resources/uitest/scenarios/README.md}.
 */
@org.jspecify.annotations.NullMarked
package me.towdium.jecharacters.uitest.datadriven;
