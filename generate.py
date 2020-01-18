import os

manual = ['jei1', 'jei2', 'jei3']
suffix = [
    'net.minecraft.client.util.SearchTree:<init>(Ljava/util/function/Function;Ljava/util/function/Function;)V',
    'net.minecraft.client.util.SearchTree:func_194040_a()V',
    'net.minecraft.client.util.SearchTreeReloadable:<init>(Ljava/util/function/Function;)V',
    'net.minecraft.client.util.SearchTreeReloadable:func_194040_a()V'
]
string = [
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$8(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # controlling 1
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$7(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z',  # controlling 2
    'com.blamejared.controlling.client.gui.GuiNewControls:lambda$filterKeys$6(Lcom/blamejared/controlling/client/gui/GuiNewKeyBindingList$KeyEntry;)Z'   # controlling 3
]
strsKt = []
regExp = []
pattern = """// Generated
function initializeCoreMod() {{
    Java.type('net.minecraftforge.coremod.api.ASMAPI').loadFile('me/towdium/jecharacters/lib.js');
    return {{
        'jecharacters-gen{idx}': {{
            'target': {{
                'type': 'METHOD',
                'class': '{clazz}',
                'methodName': Api.mapMethod('{name}'),
                'methodDesc': '{desc}'
            }},
            'transformer': trans{op}
        }}
    }}
}}
"""


def decode(s):
    idx1 = s.index(':')
    idx2 = s.index('(')
    return {
        'clazz': s[:idx1],
        'name': s[idx1 + 1: idx2],
        'desc': s[idx2:]
    }


if __name__ == '__main__':
    total = 0
    file = 'src/main/resources/me/towdium/jecharacters/gen{}.js'
    path = 'src/main/resources/me/towdium/jecharacters/'
    for i in os.listdir(path):
        if i.startswith('gen'):
            os.remove(path + i)

    for i in ['string', 'suffix', 'strsKt', 'regExp']:
        for j in globals()[i]:
            print(j)
            s = pattern.format(idx=total, **decode(j), op=i.capitalize())
            with open(file.format(total), 'w') as f:
                f.write(s)
            total += 1

    json = '{'
    for i in manual:
        json += '\n  "jecharacters-{0}": "me/towdium/jecharacters/{0}.js",'.format(i)
    for i in range(total):
        json += '\n  "jecharacters-gen{0}": "me/towdium/jecharacters/gen{0}.js",'.format(i)
    json = json[:-1] + '\n}\n'
    with open('src/main/resources/META-INF/coremods.json', 'w') as f:
        f.write(json)
