var Ops = Java.type('org.objectweb.asm.Opcodes');
var InsnMethod = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var InsnDynamic = Java.type('org.objectweb.asm.tree.InvokeDynamicInsnNode');
var Handle = Java.type('org.objectweb.asm.Handle');
var Api = Java.type('net.minecraftforge.coremod.api.ASMAPI');

function transConstruct(src, dst) {
    return function (method) {
        var i = method.instructions.iterator();
        while (i.hasNext()) {
            var n = i.next();
            if (n.getOpcode() === Ops.NEW) {
                if (n.desc === src) {
                    n.desc = dst;
                    print(src)
                }
            } else if (n.getOpcode() === Ops.INVOKESPECIAL) {
                if (n.owner === src) n.owner = dst;
            }
        }
        return method;
    };
}

function transInvoke(srcOwner, srcName, srcDesc, dstOwner, dstName, dstDesc) {
    return function (method) {
        var i = method.instructions.iterator();
        while (i.hasNext()) {
            var n = i.next();
            var op = n.getOpcode();
            if (n instanceof InsnMethod && n.owner === srcOwner && n.name === srcName && n.desc === srcDesc
                && (Ops.INVOKEVIRTUAL === op || Ops.INVOKESPECIAL === op || Ops.INVOKESTATIC === op)) {
                n.setOpcode(Ops.INVOKESTATIC);
                n.owner = dstOwner;
                n.name = dstName;
                n.desc = dstDesc
            } else if (n instanceof InsnDynamic && op === Ops.INVOKEDYNAMIC) {
                var h = n.bsmArgs[1];
                if (h.getOwner() === srcOwner && h.getName() === srcName && h.getDesc() === srcDesc)
                    n.bsmArgs[1] = new Handle(Ops.H_INVOKESTATIC, dstOwner, dstName, dstDesc);
            }
        }
        return method;
    };
}

var transString = transInvoke(
    'java/lang/String',
    'contains',
    '(Ljava/lang/CharSequence;)Z',
    'me/towdium/jecharacters/JechMatcher',
    'contains',
    '(Ljava/lang/String;Ljava/lang/CharSequence;)Z'
);

var transSuffix = transConstruct(
    'net/minecraft/client/util/SuffixArray',
    'me/towdium/jecharacters/JechMatcher$FakeArray'
);
