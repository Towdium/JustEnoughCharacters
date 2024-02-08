var api = Java.type('net.minecraftforge.coremod.api.ASMAPI')

function initializeCoreMod() {
    api.loadFile('coremods/lib.js');
    return {
        'jei-Parser-1': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.search.ElementPrefixParser',
                'methodName': '<clinit>',
                'methodDesc': '()V'
            },
            'transformer': transform
        },
        'jei-Parser-2': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.search.ElementPrefixParser',
                'methodName': '<init>',
                'methodDesc': '(Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/config/IIngredientFilterConfig;)V'
            },
            'transformer': transform
        },
        'jei-filter': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.ingredients.IngredientFilter',
                'methodName': 'parseSearchTokens',
                'methodDesc': '(Ljava/lang/String;)Lmezz/jei/ingredients/IngredientFilter$SearchTokens;'
            },
            'transformer': function (method) {
                var list = method.instructions;
                for (var i = 0; i < list.size(); i++) {
                    var node = list.get(i);
                    if (node.getOpcode() === opcodes.INVOKEVIRTUAL) {
                        var methodInsn = node;
                        if (methodInsn.owner === 'java/util/regex/Pattern' && methodInsn.name === 'matcher' && methodInsn.desc === '(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;') {
                            list.insert(node.getPrevious(), new insn(opcodes.INVOKESTATIC,
                                "me/towdium/jecharacters/utils/Match", "wrap",
                                "(Ljava/lang/String;)Ljava/lang/String;", false));
                            return method;
                        }
                    }
                }
            }
        }
    }
}

function transform(method) {
    transInvokeLambda(method,
        'mezz/jei/search/suffixtree/GeneralizedSuffixTree',
        '<init>',
        '()V',
        'me/towdium/jecharacters/utils/FakeTree',
        '<init>',
        '()V'
    );
    return method;
}