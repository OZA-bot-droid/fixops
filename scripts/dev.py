#!/usr/bin/env python3
"""FixOps local environment bootstrap/runner. Python >=3.12 standard library only.
Usage: python scripts/dev.py init|doctor|db-up|db-down|db-check|env <service>|run <service>|frontend|python <ai|mcp> [--integration]
"""
from __future__ import annotations
import argparse, os, pathlib, re, secrets, shutil, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
DBS={'member':'member_db','asset':'asset_db','workorder':'workorder_db','inventory':'inventory_db','ai':'ai_db'}
ROLES={n:f'{n}_app' for n in DBS}
SERVICES={'gateway':'gateway','member':'member-service','asset':'asset-service','workorder':'workorder-service','inventory':'inventory-service'}
CRED=ROOT/'.env'
KEYS=ROOT/'local-secrets'/'jwt'


def die(message):raise SystemExit('ERROR: '+message)
def load_env(path):
    if not path.is_file():die('설정 파일이 없습니다: '+str(path)+' (먼저 init)')
    out={}
    for line in path.read_text(encoding='utf-8').splitlines():
        s=line.strip()
        if not s or s.startswith('#'):continue
        k,sep,v=s.partition('=')
        if not sep:die('잘못된 .env 항목: '+k)
        out[k.strip()]=v.strip().strip('"').strip("'")
    return out

def init():
    if CRED.exists():
        print('.env 유지: 기존 DB 비밀번호를 변경하지 않습니다')
    else:
        data={
            'POSTGRES_PASSWORD':secrets.token_hex(24),
            'REDIS_PASSWORD':secrets.token_hex(24),
            }
        for n in DBS:data[n.upper()+'_DB_PASSWORD']=secrets.token_hex(24)
        template=(ROOT/'.env.example').read_text(encoding='utf-8')
        for k,v in data.items(): template=re.sub(rf'(?m)^{k}=.*$',f'{k}={v}',template)
        CRED.write_text(template,encoding='utf-8')
        try:CRED.chmod(0o600)
        except OSError:pass
        print('개발자 전용 .env 생성')
    KEYS.mkdir(parents=True,exist_ok=True)
    priv,pub=KEYS/'private_key.pem',KEYS/'public_key.pem'
    if priv.exists() and pub.exists():print('JWT 키 유지')
    elif priv.exists()!=pub.exists():die('JWT 개인키/공개키 일부만 존재합니다. 수동 점검 필요')
    else:
        if not shutil.which('java'): die('Java 21 required to generate JWT keys')
        subprocess.run(['java',str(ROOT/'scripts/GenerateJwtKeys.java'),str(KEYS)],check=True,cwd=ROOT)


def env_for(service,integrate=False):
    src=CRED
    if integrate and not src.exists():die('팀장 공유 DB에 맞는 별도 .env를 준비하세요')
    vals=load_env(src)
    host=os.environ.get('INTEGRATION_DB_HOST', '192.168.0.84') if integrate else '127.0.0.1'
    env=dict(os.environ)
    env['SPRING_PROFILES_ACTIVE']='local'
    env['APP_ENV']='integration' if integrate else 'local'
    env['JWT_PRIVATE_KEY_PATH']=str(KEYS/'private_key.pem')
    env['JWT_PUBLIC_KEY_PATH']=str(KEYS/'public_key.pem')
    # Redis configuration for local Java services
    if service in ('member', 'gateway'):
        redis_password = vals.get('REDIS_PASSWORD', '')
        if (not redis_password or redis_password.startswith('REPLACE_')):
            die('REDIS_PASSWORD를 .env에 설정하세요')
        redis_host = (os.environ.get('INTEGRATION_REDIS_HOST',host)
            if integrate else vals.get('REDIS_HOST','127.0.0.1'))
        env.update(REDIS_HOST=redis_host, REDIS_PORT=vals.get('REDIS_PORT','6379'),
            REDIS_PASSWORD=redis_password,
        )
    if service in DBS:
        name=DBS[service]
        env.update(DB_URL=f'jdbc:postgresql://{host}:5432/{name}',DB_USER=ROLES[service],DB_PASSWORD=vals[service.upper()+'_DB_PASSWORD'])
        env['DATABASE_URL']=f'postgresql://{ROLES[service]}:{vals[service.upper()+"_DB_PASSWORD"]}@{host}:5432/{name}'
    for name in ('member','asset','workorder','inventory','ai'):
        env[name.upper()+'_SERVICE_URL']=f'http://127.0.0.1:{dict(member=8081,asset=8082,workorder=8083,inventory=8084,ai=8085)[name]}'
    env['WORKORDER_GRPC_TARGET']='127.0.0.1:9093'
    env['MCP_SERVER_URL']='http://127.0.0.1:8090/mcp'
    for k in ('DEMO_BUILDING_APPROVAL_BYPASS','DEMO_EMAIL_VERIFICATION_BYPASS','PLATFORM_ADMIN_EMAIL','PLATFORM_ADMIN_PASSWORD'):
        env[k]=vals.get(k,'false' if k.startswith('DEMO_') else '')
    env.setdefault('DEMO_BUILDING_APPROVAL_BYPASS','false')
    env.setdefault('DEMO_EMAIL_VERIFICATION_BYPASS','false')
    return env

def run(command,env=None,cwd=None):
    print('실행:', ' '.join(map(str,command)))
    raise SystemExit(subprocess.run(command,cwd=cwd,env=env).returncode)

def doctor():
    required=['java','node','npm','docker','uv']
    failed=[]
    for name in required:
        if shutil.which(name):print('OK',name,shutil.which(name))
        else:print('MISSING',name);failed.append(name)
    for path in [CRED,KEYS/'private_key.pem',KEYS/'public_key.pem']:
        print('OK' if path.is_file() else 'MISSING',path.relative_to(ROOT))
        if not path.is_file():failed.append(str(path))
    for name in SERVICES.values():
        directory=ROOT/name
        wrapper='gradlew.bat' if os.name=='nt' else 'gradlew'
        good=(directory/wrapper).is_file() and (directory/'gradle/wrapper/gradle-wrapper.jar').is_file()
        print('OK' if good else 'PENDING',name,'Gradle Wrapper (or install Gradle 8.14.3)')
        if not good and not shutil.which('gradle'):failed.append(name+' gradle')
    for name in ['frontend/package-lock.json','ai-service/uv.lock','mcp-server/uv.lock']:
        print('OK' if (ROOT/name).is_file() else 'PENDING',name)
    if failed:print('필수 준비 누락:',', '.join(failed));return 1
    return 0

def db_action(action,integrate=False):
    if integrate:die('안전 조치: 통합 DB 기동은 팀장 PC에서 명시적 Compose 명령으로만 수행합니다')
    source=load_env(CRED)
    if any(v.startswith('REPLACE_') for v in source.values()):die('비밀번호를 교체하세요')
    command=['docker','compose','--env-file',str(CRED),'-f',str(ROOT/'compose.yml')]
    if action=='up':command+=['up','-d']
    elif action=='down':command+=['down']
    else:command+=['ps']
    run(command,cwd=ROOT)

def check_db():
    env=load_env(CRED)
    c=['docker','compose','--env-file',str(CRED),'-f',str(ROOT/'compose.yml'),'exec','-T','postgres','psql','-U','postgres','-d','postgres','-Atc',"SELECT datname FROM pg_database WHERE datname IN ('member_db','asset_db','workorder_db','inventory_db','ai_db') ORDER BY 1"]
    p=subprocess.run(c,cwd=ROOT,capture_output=True,text=True)
    if p.returncode:die(p.stderr.strip())
    got=set(p.stdout.splitlines())
    if got!=set(DBS.values()):die(f'DB 누락: {set(DBS.values())-got}')
    print('OK: 5개 서비스 DB 확인')
    c=c[:];c[-3]='ai_db';c[-1]="SELECT extversion FROM pg_extension WHERE extname='vector'"
    p=subprocess.run(c,cwd=ROOT,capture_output=True,text=True)
    if p.returncode or p.stdout.strip()!='0.8.2':die('ai_db vector 0.8.2 검증 실패: '+p.stdout+' '+p.stderr)
    print('OK: ai_db vector 0.8.2')

def print_env(service,integ):
    env=env_for(service,integ)
    names=['APP_ENV','SPRING_PROFILES_ACTIVE','DB_URL','DB_USER','JWT_PUBLIC_KEY_PATH','WORKORDER_GRPC_TARGET','REDIS_HOST','REDIS_PORT',]
    for name in names:
        if name in env:print(name+'='+env[name])
    if service in ('member','gateway'):
        print('REDIS_PASSWORD='+('CONFIGURED' if env.get('REDIS_PASSWORD') else 'MISSING'))
    print('DB_PASSWORD / DATABASE_URL / JWT 개인키 값은 화면에 표시하지 않습니다')

def launch_service(service,integ):
    if service not in SERVICES:die('지원 서비스: '+', '.join(SERVICES))
    env=env_for(service,integ)
    folder=ROOT/SERVICES[service]
    wrapper=folder/('gradlew.bat' if os.name=='nt' else 'gradlew')
    if (folder/'gradle/wrapper/gradle-wrapper.jar').is_file() and wrapper.is_file():
        command=[str(wrapper),'bootRun','--no-daemon']
    elif shutil.which('gradle'):
        print('WARN: Wrapper 없음. 설치된 Gradle 버전이 8.14.3인지 직접 확인하십시오')
        command=['gradle','bootRun','--no-daemon']
    else:die('Gradle Wrapper 또는 Gradle 8.14.3을 먼저 준비하세요')
    run(command,env=env,cwd=folder)

def launch_python(service,integ):
    if service not in ('ai','mcp'):die('python ai|mcp만 지원')
    folder=ROOT/('ai-service' if service=='ai' else 'mcp-server')
    if not (folder/'uv.lock').is_file():die(f'{folder.name}/uv.lock 필요. uv lock 후 커밋하세요')
    env=env_for(service,integ)
    if service=='ai':run(['uv','run','--frozen','uvicorn','app.main:app','--reload','--port','8085'],env=env,cwd=folder)
    else:die('MCP 서버는 현재 Placeholder입니다. 실제 MCP SDK 및 진입점 확정 후 실행 명령을 등록하세요')

def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['init','doctor','db-up','db-down','db-check','env','run','frontend','python']);p.add_argument('service',nargs='?');p.add_argument('--integration',action='store_true');a=p.parse_args()
    if a.action=='init':init()
    elif a.action=='doctor':sys.exit(doctor())
    elif a.action=='db-up':db_action('up',a.integration)
    elif a.action=='db-down':db_action('down',a.integration)
    elif a.action=='db-check':check_db()
    elif a.action=='env':print_env(a.service or 'member',a.integration)
    elif a.action=='run':launch_service(a.service or 'member',a.integration)
    elif a.action=='frontend':
        if not (ROOT/'frontend/package-lock.json').is_file():die('frontend/package-lock.json 필요. npm install --package-lock-only 실행 후 커밋')
        run(['npm','run','dev'],cwd=ROOT/'frontend')
    elif a.action=='python':launch_python(a.service or 'ai',a.integration)
if __name__=='__main__':main()
