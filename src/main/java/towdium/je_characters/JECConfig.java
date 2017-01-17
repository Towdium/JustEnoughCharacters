package towdium.je_characters;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import towdium.je_characters.jei.TransformHelper;

import java.io.File;

/**
 * Author:  Towdium
 * Created: 2016/6/14.
 */

public class JECConfig {
    public static Configuration config;
    public static Object empty;

    public static void preInit(File location) {
        config = new Configuration(new File(location, "config/JustEnoughCharacters.cfg"), "@VERSION@");
        config.load();
        handleFormerVersion();
        initProperties();
        setValue();
        ClassTransformer.init();
        CheckHelper.class.getClass();
        TransformHelper.class.getClass();
        config.save();
        LoadingPlugin.initialized = true;
    }

    public static void setValue() {
        EnumItems.ListDefaultRegExpMatch.getProperty().set(((String[]) EnumItems.ListDefaultRegExpMatch.getDefault()));
        EnumItems.ListDefaultStringMatch.getProperty().set(((String[]) EnumItems.ListDefaultStringMatch.getDefault()));
    }

    public static void handleFormerVersion() {
    }

    public static void initProperties() {
        for (EnumItems item : EnumItems.values()) {
            item.init();
        }
    }

    public static void save() {
        config.save();
    }

    public enum EnumItems {
        ListAdditionalStringMatch,
        ListAdditionalRegExpMatch,
        ListDefaultStringMatch,
        ListDefaultRegExpMatch,
        ListDumpClass,
        ListMethodBlacklist,
        EnableRadicalMode,
        EnableJEI;


        public String getComment() {
            switch (this) {
                case ListAdditionalStringMatch:
                    return "Give a list of methods to transform, of which uses \"String.contains\" to match strings.\n" +
                            "The format is \"full.class.path$InnerClass:methodName\"";
                case ListAdditionalRegExpMatch:
                    return "Give a list of methods to transform, of which uses regular expression to match strings.\n" +
                            "The format is \"full.class.path$InnerClass:methodName\"";
                case ListDefaultStringMatch:
                    return "Default list of methods to transform, of which uses \"String.contains\" to match strings.\n" +
                            "This list is maintained by the mod and will have no effect if you change it.";
                case ListDefaultRegExpMatch:
                    return "Default list of methods to transform, of which uses regular expression to match strings.\n" +
                            "This list is maintained by the mod and will have no effect if you change it.";
                case ListDumpClass:
                    return "Dump all the methods in this class into log.";
                case ListMethodBlacklist:
                    return "Put the strings in default list here to disable transform for certain method";
                case EnableRadicalMode:
                    return "Set to false to disable radical mode transform.\n" +
                            "When in radical mode, this mod will try to change every instance of \"contains\",\n" +
                            "so every mod using this method will be supported, while this could lead to\n" +
                            "unexpected problems and slow down the launching speed.";
                case EnableJEI:
                    return "In the 1.11 version of JEI, the test filtering mechanics has been changed to a prefix tree, \n" +
                            "which is incompatible with Chinese pinyin system. So I have to entirely change the behavior.\n" +
                            "Specifically, I have ported the original filtering mechanics in 1.10.\n" +
                            "Therefore, I'm afraid new filtering features will not be implemented very soon and\n" +
                            "this might easily be broken and lead to bugs when JEI is having major changes.\n" +
                            "If you are enabling this feature and having some JEI related crashes, do provide the\n" +
                            "crash report to me before providing to the JEI page since this is most possibly caused\n" +
                            "by this mod.";
            }
            return "";
        }

        public String getCategory() {
            switch (this) {
                case ListAdditionalStringMatch:
                    return EnumCategory.General.toString();
                case ListAdditionalRegExpMatch:
                    return EnumCategory.General.toString();
                case ListDefaultStringMatch:
                    return EnumCategory.General.toString();
                case ListDefaultRegExpMatch:
                    return EnumCategory.General.toString();
                case ListDumpClass:
                    return EnumCategory.General.toString();
                case ListMethodBlacklist:
                    return EnumCategory.General.toString();
                case EnableRadicalMode:
                    return EnumCategory.General.toString();
                case EnableJEI:
                    return EnumCategory.General.toString();
            }
            return "";
        }

        public EnumType getType() {
            switch (this) {
                case ListAdditionalStringMatch:
                    return EnumType.ListString;
                case ListAdditionalRegExpMatch:
                    return EnumType.ListString;
                case ListDefaultStringMatch:
                    return EnumType.ListString;
                case ListDefaultRegExpMatch:
                    return EnumType.ListString;
                case ListDumpClass:
                    return EnumType.ListString;
                case ListMethodBlacklist:
                    return EnumType.ListString;
                case EnableRadicalMode:
                    return EnumType.Boolean;
                case EnableJEI:
                    return EnumType.Boolean;
            }
            return EnumType.Error;
        }

        public Object getDefault() {
            switch (this) {
                case ListAdditionalStringMatch:
                    return new String[0];
                case ListAdditionalRegExpMatch:
                    return new String[0];
                case ListDefaultStringMatch:
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
                            "mcjty.rftools.blocks.storage.GuiModularStorage:updateList",
                            "net.minecraft.client.gui.inventory.GuiContainerCreative:updateFilteredItems"
                    };
                case ListDefaultRegExpMatch:
                    return new String[]{
                            "appeng.client.me.ItemRepo:updateView",
                            "codechicken.nei.ItemList$PatternItemFilter:matches"
                    };
                case ListDumpClass:
                    return new String[0];
                case ListMethodBlacklist:
                    return new String[0];
                case EnableRadicalMode:
                    return true;
                case EnableJEI:
                    return false;
            }
            return JECConfig.empty;
        }

        public Property init() {
            EnumType type = this.getType();
            if (type != null) {
                switch (this.getType()) {
                    case Boolean:
                        return config.get(this.getCategory(), this.toString(), (Boolean) this.getDefault(), this.getComment());
                    case ListString:
                        return config.get(this.getCategory(), this.toString(), (String[]) this.getDefault(), this.getComment());
                }
                config.getCategory(EnumCategory.General.toString()).get(this.toString());
            }
            return config.get(this.getCategory(), this.toString(), false, this.getComment());
        }

        public Property getProperty() {
            return config.getCategory(EnumCategory.General.toString()).get(this.toString());
        }
    }

    public enum EnumCategory {
        General;

        @Override
        public String toString() {
            switch (this) {
                case General:
                    return "general";
                default:
                    return "";
            }
        }
    }

    public enum EnumType {Boolean, ListString, Error}
}

