var Ops = Java.type('org.objectweb.asm.Opcodes');
var InsnMethod = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var InsnDynamic = Java.type('org.objectweb.asm.tree.InvokeDynamicInsnNode');
var Handle = Java.type('org.objectweb.asm.Handle');
var Api = Java.type('net.minecraftforge.coremod.api.ASMAPI');

function transConstruct(method, src, dst) {
    var i = method.instructions.iterator();
    while (i.hasNext()) {
        var n = i.next();
        if (n.getOpcode() === Ops.NEW) {
            if (n.desc === src) {
                n.desc = dst;
            }
        } else if (n.getOpcode() === Ops.INVOKESPECIAL) {
            if (n.owner === src) n.owner = dst;
        }
    }
}

function transInvoke(method, srcOwner, srcName, srcDesc, dstOwner, dstName, dstDesc) {
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
            if (h instanceof Handle) {
                if (h.getOwner() === srcOwner && h.getName() === srcName && h.getDesc() === srcDesc)
                    n.bsmArgs[1] = new Handle(Ops.H_INVOKESTATIC, dstOwner, dstName, dstDesc);
            }
        }
    }
}

function transInvokeLambda(method, srcOwner, srcName, srcDesc, dstOwner, dstName, dstDesc) {
    var i = method.instructions.iterator();
    while (i.hasNext()) {
        var n = i.next();
        var op = n.getOpcode();
        if (n instanceof InsnDynamic && op === Ops.INVOKEDYNAMIC) {
            var h = n.bsmArgs[1];
            if (h instanceof Handle) {
                if (h.getOwner() === srcOwner && h.getName() === srcName && h.getDesc() === srcDesc)
                    n.bsmArgs[1] = new Handle(h.tag, dstOwner, dstName, dstDesc);
            }
        }
    }
}

var transContains = function (method) {
    transInvoke(method,
        'java/lang/String',
        'contains',
        '(Ljava/lang/CharSequence;)Z',
        'me/towdium/jecharacters/utils/Match',
        'contains',
        '(Ljava/lang/String;Ljava/lang/CharSequence;)Z'
    );
    transInvoke(method,
        'kotlin/text/StringsKt',
        'contains',
        '(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z',
        'me/towdium/jecharacters/utils/Match',
        'contains',
        '(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z'
    );
    transInvoke(method,
        'kotlin/text/StringsKt',
        'contains',
        '(Ljava/lang/CharSequence;Ljava/lang/CharSequence)Z',
        'me/towdium/jecharacters/utils/Match',
        'contains',
        '(Ljava/lang/CharSequence;Ljava/lang/CharSequence)Z'
    );
    return method;
};

var transSuffix = function (method, suffixClass) {
    transConstruct(method,
        suffixClass,
        'me/towdium/jecharacters/utils/FakeArray'
    );
    return method;
};

var transRegExp = function (method) {
    transInvoke(method,
        'java/util/regex/Pattern',
        'matcher',
        '(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;',
        'me/towdium/jecharacters/utils/Match',
        'matcher',
        '(Ljava/util/regex/Pattern;Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;'
    );
    transInvoke(method,
        'java/lang/String',
        'matches',
        '(Ljava/lang/String;)Z',
        'me/towdium/jecharacters/utils/Match',
        'matches',
        '(Ljava/lang/String;Ljava/lang/String;)Z'
    );
    return method;
};

var transEquals = function (method) {
    transInvoke(method,
        'java/lang/String',
        'equals',
        '(Ljava/lang/Object;)Z',
        'me/towdium/jecharacters/utils/Match',
        'equals',
        '(Ljava/lang/String;Ljava/lang/Object;)Z'
    );
    return method;
};

function logMsg(msg) {
    api.log('INFO', msg, ['a'])
}