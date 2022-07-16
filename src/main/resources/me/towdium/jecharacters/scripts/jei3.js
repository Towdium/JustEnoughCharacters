function initializeCoreMod() {
    var insn = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var opcodes = Java.type('org.objectweb.asm.Opcodes');
    return {
        'jecharacters-jei3': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.common.ingredients.IngredientFilter',
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