"""Publish reproducible local evaluation artifacts into the read-only lab history."""
import hashlib
import json
import platform
import uuid
from datetime import datetime,timezone
from pathlib import Path

root=Path(__file__).resolve().parents[1]
folder=root/'logs'/'innovation'
runs=folder/'lab-runs';runs.mkdir(exist_ok=True)
summaries=[]
for seed in (20260920,20260921,20260922):
    file=folder/f'evaluation-v2-{seed}.json'
    report=json.loads(file.read_text(encoding='utf8'))
    key=uuid.uuid5(uuid.NAMESPACE_URL,f'water-innovation-v2:{seed}').hex
    model=folder/'models'/(report['modelVersion']+'.joblib')
    summaries.append(dict(seed=seed,summary=report['summary'],model=report['modelVersion'],modelSha256=hashlib.sha256(model.read_bytes()).hexdigest(),reportSha256=hashlib.sha256(file.read_bytes()).hexdigest()))
    (runs/(key+'.json')).write_text(json.dumps(dict(id=key,status='completed',createdAt=datetime.fromtimestamp(file.stat().st_mtime,timezone.utc).isoformat(),config=dict(seed=seed,meters=60,days=84),progress=1,message='完整离线对照实验；未通过上线效果门槛',summary=report['summary'],events=report['events'][:100],report=report,error=None),ensure_ascii=False,indent=2),encoding='utf8')
out=root/'output'/'innovation';out.mkdir(exist_ok=True,parents=True)
(out/'evaluation-summary.json').write_text(json.dumps(dict(schema='innovation-summary-v2',platform=platform.platform(),runs=summaries,acceptance='not_passed',recommendation='off_until_validated'),ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps([{'seed':r['seed'],'groups':{k:{q:v[q] for q in ('tp','fp','fn','recall')} for k,v in r['summary'].items()}} for r in summaries],ensure_ascii=False))
