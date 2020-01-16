function initializeCoreMod() {
    var insn = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var opcodes = Java.type('org.objectweb.asm.Opcodes');
    return {
        'jecharacters-jei3': {
            'target': {
                'type': 'METHOD',
                'class': 'mezz.jei.ingredients.IngredientFilter',
                'methodName': 'getElements',
                'methodDesc': '(Ljava/lang/String;)Lit/unimi/dsi/fastutil/ints/IntSet;'
            },
            'transformer': function (method) {
                var list = method.instructions;
                list.insert(list.get(3), new insn(opcodes.INVOKESTATIC,
                    "me/towdium/jecharacters/JechSearcher", "wrap",
                    "(Ljava/lang/String;)Ljava/lang/String;", false));
                return method;
            }
        }
    }
}