package me.towdium.jecharacters.utils;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.auto.service.AutoService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@AutoService(Profiler.InfoReader.class)
public class ForgeInfoReader implements Profiler.InfoReader {
    @Override
    public Profiler.Platform getPlatform() {
        return Profiler.Platform.NEOFORGE;
    }

    @Override
    public Profiler.ModContainer[] readInfo(InputStream is) {
        Path p = null;
        try {
            p = Files.createTempFile("jecharacters", ".toml");
            Files.copy(is, p, REPLACE_EXISTING);
            FileConfig c = FileConfig.of(p);
            c.load();
            Collection<Config> mods = c.get("mods");
            return mods.stream()
                    .map(i -> new Profiler.ModContainer(i.get("modId"), i.get("displayName"), i.get("version")))
                    .toArray(Profiler.ModContainer[]::new);
        } catch (IOException e) {
            Profiler.LOGGER.error("Failed to read forge mod list.");
            return null;
        } finally {
            if (p != null && !p.toFile().delete()) {
                Profiler.LOGGER.error("Failed to delete temp file.");
            }
        }
    }
}
