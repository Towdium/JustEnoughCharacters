function initializeCoreMod() {
    return {
        'jecharacters-psi': {
            'target': {
                'type': 'METHOD',
                'class': 'vazkii.psi.client.gui.GuiProgrammer',
                'methodName': 'ranking',
                'methodDesc': '(Ljava/lang/String;Lvazkii/psi/api/spell/SpellPiece;)I'
            },
            'transformer': function (method) {
                transInvoke(method,
                    'vazkii/psi/client/gui/GuiProgrammer',
                    'rankTextToken',
                    '(Ljava/lang/String;Ljava/lang/String;)I',
                    'me/towdium/jecharacters/utils/Match',
                    'rank',
                    '(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)I'
                );
                return method;
            }
        }
    }
}