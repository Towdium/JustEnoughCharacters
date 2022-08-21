function initializeCoreMod() {
    Java.type('net.minecraftforge.coremod.api.ASMAPI').loadFile('me/towdium/jecharacters/scripts/_lib.js');
    return {
        'jecharacters-jei9-1': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.search.ElementPrefixParser',
                'methodName': "<clinit>",
                'methodDesc': '()V'
            },
            'transformer': function (method) {
                transInvokeLambda(method,
                    'mezz/jei/core/search/suffixtree/GeneralizedSuffixTree',
                    '<init>',
                    '()V',
                    'me/towdium/jecharacters/utils/Match$FakeTree',
                    '<init>',
                    '()V'
                );
                return method;
            }
        },
        'jecharacters-jei10-1' :{
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.common.search.ElementPrefixParser',
                'methodName': "<clinit>",
                'methodDesc': '()V'
            },
            'transformer': function (method) {
                transInvokeLambda(method,
                    'mezz/jei/core/search/suffixtree/GeneralizedSuffixTree',
                    '<init>',
                    '()V',
                    'me/towdium/jecharacters/utils/Match$FakeTree',
                    '<init>',
                    '()V'
                );
                return method;
            }
        }
    }
}