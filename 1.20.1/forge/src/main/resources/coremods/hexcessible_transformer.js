var Opcodes = Java.type('org.objectweb.asm.Opcodes');

var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');

function initializeCoreMod() {
    return {
        'jec-hexcessible-fluffy-search': {
            'target': {
                'type': 'METHOD',
                'class': 'dev.tizu.hexcessible.Utils',
                'methodName': 'fluffySearch',
                'methodDesc': '(Ljava/lang/String;Ljava/lang/String;)I'
            },
            'transformer': function (method) {
                var instructions = method.instructions;
                var node = instructions.getFirst();
                var transformedReturns = 0;

                while (node !== null) {
                    var next = node.getNext();
                    if (node.getOpcode() === Opcodes.IRETURN) {
                        var injected = new InsnList();
                        injected.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        injected.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        injected.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            'me/towdium/jecharacters/utils/HexcessibleHooks',
                            'mergeScore',
                            '(ILjava/lang/String;Ljava/lang/String;)I',
                            false));

                        instructions.insertBefore(node, injected);

                        transformedReturns++;
                    }
                    node = next;
                }

                return method;
            }
        }
    };
}