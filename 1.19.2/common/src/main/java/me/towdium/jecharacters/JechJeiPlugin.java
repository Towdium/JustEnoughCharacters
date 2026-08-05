package me.towdium.jecharacters;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IAdvancedSearchRegistration;
import mezz.jei.api.search.ISearchStorage;
import mezz.jei.api.search.ISearchStorageFactory;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class JechJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(JustEnoughCharacters.MODID, "advanced_search");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerAdvancedSearch(IAdvancedSearchRegistration registration) {
        JustEnoughCharacters.logger.info("Registering JEI pinyin search storage");
        registration.replaceSearchStorage(new ISearchStorageFactory() {
            @Override
            public <T> ISearchStorage<T> createSearchStorage() {
                return new JechSearchStorage<>();
            }
        });
    }
}
