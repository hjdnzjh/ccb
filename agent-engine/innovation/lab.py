"""Local experiment runner. Stores only isolated manifests/reports, never business SQL."""
import hmac
import json
import os
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from flask import Flask, request, jsonify
from .evaluate import run, ROOT

DIRECTORY=ROOT/'logs'/'innovation'/'lab-runs'


def create_lab(directory=None):
    directory=Path(directory) if directory else DIRECTORY
    directory.mkdir(parents=True,exist_ok=True)
    app=Flask(__name__);app.config['MAX_CONTENT_LENGTH']=65536
    jobs={};lock=threading.RLock()
    def path(key):
        if not isinstance(key,str) or len(key)!=32 or any(c not in '0123456789abcdef' for c in key):raise ValueError('Invalid run ID')
        return directory/(key+'.json')
    def save(job):
        with lock:
            destination=path(job['id']);temporary=destination.with_suffix('.tmp')
            temporary.write_text(json.dumps(job,ensure_ascii=False,indent=2),encoding='utf8');temporary.replace(destination)
    def get(key):
        with lock:
            if key in jobs:return jobs[key]
            record=json.loads(path(key).read_text(encoding='utf8'))
            if record['status'] in ('running','paused'):record['status']='interrupted';record['error']='服务重启中断了本次实验，可重新创建；原输入与结果已保留'
            return record
    @app.before_request
    def auth():
        if request.path=='/api/health':return None
        token=os.environ.get('AGENT_INTERNAL_TOKEN','')
        if not token or not hmac.compare_digest(token,request.headers.get('X-Agent-Token','')):return jsonify(message='禁止直接访问演练服务'),401
    @app.errorhandler(ValueError)
    def bad(error):return jsonify(message=str(error)),400
    @app.errorhandler(FileNotFoundError)
    def missing(error):return jsonify(message='演练记录不存在'),404
    @app.get('/api/health')
    def health():return jsonify(status='healthy',source='isolated-lab')
    @app.get('/api/lab/replays')
    def listing():
        records=[get(p.stem) for p in sorted(directory.glob('*.json'),key=lambda p:p.stat().st_mtime,reverse=True)[:30]]
        return jsonify(enabled=True,runs=[{k:v for k,v in r.items() if k not in ('report','events')} for r in records])
    def worker(job,event):
        try:
            def progress(n,description):
                event.wait()
                with lock:
                    job['progress']=n;job['message']=description
                    if job.pop('singleStep',False):event.clear();job['status']='paused'
                    save(job)
            result=run(**job['config'],progress=progress)
            with lock:
                job.update(status='completed',progress=1,summary=result['summary'],report=result,events=result['events'][:100],error=None);save(job)
        except Exception as exc:
            with lock:job.update(status='failed',error=str(exc)[:500]);save(job)
    @app.post('/api/lab/replays')
    def create():
        body=request.get_json(silent=True) or {};config={k:body.get(k,v) for k,v in {'seed':20260920,'meters':6,'days':21}.items()}
        if any(type(v) is not int for v in config.values()) or not 1<=config['meters']<=60 or not 1<=config['days']<=84 or not 0<=config['seed']<=2147483647:raise ValueError('支持1至60块表、1至84天及有效整数种子')
        with lock:
            if any(j['status'] in ('running','paused') for j in jobs.values()):return jsonify(message='已有实验正在运行或暂停，请先完成当前实验'),409
            key=uuid.uuid4().hex;event=threading.Event();event.set();job=dict(id=key,status='running',createdAt=datetime.now(timezone.utc).isoformat(),config=config,progress=0,summary=None,events=[],error=None)
            jobs[key]=job;controls[key]=event;save(job)
            threading.Thread(target=worker,args=(job,event),daemon=True).start()
        return jsonify(id=key)
    controls={}
    @app.get('/api/lab/replays/<key>')
    def detail(key):return jsonify({k:v for k,v in get(key).items() if k!='report'})
    @app.get('/api/lab/replays/<key>/report')
    def report(key):
        job=get(key)
        if job['status']!='completed':return jsonify(message='实验尚未完成，没有最终报告'),409
        return jsonify(job['report'])
    @app.post('/api/lab/replays/<key>/control')
    def control(key):
        with lock:
            job=get(key);action=(request.get_json(silent=True) or {}).get('action')
            if action not in ('pause','resume','step'):raise ValueError('未知控制动作')
            if key not in controls or job['status'] not in ('running','paused'):return jsonify(message='当前实验不可继续控制'),409
            event=controls[key]
            if action=='pause':event.clear();job['status']='paused'
            else:job['status']='running';job['singleStep']=action=='step';event.set()
            save(job);return jsonify(id=key,status=job['status'])
    return app


if __name__=='__main__':
    if len(os.environ.get('AGENT_INTERNAL_TOKEN',''))<40:raise SystemExit('AGENT_INTERNAL_TOKEN required')
    create_lab().run(host='127.0.0.1',port=8088,debug=False,use_reloader=False)
