"""Freeze actual local verification results; refuse failed or incomplete logs."""
import json
import re
from datetime import datetime,timezone
from hashlib import sha256
from pathlib import Path

root=Path(__file__).resolve().parents[1]
def log(name):return (root/'logs'/name).read_text(encoding='utf-8-sig')
java=log('innovation-final-package.log');python=log('innovation-all-python-tests.log');frontend=log('innovation-final-frontend-tests.log');build=log('innovation-final-frontend-build.log');browser=log('innovation-browser.log')
assert 'BUILD SUCCESS' in java and 'BUILD FAILURE' not in java
result=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',java)[-1]
assert tuple(map(int,result[1:]))==(0,0,0)
assert re.search(r'^OK\s*$',python,re.M) and '# fail 0' in frontend and 'built in' in build
assert browser.count('PASS ')==3 and 'CLEANUP' in browser and 'Error:' not in browser
original=root/'logs/innovation/evaluation-v2-20260920.json';repeat=root/'logs/innovation/evaluation-v2-repeat.json'
assert original.read_bytes()==repeat.read_bytes()
data=dict(verifiedAt=datetime.now(timezone.utc).isoformat(),javaTests=int(result[0]),pythonTests=int(re.search(r'Ran (\d+) tests',python).group(1)),frontendTests=int(re.search(r'# tests (\d+)',frontend).group(1)),backendPackage='passed',frontendBuild='passed_with_existing_bundle_and_sass_warnings',browserFlows='passed',policy='off',modelAcceptance='not_passed',repeatReportSha256=sha256(original.read_bytes()).hexdigest(),hardware=dict(cpu='AMD Ryzen 7 8845H',logicalProcessors=16,physicalMemoryBytes=33601986560),performance={name:json.loads((root/f'logs/innovation/performance-{name}.json').read_text(encoding='utf8')) for name in ('java','python')})
destination=root/'output/innovation/verification-summary.json';destination.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in data.items() if k not in ('performance','hardware')},ensure_ascii=False))
