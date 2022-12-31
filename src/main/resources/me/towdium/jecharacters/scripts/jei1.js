function initializeCoreMod() {
    Java.type('net.minecraftforge.coremod.api.ASMAPI').loadFile('me/towdium/jecharacters/scripts/_lib.js');
    return {
        'jecharacters-jei1-legacy': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.common.search.ElementPrefixParser',
                'methodName': "<clinit>",
                'methodDesc': '()V'
            },
            'transformer': transform
        },
        'jecharacters-jei1-new': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.gui.search.ElementPrefixParser',
                'methodName': "<clinit>",
                'methodDesc': '()V'
            },
            'transformer': transform
        }
    }
}

function transform(method) {
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