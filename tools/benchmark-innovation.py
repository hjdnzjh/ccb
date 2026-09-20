"""Measure local detector throughput; results are not device capacity claims."""
import json
import os
import platform
import sys
import time
from datetime import datetime,timedelta,timezone
from pathlib import Path
from statistics import median

root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'agent-engine'))
sys.path.insert(0,str(root/'logs'/'innovation'/'deps'))
from innovation.model import diagnose,CandidateForest
from innovation.features import feature_vector

start=datetime(2026,1,1,tzinfo=timezone(timedelta(hours=8)))
rows=[dict(id=i,reportedAt=(start+timedelta(minutes=15*i)).isoformat(),flow=.02,total=100+i*.005,temperature=25,valve=1,alarm=0) for i in range(16*96+1)]
diagnose(dict(meterId=1,observations=rows))
forest=CandidateForest.load(root/'logs'/'innovation'/'models'/'isolation-shadow-v2-20260920.joblib')
durations=[]
for batch in range(5):
    begin=time.perf_counter()
    vectors=[feature_vector(diagnose(dict(meterId=meter,observations=rows))['features']) for meter in range(100)]
    assert len(forest.score(vectors))==100
    durations.append(time.perf_counter()-begin)
result=dict(platform=platform.platform(),python=sys.version,cpu=platform.processor(),logicalProcessors=os.cpu_count(),measurement='five sequential batches: 100 full-history feature/baseline calls plus one real forest batch score; excludes HTTP and database',historyRows=len(rows),batchSeconds=durations,p95BatchSeconds=sorted(durations)[-1],targetSeconds=2,targetMet=max(durations)<=2)
destination=root/'logs'/'innovation'/'performance-python.json';destination.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps(result,ensure_ascii=False))
