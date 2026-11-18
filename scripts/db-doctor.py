#!/usr/bin/env python3
"""Check configured file consistency, and optionally remote TCP reachability (no credentials)."""
import argparse, pathlib, socket, sys
ROOT = pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--mode',choices=['local','integration'],required=True)
p.add_argument('--host',default=None)
p.add_argument('--skip-network',action='store_true')
a=p.parse_args()
compose=ROOT/f'compose.db.{a.mode}.yml'
boot=ROOT/'infra/postgres/bootstrap/01-bootstrap.sh'
for f in (compose,boot,ROOT/'.env.db.example'):
    if not f.is_file(): print('MISSING',f);sys.exit(2)
s=compose.read_text()
for x in ('pgvector/pgvector:0.8.2-pg16-bookworm','5432:5432','fixops_'+a.mode+'_pgdata','./infra/postgres/bootstrap'):
    if x not in s: print('MISSING SETTING',x);sys.exit(2)
print('OK: compose, database image/version, isolated volume and bootstrap files')
if not a.skip_network:
    host=a.host or ('127.0.0.1' if a.mode=='local' else '192.168.0.84')
    try:
        with socket.create_connection((host,5432),timeout=3):print('OK: TCP connection',host,5432)
    except OSError as e:
        print('UNREACHABLE:',host,5432,str(e));sys.exit(1)
