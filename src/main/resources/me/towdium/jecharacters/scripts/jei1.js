function initializeCoreMod() {
    Java.type('net.minecraftforge.coremod.api.ASMAPI').loadFile('me/towdium/jecharacters/scripts/_lib.js');
    return {
        'jecharacters-jei1': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.search.ElementSearch',
                'methodName': 'registerPrefix',
                'methodDesc': '(Lmezz/jei/search/PrefixInfo;)V'
            },
            'transformer': function (method) {
                transConstruct(method,
                    'mezz/jei/search/suffixtree/GeneralizedSuffixTree',
                    'me/towdium/jecharacters/utils/Match$FakeTree'
                );
                return method;
            }
        }
    }
}