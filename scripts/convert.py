from collections import defaultdict
from re import compile

l_initial = [
    'b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k',
    'h', 'z', 'c', 's', 'zh', 'ch', 'sh', 'j', 'q',
    'x', 'y', 'w', 'r', ''
]

l_final = [
    'a', 'o', 'e', 'i', 'u', 'v', 'ai', 'ei', 'ui',
    'ao', 'ou', 'iu', 'an', 'ang', 'in', 'ing', 'en',
    'eng', 'ong', 'uan', 'uang', 'ian', 'iang', 'ua',
    'ie', 'uo', 'iong', 'iao', 'un', 've', 'er', 'ia',
    'ue', 'uai'
]

l_tone = ['0', '1', '2', '3', '4']


def check(l):
    for i in l:
        if len(i) < 2: print(i)
        tone = i[-1]
        i = i[:-1]

        if i[0] in ['a', 'o', 'e', 'i', 'u', 'v']:
            initial = ''
            final = i
        elif i[0] in ['z', 'c', 's'] and i[1] == 'h':
            initial = i[:2]
            final = i[2:]
        else:
            initial = i[0]
            final = i[1:]
        if tone not in l_tone or initial not in l_initial or final not in l_final:
            raise ValueError(i)


def read(name, func, line=None):
    with open(name) as f:
        l = map(lambda i: i.strip(), f)
        l = [func(s) for i, s in enumerate(l) if s != '' and (line is None or i < line)]
        ret = defaultdict(set)
        for k, v in l:
            check(v)
            ret[k] |= v
    return ret


def f_builtin(l, p=compile(r'(.+): (.+)\n')):
    m = p.match(l)
    return m[1], m[2].split(', ')


def f_terra(l):
    match = {
        'r': 'ri',
        'eh': 'ei'
    }
    data = l.split('\t')[:2]
    pinyin, tone = data[1][:-1], data[1][-1]
    if tone == '5':
        tone = '0'

    return data[0], {match.get(pinyin, pinyin) + tone}


def f_data(l, p=compile(r'U\+([0-9a-fA-F]+): (.+) # .')):
    tones = {
        'ā': (1, 'a'), 'á': (2, 'a'), 'ǎ': (3, 'a'), 'à': (4, 'a'),
        'ē': (1, 'e'), 'é': (2, 'e'), 'ě': (3, 'e'), 'è': (4, 'e'),
        'ī': (1, 'i'), 'í': (2, 'i'), 'ǐ': (3, 'i'), 'ì': (4, 'i'),
        'ō': (1, 'o'), 'ó': (2, 'o'), 'ǒ': (3, 'o'), 'ò': (4, 'o'),
        'ū': (1, 'u'), 'ú': (2, 'u'), 'ǔ': (3, 'u'), 'ù': (4, 'u'),
        'ǘ': (2, 'v'), 'ǚ': (3, 'v'), 'ǜ': (4, 'v'),
        'ü': (0, 'v'), 'ḿ': (3, 'm'), 'ń': (2, 'n'), 'ň': (3, 'n'), 'ǹ': (4, 'n')
    }
    replace = {'ng': 'eng', 'n': 'en', 'm': 'en', 'hng': 'heng', 'hm': 'hen'}

    def convert(string):
        string = string.strip()
        tone = [tones.get(i, None) for i in string]  # try recognize tone
        string = [i if j is None else j[1] for i, j in zip(string, tone)]  # replace letter with tone
        string = ''.join(string)  # convert list to string
        tone = [i for i in tone if i is not None]  # filter nones in tone
        tone = str(tone[0][0]) if len(tone) != 0 else '0'  # add tone number
        if string == 'm̀':
            return 'en4'
        elif string in replace:
            return replace[string] + tone
        else:
            return string + tone

    m = p.match(l)
    i = int(m[1], 16)
    s = m[2].split(',')
    s = [convert(i) for i in s]
    return chr(i), {i for i in s if i is not None}


if __name__ == '__main__':
    data = defaultdict(set)
    # builtin = read('pinyin.txt', f_builtin)
    terra = read('terra.yaml', f_terra, 47112)
    xdhycd = read('xdhycd.txt', f_data)
    for i in [terra, xdhycd]:
        for k, v in i.items():
            if ord(k) < 0x9FFF:
                data[k] |= v
    with open('pinyin.txt', 'w') as f:
        for i in sorted(data):
            s = '{}: '.format(i)
            for i in data[i]:
                s += '{}, '.format(i)
            f.write(s[:-2] + '\n')
