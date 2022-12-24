import os

manual = ['jei1', 'jei2', 'jei3', 'psi']
suffix = [
    'net.minecraft.client.searchtree.ReloadableIdSearchTree:m_7729_()V',  # Vanilla
    'net.minecraft.client.searchtree.ReloadableSearchTree:m_7729_()V',  # Vanilla
]
contains = [
    'com.blamejared.controlling.client.NewKeyBindsScreen:lambda$filterKeys$10(Lcom/blamejared/controlling/client/NewKeyBindsList$KeyEntry;)Z',  # Controlling
    'com.blamejared.controlling.client.NewKeyBindsScreen:lambda$filterKeys$8(Lcom/blamejared/controlling/client/NewKeyBindsList$KeyEntry;)Z',  # Controlling
    'com.blamejared.controlling.client.NewKeyBindsScreen:lambda$filterKeys$9(Lcom/blamejared/controlling/client/NewKeyBindsList$KeyEntry;)Z', # Controlling
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$11(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # Controlling legacy
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$10(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # Controlling legacy
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$9(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # Controlling legacy
    'com.refinedmods.refinedstorage.screen.grid.filtering.ModGridFilter:test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z',  # Refined Storage
    'com.refinedmods.refinedstorage.screen.grid.filtering.NameGridFilter:test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z',  # Refined Storage
    'com.refinedmods.refinedstorage.screen.grid.filtering.TagGridFilter:lambda$test$0(Ljava/lang/String;)Z',  # Refined Storage
    'com.refinedmods.refinedstorage.screen.grid.filtering.TooltipGridFilter:test(Lcom/refinedmods/refinedstorage/screen/grid/stack/IGridStack;)Z',  # Refined Storage
    'com.rwtema.extrautils2.transfernodes.TileIndexer$ContainerIndexer$WidgetItemRefButton:lambda$getRef$0([Ljava/lang/String;Lcom/rwtema/extrautils2/utils/datastructures/ItemRef;)Z',  # Extra Utilities
    'de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:fitsFilter(Lde/ellpeck/actuallyadditions/api/booklet/IBookletPage;Ljava/lang/String;)Z',  # Actually Additions
    'de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:getChaptersForDisplay(Ljava/lang/String;)Ljava/util/List;',  # Actually Additions
    'appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen:refreshList()V',  # Applied Energistics
    'appeng.client.gui.me.interfaceterminal.InterfaceTerminalScreen:itemStackMatchesSearchTerm(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z',  # Applied Energistics
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
    'mcjty.rftoolsstorage.modules.scanner.blocks.StorageScannerTileEntity:lambda$makeSearchPredicate$30(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;)Z',  # RF Tools
    'mcjty.rftoolsstorage.modules.modularstorage.client.GuiModularStorage:lambda$updateList$6(Ljava/lang/String;Ljava/util/List;Ljava/util/concurrent/atomic/AtomicInteger;Lnet/minecraftforge/items/IItemHandler;)V',  # RF Tools
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
    'com.lothrazar.storagenetwork.api.util.UtilInventory:doOverlap(Ljava/lang/String;Ljava/lang/String;)Z',  # Simple Storage Network legacy
    'com.lothrazar.storagenetwork.gui.NetworkWidget:doesStackMatchSearch(Lnet/minecraft/world/item/ItemStack;)Z', # Simple Storage Network
    'com.latmod.mods.projectex.gui.GuiTableBase:updateValidItemList()V',  # Project EX
    'net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen:m_98630_()V',  # Vanilla
    'net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen:m_98619_(Ljava/lang/String;)V',  # Vanilla
    'zuve.searchablechests.ChestEventHandler:stackMatches(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;)Z',  # Searchable Chests
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$null$5(Ljava/lang/String;Ljava/lang/String;)Z',  # Mekanism (QIO)
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$null$3(Ljava/lang/String;Ljava/lang/String;)Z',  # Mekanism (QIO)
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$static$1(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;)Z',  # Mekanism (QIO)
    'mekanism.common.content.qio.SearchQueryParser$QueryType:lambda$static$0(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;)Z',  # Mekanism (QIO)
    'hellfirepvp.astralsorcery.client.screen.journal.ScreenJournalPerkTree:updateSearchHighlight()V',  #Astral Sorcery
    'hellfirepvp.astralsorcery.client.screen.journal.ScreenJournalProgression:onSearchTextInput()V',  #Astral Sorcery
    'com.ma.guide.GuideBookEntries:lambda$null$4(Lnet/minecraft/client/Minecraft;Ljava/lang/String;Ljava/util/HashMap;Lcom/ma/guide/RelatedRecipe;)V',  #Mana and Artifice
    'com.ma.guide.GuideBookEntries:lambda$null$1(Ljava/lang/String;Lcom/ma/guide/interfaces/IEntrySection;)Z',  #Mana and Artifice
    'com.minecolonies.coremod.client.gui.WindowHutAllInventory:lambda$updateResources$1(Lcom/minecolonies/api/crafting/ItemStorage;)Z',  #MineColonies
    'com.minecolonies.coremod.client.gui.WindowPostBox:lambda$updateResources$0(Lnet/minecraft/world/item/ItemStack;)Z',  #MineColonies
    'com.minecolonies.coremod.client.gui.ItemListModuleWindow:lambda$updateResources$2(Lnet/minecraft/world/item/ItemStack;)Z',  #MineColonies
    'com.minecolonies.coremod.client.gui.WindowSelectRes:lambda$updateResources$0(Lnet/minecraft/world/item/ItemStack;)Z',  #MineColonies
    'com.chaosthedude.naturescompass.gui.NaturesCompassScreen:processSearchTerm()V',  #Nature's Compass
    'com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook:onSearchChanged(Ljava/lang/String;)V',  #Ars Nouveau
    'me.shedaniel.rei.impl.client.search.argument.type.TagArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI legacy
    'me.shedaniel.rei.impl.client.search.argument.type.IdentifierArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI legacy
    'me.shedaniel.rei.impl.client.search.argument.type.ModArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI legacy
    'me.shedaniel.rei.impl.client.search.argument.type.TooltipArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI legacy
    'me.shedaniel.rei.impl.client.search.argument.type.TextArgumentType:matches(Lorg/apache/commons/lang3/mutable/Mutable;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z',  # REI legacy
    'me.shedaniel.rei.impl.client.search.argument.type.IdentifierArgumentType:matches(Ljava/lang/String;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z', # REI
    'me.shedaniel.rei.impl.client.search.argument.type.ModArgumentType:matches(Lme/shedaniel/rei/impl/client/search/argument/type/ModArgumentType$ModInfoPair;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z', # REI
    'me.shedaniel.rei.impl.client.search.argument.type.TagArgumentType:matches([Ljava/lang/String;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z', # REI
    'me.shedaniel.rei.impl.client.search.argument.type.TextArgumentType:matches(Ljava/lang/String;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z', # REI
    'me.shedaniel.rei.impl.client.search.argument.type.TooltipArgumentType:matches(Ljava/lang/String;Lme/shedaniel/rei/api/common/entry/EntryStack;Ljava/lang/String;Lnet/minecraft/util/Unit;)Z', # REI
    'vazkii.quark.client.module.ChestSearchingModule:namesMatch(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z',  # Quark legacy
    'vazkii.quark.content.client.module.ChestSearchingModule:namesMatch(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z',  # Quark
    'me.shedaniel.clothconfig2.forge.gui.entries.DropdownBoxEntry$DefaultDropdownMenuElement:search()V',  # Cloth Config
    'me.shedaniel.clothconfig2.gui.entries.DropdownBoxEntry$DefaultDropdownMenuElement:search()V',  # Cloth Config
    'mezz.jei.search.ElementSearchLowMem:matches(Ljava/lang/String;Lmezz/jei/core/search/PrefixInfo;Lmezz/jei/ingredients/IListElementInfo;)Z',  # JEI (low memory)
    'com.github.klikli_dev.occultism.client.gui.storage.StorageControllerGuiBase:itemMatchesSearch(Lnet/minecraft/world/item/ItemStack;)Z', # Occultism
    'com.github.klikli_dev.occultism.client.gui.storage.StorageControllerGuiBase:machineMatchesSearch(Lcom/github/klikli_dev/occultism/api/common/data/MachineReference;)Z', # Occultism,
    'pro.mikey.xray.gui.GuiSelectionScreen:lambda$updateSearch$7(Lpro/mikey/xray/utils/BlockData;)Z', #Advanced XRay Gui
    'pro.mikey.xray.gui.manage.GuiBlockList:lambda$reloadBlocks$1(Lpro/mikey/xray/store/GameBlockStore$BlockWithItemStack;)Z', #Advanced XRay Gui
    'com.minecolonies.coremod.client.gui.WindowPostBox:lambda$updateResources$1(Lnet/minecraft/world/item/ItemStack;)Z', # MineColonies
    'com.minecolonies.coremod.client.gui.modules.ItemListModuleWindow:lambda$updateResources$3(Lnet/minecraft/world/item/ItemStack;)Z', # MineColonies
    'com.minecolonies.coremod.client.gui.WindowSelectRes:updateResources()V', # MineColonies
    'com.minecolonies.coremod.client.gui.modules.EntityListModuleWindow:lambda$updateResources$3(Lnet/minecraft/resources/ResourceLocation;)Z', # MineColonies
    'me.desht.pneumaticcraft.api.crafting.recipe.AmadronRecipe:passesQuery(Ljava/lang/String;)Z', # PneumaticCraft Amadron
    'me.desht.pneumaticcraft.client.gui.ItemSearcherScreen$SearchEntry:test(Ljava/lang/String;)Z', # PneumaticCraft Item Search Upgrade
    'de.ellpeck.prettypipes.terminal.containers.ItemTerminalGui:lambda$updateWidgets$8(Ljava/lang/String;Lorg/apache/commons/lang3/tuple/Pair;)Z', # Pretty Pipes Terminal
    'dev.ftb.mods.ftblibrary.config.ui.SelectItemStackScreen$ItemStackButton:shouldAdd(Ljava/lang/String;Ljava/lang/String;)Z', # FTB Library(FTB Quests)
    'dev.ftb.mods.ftblibrary.ui.misc.ButtonListBaseScreen$1:add(Ldev/ftb/mods/ftblibrary/ui/Widget;)V' # FTB Library(FTB Quests)
    'me.shedaniel.rei.impl.client.search.method.DefaultInputMethod:contains(Ljava/lang/String;Ljava/lang/String;)Z', #REI default
]
equals = [
    'vazkii.botania.api.corporea.CorporeaRequestDefaultMatchers$CorporeaStringMatcher:equalOrContain(Ljava/lang/String;)Z',  # Botania (Corporea)
    'vazkii.quark.client.module.ChestSearchingModule:namesMatch(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z',  # Quark legacy
    'vazkii.quark.content.client.module.ChestSearchingModule:namesMatch(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z',  # Quark
    'org.cyclops.integrateddynamics.core.client.gui.WidgetTextFieldDropdown:lambda$refreshDropdownList$0(Lorg/cyclops/integrateddynamics/core/client/gui/IDropdownEntry;)Z',  # Integrated Dynamics
]
regExp = [
    'appeng.client.gui.me.fluids.FluidRepo:matchesSearch(Lappeng/client/gui/me/common/Repo$SearchMode;Ljava/util/regex/Pattern;Lappeng/api/storage/data/IAEFluidStack;)Z',  # Applied Energistics
    'appeng.client.gui.me.items.ItemRepo:matchesSearch(Lappeng/client/gui/me/common/Repo$SearchMode;Ljava/util/regex/Pattern;Lappeng/api/storage/data/IAEItemStack;)Z',  # Applied Energistics
    'org.cyclops.integrateddynamics.inventory.container.ContainerLogicProgrammerBase:lambda$static$0(Lorg/cyclops/integrateddynamics/api/logicprogrammer/ILogicProgrammerElement;Ljava/util/regex/Pattern;)Z',  # Integrated Dynamics 1
    'org.cyclops.integrateddynamics.core.inventory.container.ContainerMultipartAspects:lambda$new$0(Lorg/cyclops/integrateddynamics/api/part/aspect/IAspect;Ljava/util/regex/Pattern;)Z',  # Integrated Dynamics 2
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerFluidStack:lambda$getInstanceFilterPredicate$13(Ljava/lang/String;Lnet/minecraftforge/fluids/FluidStack;)Z',  # Integrated Terminals legacy
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerFluidStack:lambda$null$10(Ljava/lang/String;Lnet/minecraft/util/ResourceLocation;)Z',  # Integrated Terminals legacy
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack:lambda$getInstanceFilterPredicate$7(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;)Z',  # Integrated Terminals legacy
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack:lambda$null$2(Ljava/lang/String;Lnet/minecraft/util/text/ITextComponent;)Z',  # Integrated Terminals legacy
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack:lambda$null$4(Ljava/lang/String;Lnet/minecraft/util/ResourceLocation;)Z',  # Integrated Terminals legacy
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerFluidStack:lambda$getInstanceFilterPredicate$10(Ljava/lang/String;Lnet/minecraft/tags/TagKey;)Z',  # Integrated Terminals
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerFluidStack:lambda$getInstanceFilterPredicate$14(Ljava/lang/String;Lnet/minecraftforge/fluids/FluidStack;)Z',  # Integrated Terminals
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerFluidStack:lambda$getInstanceFilterPredicate$8(Ljava/lang/String;Lnet/minecraftforge/fluids/FluidStack;)Z',  # Integrated Terminals
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack:lambda$getInstanceFilterPredicate$1(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;)Z',  # Integrated Terminals
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack:lambda$getInstanceFilterPredicate$2(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)Z',  # Integrated Terminals
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack:lambda$getInstanceFilterPredicate$4(Ljava/lang/String;Lnet/minecraft/tags/TagKey;)Z',  # Integrated Terminals
    'org.cyclops.integratedterminals.capability.ingredient.IngredientComponentTerminalStorageHandlerItemStack:lambda$getInstanceFilterPredicate$8(Ljava/lang/String;Lnet/minecraft/world/item/ItemStack;)Z',  # Integrated Terminals
    'com.tom.storagemod.gui.GuiStorageTerminalBase:updateSearch()V', # Toms Storage
    'appeng.client.gui.me.search.SearchPredicates:lambda$createNamePredicate$2(Ljava/util/regex/Pattern;Lappeng/menu/me/common/GridInventoryEntry;)Z', #new Applied Energistics Terminals
    'appeng.client.gui.me.search.SearchPredicates:lambda$createTooltipPredicate$3(Lappeng/client/gui/me/search/RepoSearch;Ljava/util/regex/Pattern;Lappeng/menu/me/common/GridInventoryEntry;)Z', #new Applied Energistics Terminals
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
