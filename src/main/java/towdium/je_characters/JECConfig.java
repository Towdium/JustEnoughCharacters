package towdium.je_characters;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

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
        ListMethodBlacklist;


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
                            "com.rwtema.extrautils2.transfernodes.TileIndexer$ContainerIndexer$WidgetItemRefButton:lambda$getRef$0",
                            "crazypants.enderio.machine.invpanel.client.ItemFilter$ModFilter:matches",
                            "crazypants.enderio.machine.invpanel.client.ItemFilter$NameFilter:matches",
                            "vazkii.psi.client.gui.GuiProgrammer:shouldShow",
                            "vazkii.botania.client.gui.lexicon.GuiLexiconIndex:matchesSearch"
                    };
                case ListDefaultRegExpMatch:
                    return new String[]{"appeng.client.me.ItemRepo:updateView"};
                case ListDumpClass:
                    return new String[0];
                case ListMethodBlacklist:
                    return new String[0];
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

