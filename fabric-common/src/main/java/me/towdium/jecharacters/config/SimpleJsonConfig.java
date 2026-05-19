package me.towdium.jecharacters.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;


/**
 * A simple config implementation using json.
 */
public class SimpleJsonConfig {

    private static final Logger logger = LogManager.getLogger("Jech Config");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File configFile;
    private JsonObject jsonObject = new JsonObject();

    public SimpleJsonConfig() {
        this.configFile = FabricLoader.getInstance().getConfigDir().resolve("jecharacters.json").toFile();
    }

    public boolean save() {
        if (!configFile.exists()) {
            try {
                Files.createFile(configFile.toPath());
            } catch (IOException e) {
                logger.error("Can't create config file!");
                return false;
            }
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            IOUtils.write(gson.toJson(jsonObject), writer);
        } catch (IOException e) {
            logger.error("Can't save config file!");
            return false;
        }
        return true;
    }

    public boolean load() {
        if (!configFile.exists()) {
            return false;
        }
        try (FileReader reader = new FileReader(configFile)) {
            jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            logger.error("Can't read config file!");
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