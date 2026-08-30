import os, sys, re, shutil

# Refreshed 2026-08-30 for the upstream jdk.incubator.json module layout (issue #154).
# Scope: impl files fetched into the snapshot dir (see RefreshFromUpstream.java).
# Public API files follow the manual process in json-java21/AGENTS.md.
SRC = 'updates/2025-09-04/upstream/jdk.incubator.json.impl'
DST = 'json-java21/src/main/java/jdk/incubator/internal/util/json'

def read(path):
    f = open(path, 'r')
    try:
        return f.read()
    finally:
        f.close()

def write_safe(path, text):
    tmp = path + '.tmp'
    f = open(tmp, 'w')
    try:
        f.write(text)
    finally:
        f.close()
    if os.path.getsize(tmp) == 0:
        sys.stderr.write('Refusing to overwrite 0-byte: '+path+'\n')
        os.remove(tmp)
        return False
    if os.path.exists(path):
        os.remove(path)
    os.rename(tmp, path)
    return True

def transform(text, name):
    # package: upstream impl package -> our internal package
    text = re.sub(r'^package\s+jdk\.incubator\.json\.impl;', 'package jdk.incubator.internal.util.json;', text, flags=re.M)
    # imports: impl-internal first (defensive; upstream impl rarely imports itself), then public API
    text = re.sub(r'^(\s*import\s+)jdk\.incubator\.json\.impl\.', r'\1jdk.incubator.internal.util.json.', text, flags=re.M)
    text = re.sub(r'^(\s*import\s+)jdk\.incubator\.json\.', r'\1jdk.incubator.java.util.json.', text, flags=re.M)
    # annotations (single-line)
    text = re.sub(r'^\s*@(?:jdk\.internal\..*|ValueBased|StableValue).*\n', '', text, flags=re.M)
    # remove import of ValueBased if present
    text = re.sub(r'^\s*import\s+jdk\.internal\.ValueBased;\s*\n', '', text, flags=re.M)
    # remove JsonValueImpl from implements if present
    text = re.sub(r'\bimplements\s+JsonValueImpl\s*,\s*', 'implements ', text)
    text = re.sub(r'\bimplements\s+([^\{\n]*)\bJsonValueImpl\s*(,\s*)', lambda m: 'implements '+m.group(1), text)
    text = re.sub(r'\bimplements\s+JsonValueImpl\b\s*', '', text)
    # remove stray imports of JsonValueImpl
    text = re.sub(r'^\s*import\s+.*JsonValueImpl;\s*\n', '', text, flags=re.M)
    # Java 22+ patterns: unnamed variables '_' → name them
    text = re.sub(r'catch\s*\(([^\)]*)_\)', r'catch(\1e)', text)
    text = re.sub(r'case\s+([A-Za-z0-9_$.<>\[\]]+)\s+_\s*->', r'case \1 v ->', text)
    return text

def main():
    if not os.path.isdir(SRC):
        sys.stderr.write('Missing SRC: '+SRC+'\n')
        sys.exit(1)
    if not os.path.isdir(DST):
        sys.stderr.write('Missing DST: '+DST+'\n')
        sys.exit(1)
    ok = True
    for name in os.listdir(SRC):
        if not name.endswith('.java'):
            continue
        src_path = os.path.join(SRC, name)
        dst_path = os.path.join(DST, name)
        data = read(src_path)
        out = transform(data, name)
        if not write_safe(dst_path, out):
            ok = False
    if not ok:
        sys.exit(2)
    print('Transform complete')
    print('Reminder: after transforming, re-append the Utils.powExact polyfill (Java 21 lacks Math.powExact) and keep LazyConstant.java untouched (local polyfill).')

if __name__ == '__main__':
    main()
