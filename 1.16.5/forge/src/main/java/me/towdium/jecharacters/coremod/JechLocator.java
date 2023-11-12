package me.towdium.jecharacters.coremod;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.locating.IModFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class JechLocator extends AbstractJarFileLocator {

    @Override
    public List<IModFile> scanMods() {
        List<IModFile> list = new ArrayList<>();
        try {
            Path path = Paths.get(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isDirectory(path)) { // Check if we are under development environment.
                IModFile file = ModFile.newFMLInstance(path, this);
                this.modJars.compute(file, (mf, fs) -> this.createFileSystem(mf));
                list.add(file);
            }
        } catch (Exception e) {
            JechServiceProvider.LOGGER.error("", e);
        }
        return list;
    }

    @Override
    public String name() {
        return "JechLocator";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {

    }
}
