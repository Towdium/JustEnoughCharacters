function initializeCoreMod() {
    Java.type('net.minecraftforge.coremod.api.ASMAPI').loadFile('me/towdium/jecharacters/_lib.js');
    return {
        'jecharacters-jei1': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.ingredients.IngredientFilter',
                'methodName': '<init>',
                'methodDesc': '(Lmezz/jei/ingredients/IngredientBlacklistInternal;' +
                    'Lmezz/jei/config/IIngredientFilterConfig;Lmezz/jei/config/IEditModeConfig;' +
                    'Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/helpers/IModIdHelper;)V'
            },
            'transformer': function (method) {
                transConstruct(method,
                    'mezz/jei/suffixtree/GeneralizedSuffixTree',
                    'me/towdium/jecharacters/utils/Match$FakeTree'
                );
                return method;
            }
        }
    }
}