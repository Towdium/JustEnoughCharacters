package me.towdium.jecharacters.uitest.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The scenario selection grammar: {@code all | name | a,b | group: | tag: | regex: | mod:}, with an
 * optional second expression that subtracts.
 *
 * <p>Terms inside one expression are OR-ed, so {@code a,b} selects both. A comma separates terms,
 * which means a regular expression may not contain one; put it in the {@code exclude} expression or
 * widen it. An empty selection matches nothing, and the runner reports that rather than passing
 * silently.
 */
public final class ScenarioSelection {

    private final List<String> include;
    private final List<String> exclude;

    private ScenarioSelection(List<String> include, List<String> exclude) {
        this.include = List.copyOf(include);
        this.exclude = List.copyOf(exclude);
    }

    /** Parses two comma-separated expressions. */
    public static ScenarioSelection parse(String include, String exclude) {
        return new ScenarioSelection(split(include), split(exclude));
    }

    /** Selects everything. */
    public static ScenarioSelection all() {
        return new ScenarioSelection(List.of("all"), List.of());
    }

    /** Whether the selection names anything at all. */
    public boolean isEmpty() {
        return include.isEmpty();
    }

    /** Whether a scenario matches. */
    public boolean matches(String name, Collection<String> tags, String group, Class<?> type) {
        if (include.isEmpty()) return false;
        if (!anyMatch(include, name, tags, group, type)) return false;
        return !anyMatch(exclude, name, tags, group, type);
    }

    /** The include expression as it was written, for the report. */
    public String describe() {
        return String.join(",", include);
    }

    private static boolean anyMatch(
        List<String> terms,
        String name,
        Collection<String> tags,
        String group,
        Class<?> type
    ) {
        for (String term : terms) {
            if (matchesTerm(term, name, tags, group, type)) return true;
        }
        return false;
    }

    private static boolean matchesTerm(
        String rawTerm,
        String name,
        Collection<String> tags,
        String group,
        Class<?> type
    ) {
        String term = rawTerm.trim();
        if (term.isEmpty()) return false;
        if ("all".equals(term) || "*".equals(term)) return true;
        if (term.startsWith("tag:")) return tags.contains(term.substring(4).trim());
        if (term.startsWith("group:")) return group.equals(term.substring(6).trim());
        if (term.startsWith("mod:")) return type.getName().contains("." + term.substring(4).trim() + ".");
        if (term.startsWith("regex:")) return compile(term.substring(6)).matcher(name).find();
        return name.equals(term);
    }

    private static Pattern compile(String expression) {
        try {
            return Pattern.compile(expression);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("bad selection regex '" + expression + "': " + e.getDescription(), e);
        }
    }

    private static List<String> split(String expression) {
        List<String> terms = new ArrayList<>();
        for (String piece : expression.split(",")) {
            if (!piece.isBlank()) terms.add(piece.trim());
        }
        return terms;
    }
}
