package me.towdium.jecharacters.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

public class SimpleJsonConfig {

    private static final Logger LOGGER = LogManager.getLogger("Jech Config");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File configFile;
    private JsonObject jsonObject = new JsonObject();

    public SimpleJsonConfig(File configFile) {
        this.configFile = configFile;
    }

    public void sync(Consumer<SimpleJsonConfig> configSyncer) {
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                LOGGER.error("Can't create config file!");
                return;
            }
        }
        if (!load()) return;
        configSyncer.accept(this);
        save();
    }

    public boolean save() {
        try (FileWriter writer = new FileWriter(configFile)) {
            IOUtils.write(gson.toJson(jsonObject), writer);
        } catch (IOException e) {
            LOGGER.error("Can't save config file!");
            return false;
        }
        return true;
    }

    public boolean load() {
        try {
            if (Files.readAllLines(configFile.toPath()).isEmpty()) {
                save();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (FileReader reader = new FileReader(configFile)) {
            jsonObject = new JsonParser().parse(reader).getAsJsonObject();
        } catch (IOException e) {
            LOGGER.error("Can't read config file!");
            return false;
        } catch (IllegalStateException e) {
            LOGGER.error("Can't read config file!");
            jsonObject = new JsonObject();
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
