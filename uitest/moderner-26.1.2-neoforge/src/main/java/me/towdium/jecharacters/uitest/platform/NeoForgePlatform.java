package me.towdium.jecharacters.uitest.platform;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.List;

public final class NeoForgePlatform implements Platform {

    @Override
    public String loaderLabel() {
        return "neoforge";
    }

    @Override
    public String loaderVersion() {
        return ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public List<String> modIds() {
        return ModList.get().getMods().stream().map(IModInfo::getModId).sorted().toList();
    }
}
