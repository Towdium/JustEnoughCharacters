package me.towdium.jecharacters.uitest.datadriven;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Args {

    private final String context;

    private final JsonObject raw;

    private final Set<String> read = new LinkedHashSet<String>();

    Args(String context, JsonObject raw) {
        this.context = context;
        this.raw = raw == null ? new JsonObject() : raw;
    }

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

    boolean bool(String key) {
        JsonElement element = element(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw bad(key, "must be true or false");
        }
        return element.getAsBoolean();
    }

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

    double number(String key) {
        JsonElement element = element(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw bad(key, "must be a number");
        }
        return element.getAsDouble();
    }

    String stringOrNull(String key) {
        JsonElement element = peek(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return string(key);
    }

    String stringOr(String key, String fallback) {
        String value = stringOrNull(key);
        return value == null ? fallback : value;
    }

    boolean boolOr(String key, boolean fallback) {
        JsonElement element = peek(key);
        if (element == null) return fallback;
        return bool(key);
    }

    int integerOr(String key, int fallback) {
        JsonElement element = peek(key);
        if (element == null) return fallback;
        return integer(key);
    }

    List<String> stringsOrEmpty(String key) {
        JsonElement element = peek(key);
        if (element == null || element.isJsonNull()) {
            return new ArrayList<String>();
        }
        if (!element.isJsonArray()) {
            throw bad(key, "must be a list of strings");
        }
        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<String>(array.size());
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive() || !((JsonPrimitive) item).isString()) {
                throw bad(key, "must be a list of strings");
            }
            values.add(item.getAsString());
        }
        return values;
    }

    JsonArray array(String key) {
        JsonElement element = element(key);
        if (!element.isJsonArray()) {
            throw bad(key, "must be a list");
        }
        return element.getAsJsonArray();
    }

    boolean has(String key) {
        return peek(key) != null;
    }

    void done() {
        List<String> unknown = new ArrayList<String>();
        for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
            if (!read.contains(entry.getKey())) unknown.add(entry.getKey());
        }
        if (!unknown.isEmpty()) {
            throw new ScenarioJsonException(
                    context + ": unknown parameter(s) " + quote(unknown) + "; the keys this step takes are "
                            + (read.isEmpty() ? "none" : quote(read))
            );
        }
    }

    private JsonElement peek(String key) {
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

    private static String describe(JsonElement element) {
        if (element == null) return "absent";
        String text = element.toString();
        return text.length() <= 80 ? text : text.substring(0, 80) + "\u2026";
    }

    private static String quote(Iterable<String> values) {
        List<String> quoted = new ArrayList<String>();
        for (String value : values) {
            quoted.add("'" + value.toLowerCase(Locale.ROOT) + "'");
        }
        return String.join(", ", quoted);
    }
}
