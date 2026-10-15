package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One step's arguments, read strictly.
 *
 * <p>A hand-written JSON file fails in the ways hand-written files do: a key spelled slightly wrong,
 * a number where a string belonged, a parameter that belongs to a different step. Every accessor here
 * therefore does three things — records that the key was read, checks its type, and quotes the step
 * and the key in whatever it throws — and {@link #done()} refuses the step when any key was never
 * read. A misspelled key is then an error that names itself, instead of a scenario that silently
 * does something other than what its author wrote.
 */
final class Args {

    private final String context;

    private final JsonObject raw;

    private final Set<String> read = new LinkedHashSet<>();

    Args(String context, @Nullable JsonObject raw) {
        this.context = context;
        this.raw = raw == null ? new JsonObject() : raw;
    }

    /** A required non-blank string. */
    String string(String key) {
        JsonElement element = element(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw bad(key, "must be a string");
        }
        String value = element.getAsString();
        if (value.isEmpty()) {
            throw bad(key, "must not be empty");
        }
        return value;
    }

    /** A required boolean. */
    boolean bool(String key) {
        JsonElement element = element(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw bad(key, "must be true or false");
        }
        return element.getAsBoolean();
    }

    /** A required integer. */
    int integer(String key) {
        JsonElement element = element(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw bad(key, "must be a number");
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value)) {
            throw bad(key, "must be a whole number");
        }
        return (int) value;
    }

    /** A required number. */
    double number(String key) {
        JsonElement element = element(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw bad(key, "must be a number");
        }
        return element.getAsDouble();
    }

    /** An optional string, or {@code null} when the key is absent or JSON null. */
    @Nullable String stringOrNull(String key) {
        JsonElement element = peek(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return string(key);
    }

    /** An optional string with a default. */
    String stringOr(String key, String fallback) {
        String value = stringOrNull(key);
        return value == null ? fallback : value;
    }

    /** An optional boolean with a default. */
    boolean boolOr(String key, boolean fallback) {
        JsonElement element = peek(key);
        if (element == null) return fallback;
        return bool(key);
    }

    /** An optional integer with a default. */
    int integerOr(String key, int fallback) {
        JsonElement element = peek(key);
        if (element == null) return fallback;
        return integer(key);
    }

    /** An optional list of strings, empty when the key is absent. */
    List<String> stringsOrEmpty(String key) {
        JsonElement element = peek(key);
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            throw bad(key, "must be a list of strings");
        }
        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive() || !((JsonPrimitive) item).isString()) {
                throw bad(key, "must be a list of strings");
            }
            values.add(item.getAsString());
        }
        return values;
    }

    /** A required list, refusing anything that is not one. */
    JsonArray array(String key) {
        JsonElement element = element(key);
        if (!element.isJsonArray()) {
            throw bad(key, "must be a list");
        }
        return element.getAsJsonArray();
    }

    /**
     * Whether a key is present at all — including as JSON {@code null}.
     *
     * <p>This is how a step tells "the author wrote null on purpose" (accept it) from "the author
     * forgot the key" (refuse it), which matters for a parameter whose null is meaningful.
     */
    boolean has(String key) {
        return peek(key) != null;
    }

    /** Refuses the step when it carries a key no accessor read. */
    void done() {
        List<String> unknown = new ArrayList<>();
        for (String key : raw.keySet()) {
            if (!read.contains(key)) unknown.add(key);
        }
        if (!unknown.isEmpty()) {
            throw new ScenarioJsonException(
                context + ": unknown parameter(s) " + quote(unknown) + "; the keys this step takes are "
                        + (read.isEmpty() ? "none" : quote(read))
            );
        }
    }

    /**
     * The element under a key, or {@code null} when the key is absent.
     *
     * <p>A present key counts as read even when its value is JSON {@code null}: what {@link #done()}
     * refuses is a key the step never looked at, not a null the author wrote on purpose.
     */
    private @Nullable JsonElement peek(String key) {
        if (!raw.has(key)) return null;
        read.add(key);
        return raw.get(key);
    }

    private JsonElement element(String key) {
        JsonElement element = peek(key);
        if (element == null) {
            throw new ScenarioJsonException(context + ": missing required parameter '" + key + "'");
        }
        return element;
    }

    private ScenarioJsonException bad(String key, String problem) {
        return new ScenarioJsonException(
            context + ": parameter '" + key + "' " + problem + " (was " + describe(peek(key)) + ")"
        );
    }

    private static String describe(@Nullable JsonElement element) {
        if (element == null) return "absent";
        String text = element.toString();
        return text.length() <= 80 ? text : text.substring(0, 80) + "\u2026";
    }

    private static String quote(Iterable<String> values) {
        List<String> quoted = new ArrayList<>();
        for (String value : values) {
            quoted.add("'" + value.toLowerCase(Locale.ROOT) + "'");
        }
        return String.join(", ", quoted);
    }
}
