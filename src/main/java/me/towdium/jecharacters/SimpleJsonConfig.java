package me.towdium.jecharacters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * A simple config implementation using json.
 */
public class SimpleJsonConfig {

    private final File configFile;
    private JsonObject jsonObject = new JsonObject();

    public SimpleJsonConfig() {
        this.configFile = FabricLoader.getInstance().getConfigDir().resolve("jecharacters.json").toFile();
    }

    public boolean save() {
        try (FileWriter writer = new FileWriter(configFile)) {
            IOUtils.write(jsonObject.toString().toCharArray(), writer);
        } catch (IOException e) {
            JustEnoughCharacters.logger.error("Can't save config file!");
            return false;
        }
        return true;
    }

    public boolean load() {
        try (FileReader reader = new FileReader(configFile)) {
            jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            JustEnoughCharacters.logger.error("Can't read config file!");
            return false;
        }
        return true;
    }

    public boolean getBoolValue(String category, String key) {
        if (jsonObject.has(category)) {
            JsonObject categoryObject = jsonObject.getAsJsonObject(category);
            if (categoryObject.has(key)) {
                return categoryObject.get(key).getAsBoolean();
            } else {
                throw new IllegalArgumentException("Can't find config key : " + key + "in category : " + category);
            }
        } else {
            throw new IllegalArgumentException("Can't find config category  : " + category);
        }
    }

    public <T extends Enum<T>> T getEnumValue(String category, String key, Class<T> enumClass) {
        if (jsonObject.has(category)) {
            JsonObject categoryObject = jsonObject.getAsJsonObject(category);
            if (categoryObject.has(key)) {
                String enumName = categoryObject.get(key).getAsString();
                return Enum.valueOf(enumClass, enumName);
            } else {
                throw new IllegalArgumentException("Can't find config key : " + key + "in category : " + category);
            }
        } else {
            throw new IllegalArgumentException("Can't find config category  : " + category);
        }
    }

    public void putValue(String category, String key, String value) {
        if (!jsonObject.has(category)) {
            jsonObject.add(category, new JsonObject());
        }
        JsonObject categoryObject = jsonObject.getAsJsonObject(category);
        if (!categoryObject.has(key)) {
            categoryObject.addProperty(key, value);
        }
    }

    public void putValue(String category, String key, boolean value) {
        putValue(category, key, String.valueOf(value));
    }

    public void setValue(String category, String key, String value) {
        if (jsonObject.has(category)) {
            JsonObject categoryObject = jsonObject.getAsJsonObject(category);
            categoryObject.addProperty(key, value);
        } else {
            jsonObject.add(category, new JsonObject());
        }
    }

    public void setValue(String category, String key, boolean value) {
        setValue(category, key, String.valueOf(value));
    }

}
