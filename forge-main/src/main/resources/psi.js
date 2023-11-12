function initializeCoreMod() {
    Java.type('net.minecraftforge.coremod.api.ASMAPI').loadFile('me/towdium/jecharacters/scripts/_lib.js');
    return {
        'jecharacters-psi': {
            'target': {
                'type': 'METHOD',
                'class': 'vazkii.psi.client.gui.widget.PiecePanelWidget',
                'methodName': 'ranking',
                'methodDesc': '(Ljava/lang/String;Lvazkii/psi/api/spell/SpellPiece;)I'
            },
            'transformer': function (method) {
                transInvoke(method,
                    'vazkii/psi/client/gui/widget/PiecePanelWidget',
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