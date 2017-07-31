import json, os


d = os.getcwd()
d = os.path.dirname(d)
d = os.path.dirname(d)
d = os.path.dirname(d)


def input_id(f):
	i = input('Identifier:')
	o = read()
	f(o, i)
	write(o)
	print()


def read():
	with open(d + '/feed.json', 'r') as f:
		s = f.read()
		r = json.loads(s)
	
	return r


def write(o):
	with open(d + '/feed.json', 'w') as f:
		f.write(json.dumps(o, indent=2))


def add_string(o, s):
	v = o[0]
	assert v['version'] == 1
	v['string'].append(s)


def add_regexp(o, s):
	v = o[0]
	assert v['version'] == 1
	v['regexp'].append(s)


s = {
	1: lambda: input_id(add_string),
	2: lambda: input_id(add_regexp),
}

while True:
	print('1. Add string')
	print('2. Add regexp')
	print('0. Exit')
	print('')
	c = input('Choice:')
	i = int(c)
	if i == 0:
		break
	s.get(i, lambda: print('Command not found.'))()
	
