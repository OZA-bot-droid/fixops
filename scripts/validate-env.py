#!/usr/bin/env python3
"""Local toolchain versions check. Stdlib only, no external calls.
Usage: python scripts/validate-env.py [--root /path/to/repo] [--strict]
"""
import argparse
from pathlib import Path
import re, shutil, subprocess, sys
p = argparse.ArgumentParser()
p.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
p.add_argument('--strict', action='store_true')
a=p.parse_args()
root=a.root.resolve()
cfg={}
for line in (root/'versions.env').read_text(encoding='utf-8').splitlines():
    if line and not line.lstrip().startswith('#') and '=' in line:
        k,v=line.split('=',1);cfg[k]=v.strip()
fail=[]
def cmd(args,pattern=None):
    if not shutil.which(args[0]):
        return None
    try:
        p=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=15,check=False)
        return p.stdout.strip() if p.returncode == 0 else None
    except (OSError,subprocess.TimeoutExpired):
        return None
def test(name,actual,expected,check):
    good=actual is not None and check(actual,expected)
    print(f"{'OK' if good else 'MISSING/MISMATCH'} {name}: {actual or '-'} (expected: {expected})")
    if not good: fail.append(name)
def contains(s,v): return v in s
def prefix(s,v): return s.lstrip('v').startswith(v)
test('Java',cmd(['java','-version']),cfg['JAVA_MAJOR'],lambda s,e:bool(re.search(r'(?:version |openjdk )"?'+re.escape(e)+r'(?:\.|"|\s)',s)))
test('Node',cmd(['node','--version']),cfg['NODE_VERSION'],prefix)
test('npm',cmd(['npm','--version']),cfg['NPM_VERSION'],lambda s,e:s==e)
test('Python',cmd(['python','--version']) or cmd(['python3','--version']),cfg['PYTHON_VERSION'],contains)
test('Docker',cmd(['docker','--version']),'Docker installed',lambda s,e:'Docker version' in s)
test('Compose',cmd(['docker','compose','version']),'Docker Compose v2',lambda s,e:'Docker Compose version v2' in s or 'Docker Compose version 2' in s)
for label,entry in [('Frontend lock','frontend/package-lock.json'),('AI lock','ai-service/uv.lock'),('MCP lock','mcp-server/uv.lock')]:
    exists=(root/entry).is_file()
    print(f"{'OK' if exists else 'PENDING'} {label}: {entry}")
    if not exists and a.strict:fail.append(label)
for service in ('gateway','member-service','asset-service','workorder-service','inventory-service'):
    wrapper=root/service/'gradlew'
    jar=root/service/'gradle/wrapper/gradle-wrapper.jar'
    exists=wrapper.is_file() and jar.is_file()
    print(f"{'OK' if exists else 'PENDING'} {service} Gradle wrapper")
    if not exists and a.strict:fail.append(service+' wrapper')
print('Result:', 'PASS' if not fail else 'FAIL: '+', '.join(fail))
sys.exit(1 if fail else 0)
