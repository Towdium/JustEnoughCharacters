package me.towdium.jecharacters;

import com.google.common.base.CaseFormat;
import me.towdium.jecharacters.core.JechCore;
import me.towdium.jecharacters.transform.TransformerRegistry;
import me.towdium.jecharacters.util.FeedFetcher;
import me.towdium.jecharacters.util.Keyboard;
import me.towdium.jecharacters.util.VersionChecker;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Author:  Towdium
 * Created: 2016/6/14.
 */

public class JechConfig {
    public static Configuration config;
    public static Object empty;

    public static String[] listAdditionalStringMatch = new String[0];
    public static String[] listAdditionalRegExpMatch = new String[0];
    public static String[] listDefaultStringMatch = new String[0];
    public static String[] listDefaultRegExpMatch = new String[0];
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
        Thread t = new Thread(() ->
                FeedFetcher.fetch((s, r) -> {
                    HashSet<String> buf = new HashSet<>();
                    Collections.addAll(buf, Item.LIST_ADDITIONAL_STRING_MATCH.getProperty().getStringList());
                    buf.addAll(s);
                    buf.removeAll(Arrays.asList(Item.LIST_DEFAULT_STRING_MATCH.getProperty().getStringList()));
                    Item.LIST_ADDITIONAL_STRING_MATCH.getProperty()
                            .set(buf.stream().sorted().collect(Collectors.toList()).toArray(new String[]{}));
                    buf.clear();
                    Collections.addAll(buf, Item.LIST_ADDITIONAL_REGEXP_MATCH.getProperty().getStringList());
                    buf.addAll(r);
                    buf.removeAll(Arrays.asList(Item.LIST_DEFAULT_REGEXP_MATCH.getProperty().getStringList()));
                    Item.LIST_ADDITIONAL_REGEXP_MATCH.getProperty()
                            .set(buf.stream().sorted().collect(Collectors.toList()).toArray(new String[]{}));
                    config.save();
                    TransformerRegistry.transformerRegExp.reload();
                    TransformerRegistry.transformerString.reload();
                }));
        t.setPriority(Thread.MIN_PRIORITY);
        t.run();
    }

    public static void setValue() {
        Item.LIST_DEFAULT_REGEXP_MATCH.getProperty().set(((String[]) Item.LIST_DEFAULT_REGEXP_MATCH.getDefault()));
        Item.LIST_DEFAULT_STRING_MATCH.getProperty().set(((String[]) Item.LIST_DEFAULT_STRING_MATCH.getDefault()));
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
        LIST_ADDITIONAL_STRING_MATCH,
        LIST_ADDITIONAL_REGEXP_MATCH,
        LIST_DEFAULT_STRING_MATCH,
        LIST_DEFAULT_REGEXP_MATCH,
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
                case LIST_ADDITIONAL_STRING_MATCH:
                    return "Give a list of methods to transform, of which uses \"String.contains\" to match.\n" +
                            "The format is \"full.class.Path$InnerClass:methodName\"\n" +
                            "This list will also contain data fetched from online record.";
                case LIST_ADDITIONAL_REGEXP_MATCH:
                    return "Give a list of methods to transform, of which uses regular expression to match.\n" +
                            "The format is \"full.class.path$InnerClass:methodName\"\n" +
                            "This list will also contain data fetched from online record.";
                case LIST_DEFAULT_STRING_MATCH:
                    return "Default list of methods to transform, of which uses \"String.contains\" to match.\n" +
                            "This list is maintained by the mod and will have no effect if you change it.";
                case LIST_DEFAULT_REGEXP_MATCH:
                    return "Default list of methods to transform, of which uses regular expression to match.\n" +
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
                case LIST_ADDITIONAL_STRING_MATCH:
                    return Type.LIST_STRING;
                case LIST_ADDITIONAL_REGEXP_MATCH:
                    return Type.LIST_STRING;
                case LIST_DEFAULT_STRING_MATCH:
                    return Type.LIST_STRING;
                case LIST_DEFAULT_REGEXP_MATCH:
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
                case LIST_ADDITIONAL_STRING_MATCH:
                    return new String[0];
                case LIST_ADDITIONAL_REGEXP_MATCH:
                    return new String[0];
                case LIST_DEFAULT_STRING_MATCH:
                    return new String[]{
                            "mezz.jei.ItemFilter$FilterPredicate:stringContainsTokens",
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterName:accepts",
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterTooltip:accepts",
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterName:test",
                            "com.raoulvdberge.refinedstorage.gui.grid.filtering.GridFilterTooltip:test",
                            "com.rwtema.extrautils2.transfernodes.TileIndexer$ContainerIndexer$WidgetItemRefButton:lambda$getRef$0",
                            "crazypants.enderio.machine.invpanel.client.ItemFilter$ModFilter:matches",
                            "crazypants.enderio.machine.invpanel.client.ItemFilter$NameFilter:matches",
                            "vazkii.psi.client.gui.GuiProgrammer:shouldShow",
                            "vazkii.botania.client.gui.lexicon.GuiLexiconIndex:matchesSearch",
                            "de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:fitsFilter",
                            "de.ellpeck.actuallyadditions.mod.booklet.entry.BookletEntry:getChaptersForDisplay",
                            "com.zerofall.ezstorage.gui.client.GuiStorageCore:updateFilteredItems",
                            "io.github.elytra.copo.inventory.ContainerVT:updateSlots",
                            "io.github.elytra.copo.inventory.ContainerTerminal:updateSlots",
                            "net.minecraft.client.gui.inventory.GuiContainerCreative:updateFilteredItems",
                            "bmp:updateFilteredItems",
                            "appeng.client.gui.implementation.GuiInterfaceTerminal:refreshList",
                            "appeng.client.gui.implementation.GuiInterfaceTerminal:itemStackMatchesSearchTerm",
                            "pers.towdium.just_enough_calculation.gui.guis.GuiPicker:updateLayout",
                            "io.github.elytra.correlated.inventory.ContainerTerminal:updateSlots",
                            "com.elytradev.correlated.inventory.ContainerTerminal:updateSlots",
                            "sonar.logistics.client.gui.GuiFluidReader:getGridList",
                            "sonar.logistics.client.gui.GuiGuide:updateSearchList",
                            "sonar.logistics.client.gui.GuiInventoryReader:getGridList",
                            "sonar.logistics.client.gui.GuiWirelessStorageReader:getGridList",
                            "binnie.core.machines.storage.SearchDialog:updateSearch",
                            "net.blay09.mods.cookingforblockheads.container.ContainerRecipeBook:search",
                            "mcjty.rftools.blocks.storagemonitor.GuiStorageScanner:updateContentsList",
                            "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity:lambda$null$20",
                            "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity:lambda$makeSearchPredicate$24",
                            "mcjty.rftools.blocks.storagemonitor.StorageScannerTileEntity:lambda$makeSearchPredicate$20",
                            "mcjty.rftools.blocks.storage.GuiModularStorage:updateList",
                            "mcjty.rftools.blocks.shaper.LocatorTileEntity:checkFilter",
                            "mcjty.rftools.items.netmonitor.GuiNetworkMonitor:populateList",
                            "moze_intel.projecte.utils.ItemSearchHelper$DefaultSearch:doesItemMatchFilter_",
                            "org.cyclops.integrateddynamics.core.client.gui.GuiTextFieldDropdown:func_146201_a",
                            "blusunrize.immersiveengineering.api.ManualPageBlueprint:listForSearch",
                            "blusunrize.lib.manual.ManualPages$Crafting:listForSearch",
                            "blusunrize.lib.manual.ManualPages$CraftingMulti:listForSearch",
                            "blusunrize.lib.manual.ManualPages$ItemDisplay:listForSearch",
                            "blusunrize.lib.manual.gui.GuiManual:func_73869_a",
                            "betterquesting.client.gui.editors.GuiPrerequisiteEditor:RefreshSearch",
                            "betterquesting.client.gui.editors.GuiQuestLineEditorB:RefreshSearch",
                            "betterquesting.client.gui.editors.json.GuiJsonEntitySelection:updateSearch",
                            "betterquesting.client.gui.editors.json.GuiJsonFluidSelection:doSearch",
                            "betterquesting.client.gui.editors.json.GuiJsonItemSelection:doSearch",
                            "com.elytradev.correlated.C28n:contains",
                            "vswe.stevesfactory.components.ComponentMenuContainer$2:updateSearch",
                            "vswe.stevesfactory.components.ComponentMenuFluid:updateSearch",
                            "vswe.stevesfactory.components.ComponentMenuItem:updateSearch",
                            "com.mia.props.client.container.GuiDecobench:refreshButtons",
                            "mrriegel.storagenetwork.gui.GuiRequest:match"
                    };
                case LIST_DEFAULT_REGEXP_MATCH:
                    return new String[]{
                            "appeng.client.me.ItemRepo:updateView",
                            "codechicken.nei.ItemList$PatternItemFilter:matches",
                            "codechicken.nei.util.ItemList$PatternItemFilter:matches",
                            "org.cyclops.integrateddynamics.core.inventory.container.ContainerMultipartAspects$1:apply",
                            "org.cyclops.integrateddynamics.inventory.container.ContainerLogicProgrammerBase$1:apply",
                            "p455w0rd.wct.client.me.ItemRepo:updateView"
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
                case LIST_ADDITIONAL_STRING_MATCH:
                    listAdditionalStringMatch = getProperty().getStringList();
                    break;
                case LIST_ADDITIONAL_REGEXP_MATCH:
                    listAdditionalRegExpMatch = getProperty().getStringList();
                    break;
                case LIST_DEFAULT_STRING_MATCH:
                    listDefaultStringMatch = getProperty().getStringList();
                    break;
                case LIST_DEFAULT_REGEXP_MATCH:
                    listDefaultRegExpMatch = getProperty().getStringList();
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
                case LIST_ADDITIONAL_STRING_MATCH:
                case LIST_ADDITIONAL_REGEXP_MATCH:
                case LIST_DEFAULT_STRING_MATCH:
                case LIST_DEFAULT_REGEXP_MATCH:
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

