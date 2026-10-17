package me.towdium.jecharacters.uitest.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.List;
import java.util.stream.Collectors;

public final class ForgePlatform implements Platform {

    @Override
    public String loaderLabel() {
        return "neoforge";
    }

    @Override
    public String loaderVersion() {
        return ModList.get().getModContainerById("forge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public List<String> modIds() {
        return ModList.get().getMods().stream().map(IModInfo::getModId).sorted()
                .collect(Collectors.toList());
    }
}
