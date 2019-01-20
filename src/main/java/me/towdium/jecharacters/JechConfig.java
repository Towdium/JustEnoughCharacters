package me.towdium.jecharacters;

import com.google.common.base.CaseFormat;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.util.FeedFetcher;
import me.towdium.jecharacters.util.Keyboard;
import me.towdium.jecharacters.util.VersionChecker;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

/**
 * Author:  Towdium
 * Created: 2016/6/14.
 */

public class JechConfig {
    public static Configuration config;
    public static Object empty;

    public static String[] listAdditionalString = new String[0];
    public static String[] listAdditionalRegExp = new String[0];
    public static String[] listAdditionalSuffix = new String[0];
    public static String[] listAdditionalStrsKt = new String[0];
    public static String[] listDefaultString = new String[0];
    public static String[] listDefaultRegExp = new String[0];
    public static String[] listDefaultSuffix = new String[0];
    public static String[] listDefaultStrsKt = new String[0];
    public static String[] listDumpClassFunc = new String[0];
    public static String[] listMethodBlacklist = new String[0];
    public static boolean enableRadicalMode = false;
    public static boolean enableJEI = true;
    public static boolean enableFuzzyZh2z = false;
    public static boolean enableFuzzySh2s = false;
    public static boolean enableFuzzyCh2c = false;
    public static boolean enableFuzzyAng2an = false;
    public static boolean enableFuzzyIng2in = false;
    public static boolean enableFuzzyEng2en = false;
    public static boolean enableFuzzyU2v = false;
    public static boolean enableForceQuote = false;
    public static boolean enableChatHelp = true;
    public static boolean enableDumpClassName = false;
    public static Keyboard keyboard = Keyboard.QUANPIN;

    public static void init(File location) {
        config = new Configuration(new File(location, "config/JustEnoughCharacters.cfg"), JechCore.VERSION);
        config.load();
        initProperties();
        handleFormerVersion();
        setValue();
        update();
        fetchOnline();
    }

    public static void update() {
        for (Item i : Item.values()) i.sync();
        config.save();
    }

    public static void fetchOnline() {
        Thread t = new Thread(FeedFetcher::fetch);
        t.setPriority(Thread.MIN_PRIORITY);
        t.run();
    }

    public static void setValue() {
        Item.LIST_DEFAULT_REGEXP.getProperty().set(((String[]) Item.LIST_DEFAULT_REGEXP.getDefault()));
        Item.LIST_DEFAULT_STRING.getProperty().set(((String[]) Item.LIST_DEFAULT_STRING.getDefault()));
    }

    public static void handleFormerVersion() {
        if (VersionChecker.checkVersion(config.getLoadedConfigVersion(), "1.12.0-1.10.0").toInt() < 0) {
            JechCore.LOG.info("Low version detected. Disabling radical.");
            Item.ENABLE_RADICAL_MODE.getProperty().set(false);
        }
    }

    public static void initProperties() {
        for (Item item : Item.values()) item.init();
    }

    public enum Item {
        LIST_ADDITIONAL_STRING,
        LIST_ADDITIONAL_REGEXP,
        LIST_ADDITIONAL_SUFFIX,
        LIST_ADDITIONAL_STRSKT,
        LIST_DEFAULT_STRING,
        LIST_DEFAULT_REGEXP,
        LIST_DEFAULT_SUFFIX,
        LIST_DEFAULT_STRSKT,
        LIST_DUMP_CLASS_FUNC,
        LIST_METHOD_BLACKLIST,
        ENABLE_RADICAL_MODE,
        ENABLE_JEI,
        ENABLE_CHAT_HELP,
        ENABLE_FUZZY_ZH2Z,
        ENABLE_FUZZY_SH2S,
        ENABLE_FUZZY_CH2C,
        ENABLE_FUZZY_ANG2AN,
        ENABLE_FUZZY_ING2IN,
        ENABLE_FUZZY_ENG2EN,
        ENABLE_FUZZY_U2V,
        ENABLE_FORCE_QUOTE,
        ENABLE_DUMP_CLASS_NAME,
        INT_KEYBOARD;


        public String getComment() {
            switch (this) {
                case LIST_ADDITIONAL_STRING:
                    return "Give a list of methods to transform, of which uses \"String.contains\" to match.\n" +
                            "The format is \"full.class.Path$InnerClass:methodName\"\n" +
                            "This list will also contain data fetched from online record.";
                case LIST_ADDITIONAL_REGEXP:
                    return "Give a list of methods to transform, of which uses regular expression to match.\n" +
                            "The format is \"full.class.path$InnerClass:methodName\"\n" +
                            "This list will also contain data fetched from online record.";
                case LIST_ADDITIONAL_SUFFIX:
                    return "Give a list of methods to transform, of which uses vanilla SuffixArray to match.\n" +
                            "The format is \"full.class.path$InnerClass:methodName\"\n" +
                            "This list will also contain data fetched from online record.";
                case LIST_ADDITIONAL_STRSKT:
                    return "Give a list of methods to transform, of which uses Kotlin Strings to match.\n" +
                            "The format is \"full.class.path$InnerClass:methodName\"\n" +
                            "This list will also contain data fetched from online record.";
                case LIST_DEFAULT_STRING:
                    return "Default list of methods to transform, of which uses \"String.contains\" to match.\n" +
                            "This list is maintained by the mod and will have no effect if you change it.";
                case LIST_DEFAULT_REGEXP:
                    return "Default list of methods to transform, of which uses regular expression to match.\n" +
                            "This list is maintained by the mod and will have no effect if you change it.";
                case LIST_DEFAULT_SUFFIX:
                    return "Default list of methods to transform, of which uses vanilla SuffixArray to match.\n" +
                            "This list is maintained by the mod and will have no effect if you change it.";
                case LIST_DEFAULT_STRSKT:
                    return "Default list of methods to transform, of which uses Kotlin Strings to match.\n" +
                            "This list is maintained by the mod and will have no effect if you change it.";
                case LIST_DUMP_CLASS_FUNC:
                    return "Dump all the methods in this class into log. Format is \"full.class.Path$InnerClass\".";
                case LIST_METHOD_BLACKLIST:
                    return "Put the strings in default list here to disable transform for certain method";
                case ENABLE_RADICAL_MODE:
                    return "Set to true to enable radical mode. Keep in mind this is DANGEROUS.\n" +
                            "This could support more mods but could lead to some strange behavior as well.";
                case ENABLE_JEI:
                    return "Set to false to disable JEI support.";
                case ENABLE_FUZZY_ZH2Z:
                    return "Set to true to enable fuzzy Zh <=> Z";
                case ENABLE_FUZZY_SH2S:
                    return "Set to true to enable fuzzy Sh <=> S";
                case ENABLE_FUZZY_CH2C:
                    return "Set to true to enable fuzzy Ch <=> C";
                case ENABLE_FUZZY_ANG2AN:
                    return "Set to true to enable fuzzy Ang <=> An";
                case ENABLE_FUZZY_ING2IN:
                    return "Set to true to enable fuzzy Ing <=> In";
                case ENABLE_FUZZY_ENG2EN:
                    return "Set to true to enable fuzzy Eng <=> En";
                case ENABLE_FUZZY_U2V:
                    return "Set to true to enable fuzzy U <=> V";
                case ENABLE_FORCE_QUOTE:
                    return "Set to true to disable JEI keyword separation";
                case ENABLE_CHAT_HELP:
                    return "Set to false to disable all the chat messages";
                case ENABLE_DUMP_CLASS_NAME:
                    return "Set to true to dump all the class names";
                case INT_KEYBOARD:
                    return "Choose keyboard: 0 for quanpin, 1 for phonetic (Daqian)";
            }
            return "";
        }

        public Type getType() {
            switch (this) {
                case LIST_ADDITIONAL_STRING:
                    return Type.LIST_STRING;
                case LIST_ADDITIONAL_REGEXP:
                    return Type.LIST_STRING;
                case LIST_ADDITIONAL_SUFFIX:
                    return Type.LIST_STRING;
                case LIST_ADDITIONAL_STRSKT:
                    return Type.LIST_STRING;
                case LIST_DEFAULT_STRING:
                    return Type.LIST_STRING;
                case LIST_DEFAULT_REGEXP:
                    return Type.LIST_STRING;
                case LIST_DEFAULT_SUFFIX:
                    return Type.LIST_STRING;
                case LIST_DEFAULT_STRSKT:
                    return Type.LIST_STRING;
                case LIST_DUMP_CLASS_FUNC:
                    return Type.LIST_STRING;
                case LIST_METHOD_BLACKLIST:
                    return Type.LIST_STRING;
                case ENABLE_RADICAL_MODE:
                    return Type.BOOLEAN;
                case ENABLE_JEI:
                    return Type.BOOLEAN;
                case ENABLE_FUZZY_ZH2Z:
                    return Type.BOOLEAN;
                case ENABLE_FUZZY_SH2S:
                    return Type.BOOLEAN;
                case ENABLE_FUZZY_CH2C:
                    return Type.BOOLEAN;
                case ENABLE_FUZZY_ANG2AN:
                    return Type.BOOLEAN;
                case ENABLE_FUZZY_ING2IN:
                    return Type.BOOLEAN;
                case ENABLE_FUZZY_ENG2EN:
                    return Type.BOOLEAN;
                case ENABLE_FUZZY_U2V:
                    return Type.BOOLEAN;
                case ENABLE_FORCE_QUOTE:
                    return Type.BOOLEAN;
                case INT_KEYBOARD:
                    return Type.INTEGER;
                case ENABLE_CHAT_HELP:
                    return Type.BOOLEAN;
                case ENABLE_DUMP_CLASS_NAME:
                    return Type.BOOLEAN;
            }
            return Type.ERROR;
        }

        public Object getDefault() {
            switch (this) {
                case LIST_ADDITIONAL_STRING:
                    return new String[0];
                case LIST_ADDITIONAL_REGEXP:
                    return new String[0];
                case LIST_ADDITIONAL_SUFFIX:
                    return new String[0];
                case LIST_ADDITIONAL_STRSKT:
                    return new String[0];
                case LIST_DEFAULT_STRING:
                    return new String[]{
                            "mezz.jei.ItemFilter$FilterPredicate:stringContainsTokens",  // JEI legacy
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterName:accepts",  // Refined Storage legpacy
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterTooltip:accepts",  // Refined Storage legacy
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterMod:test",  // Refined Storage item mod name
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterName:test",  // Refined Storage item name
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterOreDict:lambda$test$0",  // Refined Storage item oreDict
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterTooltip:test",  // Refined Storage item tooltip
                            "com.rwtema.extrautils2.transfernodes.TileIndexer$ContainerIndexer$WidgetItemRefButton:lambda$getRef$0",  // Extra Utilities indexer
                            "crazypants.enderio.machine.invpanel.client.ItemFilter$ModFilter:matches",  // EnderIO inventory panel item mod
                            "crazypants.enderio.machine.invpanel.client.ItemFilter$NameFilter:matches",  // EnderIO inventory panel item name
                            "vazkii.psi.client.gui.GuiProgrammer:shouldShow",  // Psi programmer search
                            "vazkii.botania.client.gui.lexicon.GuiLexiconIndex:matchesSearch",  // Botania manual
                            "de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:fitsFilter",  // Actually Additions manual
                            "de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:getChaptersForDisplay",  // Actually Additions manual
                            "com.zerofall.ezstorage.gui.client.GuiStorageCore:updateFilteredItems",  // EZ Storage search
                            "io.github.elytra.copo.inventory.ContainerVT:updateSlots",  // Correlated legacy
                            "io.github.elytra.copo.inventory.ContainerTerminal:updateSlots",  // Correlated legacy
                            "net.minecraft.client.gui.inventory.GuiContainerCreative:updateFilteredItems",  // vanilla creative search legacy
                            "bmp:updateFilteredItems",  // vanilla creative search legacy
                            "appeng.client.gui.implementation.GuiInterfaceTerminal:refreshList",  // Applied Energistics terminal interface legacy
                            "appeng.client.gui.implementation.GuiInterfaceTerminal:itemStackMatchesSearchTerm",  // Applied Energistics terminal interface legacy
                            "appeng.client.gui.implementations.GuiInterfaceTerminal:refreshList",  // Applied Energistics terminal interface
                            "appeng.client.gui.implementations.GuiInterfaceTerminal:itemStackMatchesSearchTerm",  // Applied Energistics terminal interface
                            "pers.towdium.just_enough_calculation.gui.guis.GuiPicker:updateLayout",  // JustEnoughCalculation legacy
                            "io.github.elytra.correlated.inventory.ContainerTerminal:updateSlots",  // Correlated legacy
                            "com.elytradev.correlated.inventory.ContainerTerminal:updateSlots",  // Correlated legacy
                            "sonar.logistics.client.gui.GuiFluidReader:getGridList",  // Practical Logistics fluid search
                            "sonar.logistics.client.gui.GuiGuide:updateSearchList",  // Practical Logistics manual
                            "sonar.logistics.client.gui.GuiInventoryReader:getGridList",  // Practical Logistics item search
                            "sonar.logistics.client.gui.GuiWirelessStorageReader:getGridList",  // Practical Logistics remote item search
                            "binnie.core.machines.storage.SearchDialog:updateSearch",  // BinnieCore chest search
                            "net.blay09.mods.cookingforblockheads.container.ContainerRecipeBook:search",  // Cooking for Blockheads workbench search
                            "mcjty.rftools.blocks.storagemonitor.GuiStorageScanner:updateContentsList",  // RFTools unknown
                            "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity:lambda$null$20",  // RFTools unknown
                            "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity:lambda$makeSearchPredicate$24",  // RFTools unknown
                            "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity:lambda$makeSearchPredicate$20",  // RFTools unknown
                            "mcjty.rftools.blocks.storage.GuiModularStorage:updateList",  // RFTools modular storage
                            "mcjty.rftools.blocks.shaper.LocatorTileEntity:checkFilter",  // RFTools unknown
                            "mcjty.rftools.items.netmonitor.GuiNetworkMonitor:populateList",  // RFTools network monitor
                            "moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory:doesItemMatchFilter",  // ProjectE item search
                            "moze_intel.projecte.utils.ItemSearchHelper$DefaultSearch:doesItemMatchFilter_",  // ProjectE legacy
                            "org.cyclops.integrateddynamics.core.client.gui.GuiTextFieldDropdown:func_146201_a",
                            "blusunrize.immersiveengineering.api.ManualPageBlueprint:listForSearch",  // Immersive Engineering manual
                            "blusunrize.lib.manual.ManualPages$Crafting:listForSearch",  // Immersive Engineering manual
                            "blusunrize.lib.manual.ManualPages$CraftingMulti:listForSearch",  // Immersive Engineering manual
                            "blusunrize.lib.manual.ManualPages$ItemDisplay:listForSearch",  // Immersive Engineering manual
                            "blusunrize.lib.manual.gui.GuiManual:func_73869_a",  // Immersive Engineering manual
                            "betterquesting.client.gui.editors.GuiPrerequisiteEditor:RefreshSearch",  // BetterQuesting prerequisite search
                            "betterquesting.client.gui.editors.GuiQuestLineEditorB:RefreshSearch",
                            "betterquesting.client.gui.editors.json.GuiJsonEntitySelection:updateSearch",  // BetterQuesting entity search
                            "betterquesting.client.gui.editors.json.GuiJsonFluidSelection:doSearch",  // BetterQuesting fluid search
                            "betterquesting.client.gui.editors.json.GuiJsonItemSelection:doSearch",  // BetterQuesting item search
                            "com.elytradev.correlated.C28n:contains",  // Correlated
                            "vswe.stevesfactory.components.ComponentMenuContainer$2:updateSearch",  // Steve's Factory Manager container search
                            "vswe.stevesfactory.components.ComponentMenuFluid:updateSearch",  // Steve's Factory Manager fluid search
                            "vswe.stevesfactory.components.ComponentMenuItem:updateSearch",  // Steve's Factory Manager item search
                            "com.mia.props.client.container.GuiDecobench:refreshButtons",  // Decofraft workbench
                            "mrriegel.storagenetwork.gui.GuiRequest:match",  // Storage Network
                            "vazkii.quark.client.feature.ChestSearchBar:lambda$namesMatch$0",  // quark chest
                            "logisticspipes.gui.orderer.GuiOrderer:isSearched",  // logistics pipes orderer
                            "logisticspipes.gui.orderer.GuiRequestTable:isSearched"  //logistics pipes request table
                    };
                case LIST_DEFAULT_REGEXP:
                    return new String[]{
                            "appeng.client.me.FluidRepo:updateView",  // Applied Energistics fluid search
                            "appeng.client.me.ItemRepo:updateView",  // Applied Energistics item search
                            "codechicken.nei.ItemList$PatternItemFilter:matches",  // NEI item list legacy
                            "codechicken.nei.util.ItemList$PatternItemFilter:matches",  // NEI item list
                            "org.cyclops.integrateddynamics.core.inventory.container.ContainerMultipartAspects$1:apply",
                            "org.cyclops.integrateddynamics.inventory.container.ContainerLogicProgrammerBase$1:apply",  // Integrated Dynamics programmer
                            "p455w0rd.wct.client.me.ItemRepo:updateView",  // Wireless Crafting Terminal
                            "vazkii.quark.client.feature.ChestSearchBar:lambda$namesMatch$2"  // quark chest
                    };
                case LIST_DEFAULT_SUFFIX:
                    return new String[]{
                            "net.minecraft.client.util.SearchTree:<init>",  // vanilla search
                            "net.minecraft.client.util.SearchTree:recalculate",  // vanilla search
                            "cgw:<init>",  // vanilla search notch name
                            "cgw:a",  // vanilla search notch name
                            "buildcraft.lib.client.guide.GuideManager:generateContentsPage"  // BuildCraft manual
                    };
                case LIST_DEFAULT_STRSKT:
                    return new String[]{
                            "com.cout970.magneticraft.features.multiblocks.ContainerShelvingUnit:filterSlots"  // Magneticraft shelving unit
                    };
                case LIST_DUMP_CLASS_FUNC:
                    return new String[0];
                case LIST_METHOD_BLACKLIST:
                    return new String[0];
                case ENABLE_RADICAL_MODE:
                    return false;
                case ENABLE_JEI:
                    return true;
                case ENABLE_FUZZY_ZH2Z:
                    return false;
                case ENABLE_FUZZY_SH2S:
                    return false;
                case ENABLE_FUZZY_CH2C:
                    return false;
                case ENABLE_FUZZY_ANG2AN:
                    return false;
                case ENABLE_FUZZY_ING2IN:
                    return false;
                case ENABLE_FUZZY_ENG2EN:
                    return false;
                case ENABLE_FUZZY_U2V:
                    return false;
                case ENABLE_FORCE_QUOTE:
                    return false;
                case INT_KEYBOARD:
                    return 0;
                case ENABLE_CHAT_HELP:
                    return true;
                case ENABLE_DUMP_CLASS_NAME:
                    return false;
            }
            return JechConfig.empty;
        }

        public void sync() {
            switch (this) {
                case LIST_ADDITIONAL_STRING:
                    listAdditionalString = getProperty().getStringList();
                    break;
                case LIST_ADDITIONAL_REGEXP:
                    listAdditionalRegExp = getProperty().getStringList();
                    break;
                case LIST_ADDITIONAL_SUFFIX:
                    listAdditionalSuffix = getProperty().getStringList();
                    break;
                case LIST_ADDITIONAL_STRSKT:
                    listAdditionalStrsKt = getProperty().getStringList();
                    break;
                case LIST_DEFAULT_STRING:
                    listDefaultString = getProperty().getStringList();
                    break;
                case LIST_DEFAULT_REGEXP:
                    listDefaultRegExp = getProperty().getStringList();
                    break;
                case LIST_DEFAULT_SUFFIX:
                    listDefaultSuffix = getProperty().getStringList();
                    break;
                case LIST_DEFAULT_STRSKT:
                    listDefaultStrsKt = getProperty().getStringList();
                    break;
                case LIST_DUMP_CLASS_FUNC:
                    listDumpClassFunc = getProperty().getStringList();
                    break;
                case LIST_METHOD_BLACKLIST:
                    listMethodBlacklist = getProperty().getStringList();
                    break;
                case ENABLE_RADICAL_MODE:
                    enableRadicalMode = getProperty().getBoolean();
                    break;
                case ENABLE_JEI:
                    enableJEI = getProperty().getBoolean();
                    break;
                case ENABLE_FUZZY_ZH2Z:
                    enableFuzzyZh2z = getProperty().getBoolean();
                    break;
                case ENABLE_FUZZY_SH2S:
                    enableFuzzySh2s = getProperty().getBoolean();
                    break;
                case ENABLE_FUZZY_CH2C:
                    enableFuzzyCh2c = getProperty().getBoolean();
                    break;
                case ENABLE_FUZZY_ANG2AN:
                    enableFuzzyAng2an = getProperty().getBoolean();
                    break;
                case ENABLE_FUZZY_ING2IN:
                    enableFuzzyIng2in = getProperty().getBoolean();
                    break;
                case ENABLE_FUZZY_ENG2EN:
                    enableFuzzyEng2en = getProperty().getBoolean();
                    break;
                case ENABLE_FUZZY_U2V:
                    enableFuzzyU2v = getProperty().getBoolean();
                    break;
                case ENABLE_FORCE_QUOTE:
                    enableForceQuote = getProperty().getBoolean();
                    break;
                case INT_KEYBOARD:
                    keyboard = Keyboard.get(getProperty().getInt());
                    break;
                case ENABLE_CHAT_HELP:
                    enableChatHelp = getProperty().getBoolean();
                case ENABLE_DUMP_CLASS_NAME:
                    enableDumpClassName = getProperty().getBoolean();
            }
        }

        public Category getCategory() {
            switch (this) {
                case LIST_ADDITIONAL_STRING:
                case LIST_ADDITIONAL_REGEXP:
                case LIST_ADDITIONAL_SUFFIX:
                case LIST_ADDITIONAL_STRSKT:
                case LIST_DEFAULT_STRING:
                case LIST_DEFAULT_REGEXP:
                case LIST_DEFAULT_SUFFIX:
                case LIST_DEFAULT_STRSKT:
                case LIST_METHOD_BLACKLIST:
                    return Category.TRANSFORM;
                case ENABLE_FUZZY_ZH2Z:
                case ENABLE_FUZZY_SH2S:
                case ENABLE_FUZZY_CH2C:
                case ENABLE_FUZZY_ANG2AN:
                case ENABLE_FUZZY_ING2IN:
                case ENABLE_FUZZY_ENG2EN:
                case ENABLE_FUZZY_U2V:
                    return Category.FUZZY;
                case LIST_DUMP_CLASS_FUNC:
                case ENABLE_DUMP_CLASS_NAME:
                case ENABLE_RADICAL_MODE:
                case ENABLE_JEI:
                case ENABLE_FORCE_QUOTE:
                case INT_KEYBOARD:
                case ENABLE_CHAT_HELP:
                    return Category.GENERAL;
            }
            throw new RuntimeException("Internal error.");
        }

        @SuppressWarnings("UnusedReturnValue")
        public Property init() {

            switch (this.getType()) {
                case BOOLEAN:
                    return config.get(getCategory().toString(), toString(), (Boolean) getDefault(), getComment());
                case LIST_STRING:
                    return config.get(getCategory().toString(), toString(), (String[]) getDefault(), getComment());
                case INTEGER:
                    return config.get(getCategory().toString(), toString(), (int) getDefault(), getComment());
            }
            throw new RuntimeException("Internal error.");
        }

        public Property getProperty() {
            return config.getCategory(getCategory().toString()).get(toString());
        }

        @Override
        public String toString() {
            return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, super.toString());
        }
    }

    public enum Category {
        GENERAL, FUZZY, TRANSFORM;

        @Override
        public String toString() {
            switch (this) {
                case GENERAL:
                    return "general";
                case FUZZY:
                    return "fuzzy";
                case TRANSFORM:
                    return "transform";
            }
            throw new RuntimeException("Runtime error.");
        }
    }

    public enum Type {BOOLEAN, LIST_STRING, INTEGER, ERROR}
}

