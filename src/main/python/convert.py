from collections import defaultdict
from re import compile

pattern_line_original = compile(r'(.+): (.+)\n')
pattern_line_terra = compile(r'(\S+)\s(\S+)(\s.+)?')

initial_list = [
    'b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k',
    'h', 'z', 'c', 's', 'zh', 'ch', 'sh', 'j', 'q',
    'x', 'y', 'w', 'r', ''
]

final_list = [
    'a', 'o', 'e', 'i', 'u', 'v', 'ai', 'ei', 'ui',
    'ao', 'ou', 'iu', 'an', 'ang', 'in', 'ing', 'en',
    'eng', 'ong', 'uan', 'uang', 'ian', 'iang', 'ua',
    'ie', 'uo', 'iong', 'iao', 'un', 've', 'er', 'ia',
    'ue', 'uai',
]


def check_valid(s):
    tone = s[-1]
    s = s[:-1]
    if s[0] in ['a', 'o', 'e', 'i', 'u', 'v']:
        initial = ''
        final = s
    elif s[0] in ['z', 'c', 's'] and s[1] == 'h':
        initial = s[:2]
        final = s[2:]
    else:
        initial = s[0]
        final = s[1:]
    return tone in ['0', '1', '2', '3', '4'] and \
           initial in initial_list and final in final_list


def read_original():
    with open('pinyin.txt') as f:
        l = [original_analyze_line(i) for i in f]
    return {int(i[0], 16): i[1] for i in l}


def original_analyze_line(line):
    m = pattern_line_original.match(line)
    if m is None:
        raise ValueError
    k = m[1]
    v = m[2].split(', ')
    for i in v:
        if not check_valid(i):
            raise ValueError('Unrecognized pinyin: ' + i)
    return k, set(v)


def read_terra():
    with open('terra.txt') as f:
        l = [terra_analyze_line(i) for i in f]
        ret = defaultdict(set)
        for k, v in l:
            ret[k].add(v)
    return ret


def terra_analyze_line(l):
    m = pattern_line_terra.match(l)
    s = m[2]
    assert m
    if s[-1] == '5':
        s = s[:-1] + '0'
    if not check_valid(s):
        raise ValueError('Unrecognized: ' + m[1] + '->' + s)
    return ord(m[1]), s


def write(d):
    with open('output.txt', 'w') as f:
        for k, v in d.items():
            f.write(write_line(k, v))


def write_line(k, v):
    s = chr(k)
    s += ': '
    for i in v:
        s += i + ', '
    return s[:-2] + '\n'


if __name__ == '__main__':
    ret = defaultdict(set)

    for k, v in read_terra().items():
        if k < 0x9FFF:
            ret[k] |= v
    # for k, v in read_original().items():
    #     if k < 0x9FFF:
    #         ret[k] |= v
    print(sorted(ret.items(), key=lambda i: i[0]))
    write(ret)
