import os

manual = ['jei1', 'jei2', 'jei3', 'psi']
suffix = [
    'net.minecraft.client.util.SearchTree:<init>(Ljava/util/function/Function;Ljava/util/function/Function;)V',  # Vanilla
    'net.minecraft.client.util.SearchTree:func_194040_a()V',  # Vanilla
    'net.minecraft.client.util.SearchTreeReloadable:<init>(Ljava/util/function/Function;)V',  # Vanilla
    'net.minecraft.client.util.SearchTreeReloadable:func_194040_a()V'  # Vanilla
]
contains = [
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$11(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # Controlling
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$10(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # Controlling
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$9(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # Controlling
    'com.refinedmods.refinedstorage.screen.grid.filtering.ModGridFilter:test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z',  # Refined Storage
    'com.refinedmods.refinedstorage.screen.grid.filtering.NameGridFilter:test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z',  # Refined Storage
    'com.refinedmods.refinedstorage.screen.grid.filtering.TagGridFilter:lambda$test$0(Ljava/lang/String;)Z',  # Refined Storage
    'com.refinedmods.refinedstorage.screen.grid.filtering.TooltipGridFilter:test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z',  # Refined Storage
    'com.rwtema.extrautils2.transfernodes.TileIndexer$ContainerIndexer$WidgetItemRefButton:lambda$getRef$0([Ljava/lang/String;Lcom/rwtema/extrautils2/utils/datastructures/ItemRef;)Z',  # Extra Utilities
    'de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:fitsFilter(Lde/ellpeck/actuallyadditions/api/booklet/IBookletPage;Ljava/lang/String;)Z',  # Actually Additions
    'de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:getChaptersForDisplay(Ljava/lang/String;)Ljava/util/List;',  # Actually Additions
    'appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen:refreshList()V',  # Applied Energistics
    'appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen:itemStackMatchesSearchTerm(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z',  # Applied Energistics
    'me.towdium.jecalculation.utils.Utilities$I18n:contains(Ljava/lang/String;Ljava/lang/String;)Z',  # Just Enough Calculation
    'sonar.logistics.core.tiles.readers.fluids.GuiFluidReader:getGridList()Ljava/util/List;',  # Practical Logistics
    'sonar.logistics.core.tiles.readers.items.GuiInventoryReader:getGridList()Ljava/util/List;',  # Practical Logistics
    'sonar.logistics.core.items.wirelessstoragereader.GuiWirelessStorageReader:getGridList()Ljava/util/List;',  # Practical Logistics
    'sonar.logistics.core.items.guide.GuiGuide:updateSearchList()V',  # Practical Logistics
    'binnie.core.machines.storage.SearchDialog:updateSearch()V',  # Binnie Core
    'vazkii.patchouli.client.book.BookEntry:isFoundByQuery(Ljava/lang/String;)Z',  # Patchouli (Botania)
    'vazkii.botania.api.corporea.CorporeaRequestDefaultMatchers$CorporeaStringMatcher:equalOrContain(Ljava/lang/String;)Z',  # Botania (Corporea)
    'net.blay09.mods.cookingforblockheads.container.RecipeBookContainer:search(Ljava/lang/String;)V',  # Cooking for Blockheads
    'net.blay09.mods.farmingforblockheads.container.MarketClientContainer:applyFilters()V',  # Farming for Blockheads
    'mcjty.rftools.blocks.shaper.LocatorTileEntity:checkFilter(Ljava/lang/String;Lnet/minecraft/entity/Entity;)Z',  # RF Tools
    'mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity:lambda$makeSearchPredicate$20(Ljava/lang/String;Lnet/minecraft/item/ItemStack;)Z',  # RF Tools
    'mcjty.rftools.blocks.storage.GuiModularStorage:updateList()V',  # RF Tools
    'mcjty.rftools.items.netmonitor.GuiNetworkMonitor:populateList()V',  # RF Tools
    'moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory:doesItemMatchFilter(Lmoze_intel/projecte/api/ItemInfo;)Z',  # Project E
    'org.cyclops.integrateddynamics.core.client.gui.WidgetTextFieldDropdown:refreshDropdownList()V',  # Integrated Dynamics
    'blusunrize.immersiveengineering.api.ManualPageBlueprint:listForSearch(Ljava/lang/String;)Z',  # Immersive Engineering
    'blusunrize.lib.manual.ManualPages$Crafting:listForSearch(Ljava/lang/String;)Z',  # Immersive Engineering
    'blusunrize.lib.manual.ManualPages$CraftingMulti:listForSearch(Ljava/lang/String;)Z',  # Immersive Engineering
    'blusunrize.lib.manual.ManualPages$ItemDisplay:listForSearch(Ljava/lang/String;)Z',  # Immersive Engineering
    'blusunrize.lib.manual.gui.GuiManual:func_73869_a(CI)V',  # Immersive Engineering
    'betterquesting.api2.client.gui.panels.lists.CanvasEntityDatabase:queryMatches(Lnet/minecraftforge/fml/common/registry/EntityEntry;Ljava/lang/String;Ljava/util/ArrayDeque;)V',  # Better Questing
    'betterquesting.api2.client.gui.panels.lists.CanvasFileDirectory:queryMatches(Ljava/io/File;Ljava/lang/String;Ljava/util/ArrayDeque;)V',  # Better Questing
    'betterquesting.api2.client.gui.panels.lists.CanvasFluidDatabase:queryMatches(Lnet/minecraftforge/fluids/Fluid;Ljava/lang/String;Ljava/util/ArrayDeque;)V',  # Better Questing
    'betterquesting.api2.client.gui.panels.lists.CanvasItemDatabase:queryMatches(Lnet/minecraft/item/Item;Ljava/lang/String;Ljava/util/ArrayDeque;)V',  # Better Questing
    'betterquesting.api2.client.gui.panels.lists.CanvasQuestDatabase:queryMatches(Lbetterquesting/api2/storage/DBEntry;Ljava/lang/String;Ljava/util/ArrayDeque;)V',  # Better Questing
    'com.mia.props.client.container.GuiDecobench:refreshButtons()V',  # Decofraft workbench
    'logisticspipes.gui.orderer.GuiOrderer:isSearched(Ljava/lang/String;Ljava/lang/String;)Z',  # Logistics Pipes
    'logisticspipes.gui.orderer.GuiRequestTable:isSearched(Ljava/lang/String;Ljava/lang/String;)Z',  # Logistics Pipes
    'com.lothrazar.storagenetwork.api.util.UtilInventory:doOverlap(Ljava/lang/String;Ljava/lang/String;)Z',  # Simple Storage Network
    'com.lothrazar.storagenetwork.gui.NetworkWidget:doesStackMatchSearch(Lnet/minecraft/item/ItemStack;)Z',  # Simple Storage Network
    'com.latmod.mods.projectex.gui.GuiTableBase:updateValidItemList()V',  # Project EX
    'vazkii.quark.client.module.ChestSearchingModule:namesMatch(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z',  # Quark
    'net.minecraft.client.gui.screen.inventory.CreativeScreen:func_147053_i()V',  # Vanilla
    'net.minecraft.client.gui.screen.inventory.CreativeScreen:func_214080_a(Ljava/lang/String;)V',  # Vanilla
    'amerifrance.guideapi.gui.GuiSearch:getMatches:(Lamerifrance.guideapi.api.impl.Book;Ljava.lang.String;Lnet.minecraft.entity.player.EntityPlayer;Lnet.minecraft.item.ItemStack;)Ljava.util.List;',  # Guide-API
    'thaumcraft.client.gui.GuiResearchBrowser:updateSearch:()V',  # Thaumcraft
    'zuve.searchablechests.ChestEventHandler:stackMatches(Ljava/lang/String;Lnet/minecraft/item/ItemStack;)Z',  # Searchable Chests
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$null$5(Ljava/lang/String;Ljava/lang/String;)Z',  # Mekanism (QIO)
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$null$3(Ljava/lang/String;Ljava/lang/String;)Z',  # Mekanism (QIO)
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$static$1(Ljava/lang/String;Lnet/minecraft/item/ItemStack;)Z',  # Mekanism (QIO)
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$static$0(Ljava/lang/String;Lnet/minecraft/item/ItemStack;)Z',  # Mekanism (QIO)
    'hellfirepvp.astralsorcery.client.screen.journal.ScreenJournalPerkTree:updateSearchHighlight()V',  #Astral Sorcery
    'hellfirepvp.astralsorcery.client.screen.journal.ScreenJournalProgression:onSearchTextInput()V',  #Astral Sorcery
    'com.ma.guide.GuideBookEntries:lambda$null$4(Lnet/minecraft/client/Minecraft;Ljava/lang/String;Ljava/util/HashMap;Lcom/ma/guide/RelatedRecipe;)V',  #Mana and Artifice
    'com.ma.guide.GuideBookEntries:lambda$null$1(Ljava/lang/String;Lcom/ma/guide/interfaces/IEntrySection;)Z',  #Mana and Artifice
    'com.minecolonies.coremod.client.gui.WindowHutAllInventory:lambda$updateResources$1(Lcom/minecolonies/api/crafting/ItemStorage;)Z',  #MineColonies
    'com.minecolonies.coremod.client.gui.WindowPostBox:lambda$updateResources$0(Lnet/minecraft/item/ItemStack;)Z',  #MineColonies
    'com.minecolonies.coremod.client.gui.ItemListModuleWindow:lambda$updateResources$2(Lnet/minecraft/item/ItemStack;)Z',  #MineColonies
    'com.minecolonies.coremod.client.gui.WindowSelectRes:lambda$updateResources$0(Lnet/minecraft/item/ItemStack;)Z',  #MineColonies
    'com.chaosthedude.naturescompass.gui.NaturesCompassScreen:processSearchTerm()V',  #Nature's Compass
    'com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook:onSearchChanged(Ljava/lang/String;)V',  #Ars Nouveau
    'me.shedaniel.rei.impl.client.search.argument.type.TagArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI
    'me.shedaniel.rei.impl.client.search.argument.type.IdentifierArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI
    'me.shedaniel.rei.impl.client.search.argument.type.ModArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI
    'me.shedaniel.rei.impl.client.search.argument.type.TooltipArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI
    'me.shedaniel.rei.impl.client.search.argument.type.TextArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI
    'com.cout970.magneticraft.features.multiblocks.ContainerShelvingUnit$filterSlots$1:invoke:(Lnet.minecraft.item.ItemStack;Ljava.lang.String;)Z',  # Magneticraft
]
equals = [
    'vazkii.botania.api.corporea.CorporeaRequestDefaultMatchers$CorporeaStringMatcher:equalOrContain(Ljava/lang/String;)Z',  # Botania (Corporea)
    'vazkii.quark.client.module.ChestSearchingModule:namesMatch(Lnet/minecraft/item/ItemStack;Ljava/lang/String;)Z',  # Quark
]
regExp = [
    'appeng.client.gui.me.fluids.FluidRepo:matchesSearch(Lappeng/client/gui/me/common/Repo$SearchMode;Ljava/util/regex/Pattern;Lappeng/api/storage/data/IAEFluidStack;)Z',  # Applied Energistics
    'appeng.client.gui.me.items.ItemRepo:matchesSearch(Lappeng/client/gui/me/common/Repo$SearchMode;Ljava/util/regex/Pattern;Lappeng/api/storage/data/IAEItemStack;)Z',  # Applied Energistics
    'org.cyclops.integrateddynamics.inventory.container.ContainerLogicProgrammerBase:lambda$static$0(Lorg/cyclops/integrateddynamics/api/logicprogrammer/ILogicProgrammerElement;Ljava/util/regex/Pattern;)Z',  # Integrated Dynamics 1
    'org.cyclops.integrateddynamics.core.inventory.container.ContainerMultipartAspects:lambda$new$0(Lorg/cyclops/integrateddynamics/api/part/aspect/IAspect;Ljava/util/regex/Pattern;)Z',  # Integrated Dynamics 2
]

pattern = """// Generated
function initializeCoreMod() {{
    Java.type('net.minecraftforge.coremod.api.ASMAPI').loadFile('me/towdium/jecharacters/scripts/_lib.js');
    return {{
        'jecharacters-gen{idx}': {{
            'target': {{
                'type': 'METHOD',
                'class': '{clazz}',
                'methodName': Api.mapMethod('{name}'),
                'methodDesc': '{desc}'
            }},
            'transformer': trans{op}
        }}
    }}
}}
"""


def decode(s):
    idx1 = s.index(':')
    idx2 = s.index('(')
    return {
        'clazz': s[:idx1],
        'name': s[idx1 + 1: idx2],
        'desc': s[idx2:]
    }


if __name__ == '__main__':
    total = 0
    file = 'src/main/resources/me/towdium/jecharacters/scripts/_gen{}.js'
    path = 'src/main/resources/me/towdium/jecharacters/scripts/'
    for i in os.listdir(path):
        if i.startswith('_gen'):
            os.remove(path + i)

    for i in ['contains', 'suffix', 'regExp', 'equals']:
        for j in globals()[i]:
            print(j)
            s = pattern.format(idx=total, **decode(j), op=i[0].capitalize() + i[1:])
            with open(file.format(total), 'w') as f:
                f.write(s)
            total += 1

    json = '{'
    for i in manual:
        json += '\n  "jecharacters-{0}": "me/towdium/jecharacters/scripts/{0}.js",'.format(i)
    for i in range(total):
        json += '\n  "jecharacters-gen{0}": "me/towdium/jecharacters/scripts/_gen{0}.js",'.format(i)
    json = json[:-1] + '\n}\n'
    with open('src/main/resources/META-INF/coremods.json', 'w') as f:
        f.write(json)
