function constructor(src, dst) {
    var opcodes = Java.type('org.objectweb.asm.Opcodes');
    return function (method) {
        var i = method.instructions.iterator();
        while (i.hasNext()) {
            var node = i.next();
            if (node.getOpcode() === opcodes.NEW) {
                if (node.desc === src) node.desc = dst;
            } else if (node.getOpcode() === opcodes.INVOKESPECIAL) {
                if (node.owner === src) node.owner = dst;
            }
        }
        return method;
    };
}