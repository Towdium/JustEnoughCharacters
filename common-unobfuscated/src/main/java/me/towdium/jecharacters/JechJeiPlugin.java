package me.towdium.jecharacters;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedSearchRegistration;
import net.minecraft.resources.Identifier;

@JeiPlugin
public class JechJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(JustEnoughCharacters.MODID, "advanced_search");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerAdvancedSearch(IAdvancedSearchRegistration registration) {
        JustEnoughCharacters.logger.info("Registering JEI pinyin search storage");
        registration.replaceSearchStorage(JechSearchStorage::new);
    }
}
