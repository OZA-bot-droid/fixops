from pathlib import Path
base=Path(__file__).resolve().parents[1]
required=['frontend/package.json','gateway/build.gradle','member-service/build.gradle','asset-service/build.gradle','workorder-service/build.gradle','inventory-service/build.gradle','ai-service/app/main.py','mcp-server/app/server.py','contracts/proto/fixops/schedule/v1/schedule.proto']
missing=[p for p in required if not (base/p).is_file()]
print('Scaffold structure:', 'OK' if not missing else 'MISSING '+', '.join(missing))
raise SystemExit(1 if missing else 0)
