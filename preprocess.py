import os
import re

nouns = set()
# from WordNet
with open('index.noun', 'r') as f:
    for line in f:
        nouns.add(line.split()[0])

# TODO make sure words are common enough

try:
    os.remove('challenge-model')
except OSError:
    pass

with open('challenge-model', 'w') as model:
    with open('cmudict-en-us.dict', 'r') as d:
        for line in d:
            parts = line.split()
            word = parts[0]
            if re.match(r'[a-z]+', word) and word in nouns and len(parts) > 8:
                model.write(word + '\n')

    for command in ['skip', 'pause', 'resume']:
        model.write(command + '\n')
