var api = Java.type('net.minecraftforge.coremod.api.ASMAPI')

function initializeCoreMod() {
    api.loadFile('coremods/lib.js');
    var json = api.loadData('me/towdium/jecharacters/targets.json');
    var removal = json['removals']
    var ret = {}
    var suffixClass = json['suffixClassName']
    for (name in json) {
        if (name !== 'removals' && name !== 'suffixClassName') {
            addTarget(ret, name, json[name], removal, suffixClass)
        }
    }
    return ret
}

function addTarget(ret, type, data, removal, suffixClass) {
    var all = []
    var defaultArray = data['default']
    for (i = 0; i < defaultArray.length; i++) {
        all.push(defaultArray[i])
    }
    var additional = data['additional']
    for (i = 0; i < additional.length; i++) {
        all.push(additional[i])
    }
    var targets = []

    out : for (i = 0; i < all.length; i++) {
        var entry = all[i]
        for (j = 0; j < removal.length; j++) {
            var toRemove = removal[j]
            if (toRemove === entry)
                continue out
        }
        targets.push(entry)
    }


    var transformer = getTransformerFunc(type, suffixClass)
    for (i = 0; i < targets.length; i++) {
        var name = type + '-' + i
        var entry = targets[i]
        var split = entry.split(':')
        var index = split[1].indexOf('(')
        var methodName = split[1].substring(0, index)
        var methodDesc = split[1].substring(index)
        ret[name] = {
            'target': {
                'type': 'METHOD',
                'class': split[0],
                'methodName': methodName,
                'methodDesc': methodDesc
            },
            'transformer': transformer
        }
    }
}

function getTransformerFunc(type, suffixClass) {
    type = type.toLowerCase()
    if (type === 'contains')
        return transContains
    if (type === "equals")
        return transEquals
    if (type === "suffix")
        return function (method) {
            return transSuffix(method, suffixClass)
        }
    if (type === "regexp")
        return transRegExp
}