"""Offline, event-level comparison with time and meter holdouts. No business DB."""
import argparse
import json
import math
import sys
from collections import defaultdict, deque
from pathlib import Path
from statistics import median

ROOT=Path(__file__).resolve().parents[2]
DEPS=ROOT/'logs'/'innovation'/'deps'
if DEPS.exists():sys.path.insert(0,str(DEPS))
from .scenarios import Trajectory, START
from .features import group, FEATURE_KEYS, FEATURE_SCHEMA


def event_metrics(truth, alerts, meter_ids, start, end):
    truth=[e for e in truth if e.get('abnormal',True) and start<=e['start']<end]
    alerts=sorted((a for a in alerts if start<=a['time']<end),key=lambda a:(a['meterId'],a['time']))
    matched=set();false=[];delays=[];last_false={}
    for alert in alerts:
        match=next((e for e in truth if e['meterId']==alert['meterId'] and e['start']<=alert['time']<=e['end']),None)
        if match:
            if match['id'] not in matched:delays.append(alert['time']-match['start'])
            matched.add(match['id'])
        elif alert['time']-last_false.get(alert['meterId'],-1e9)>=360:
            false.append(alert);last_false[alert['meterId']]=alert['time']
    days=range(start//1440,math.ceil(end/1440))
    abnormal_days={(e['meterId'],day) for e in truth for day in range(e['start']//1440,(e['end']-1)//1440+1)}
    normal_days={(m,d) for m in meter_ids for d in days}-abnormal_days
    false_days={(a['meterId'],a['time']//1440) for a in false}&normal_days
    def quantile(values,q):
        if not values:return None
        values=sorted(values);return values[min(len(values)-1,int((len(values)-1)*q))]
    return dict(tp=len(matched),fp=len(false),fn=len(truth)-len(matched),recall=len(matched)/len(truth) if truth else None,
                precision=len(matched)/(len(matched)+len(false)) if matched or false else None,
                normalMeterDays=len(normal_days),falsePositiveMeterDayRate=len(false_days)/len(normal_days) if normal_days else None,
                falseEventsPer100MeterDays=100*len(false)/len(normal_days) if normal_days else None,
                medianDelayMinutes=quantile(delays,.5),p95DelayMinutes=quantile(delays,.95),
                missedEventIds=[e['id'] for e in truth if e['id'] not in matched])


def run(seed=20260920,meters=60,days=84,progress=None):
    if isinstance(seed,bool) or not isinstance(seed,int) or not 0<=seed<=2147483647:raise ValueError('seed out of range')
    if type(meters) is not int or type(days) is not int or not 1<=meters<=60 or not 1<=days<=84:raise ValueError('支持1至60块表、1至84天')
    import numpy as np
    vectors=[];meta=[];truth=[];quality_alerts=[];abstained=defaultdict(int)
    for meter in range(meters):
        trajectory=Trajectory(meter,seed,days);truth.extend(trajectory.events)
        samples=trajectory.sample();history=defaultdict(deque);pending=deque();valid_days=set();valid_intervals=0
        previous=None;consecutive=0;old_rate=None
        for i,raw in enumerate(samples):
            minute=i*15
            if progress and i%96==0:progress((meter+i/max(1,len(samples)))/meters*.65,f'生成并校验水表 {meter+1}/{meters}，第 {i//96+1} 天')
            if not trajectory.visible(minute):abstained[meter]+=1;continue
            if previous is not None and raw['total']<previous['total']:
                quality_alerts.append(dict(meterId=meter,time=minute));abstained[meter]+=1;continue
            at=START+__import__('datetime').timedelta(minutes=minute)
            if previous is None:previous=dict(raw,minute=minute);continue
            gap=minute-previous['minute'];rate=(raw['total']-previous['total'])*60/gap
            valid=gap==15;previous=dict(raw,minute=minute)
            while pending and pending[0][0]<=minute-45:
                t,value,grp=pending.popleft();history[grp].append((t,value));valid_days.add(t//1440);valid_intervals+=1
            grp=group(at);bucket=history[grp]
            while bucket and bucket[0][0]<minute-28*1440:bucket.popleft()
            values=[v for _,v in bucket];center=median(values) if values else 0;mad=median([abs(v-center) for v in values]) if values else 0
            ready=valid and len(valid_days)>=14 and valid_intervals>=200 and len(values)>=8
            high=ready and rate>center+max(.05,6*1.4826*mad)
            consecutive=consecutive+1 if high else 0
            alarm=raw['alarm'] or raw['flow']>10 or (raw['valve']==0 and raw['flow']>0)
            baseline_alert=bool(alarm or consecutive>=3)
            fixed_alert=bool(alarm)
            # A is the existing 10 m3/h device rule, frozen before test scoring.
            feature=[(rate-center)/max(.01,1.4826*mad),(raw['flow']-center)/max(.01,1.4826*mad),
                     (rate-old_rate)/max(.01,abs(old_rate)) if old_rate is not None else 0,min(consecutive,3),raw['alarm'],1-raw['valve'],1]
            if ready:
                vectors.append(feature);meta.append((meter,minute,fixed_alert,baseline_alert,alarm))
            else:
                abstained[meter]+=1
                if alarm:quality_alerts.append(dict(meterId=meter,time=minute))
            if valid:pending.append((minute,rate,grp));old_rate=rate
    if progress:progress(.68,'训练候选模型；验证集选阈值，测试集不参与调参')
    matrix=np.asarray(vectors,dtype=float);model=None;score=None;threshold=None;model_error=None
    # Hold out 20% within each type; never hold out an entire type by modulo.
    heldout=set()
    import hashlib
    for category in (lambda m:m%5<3,lambda m:m%5==3,lambda m:m%5==4):
        members=[m for m in range(meters) if category(m)]
        members.sort(key=lambda m:hashlib.sha256(f'{seed}:holdout:{m}'.encode()).hexdigest())
        heldout.update(members[:round(len(members)*.2)])
    develop=set(range(meters))-heldout
    train=[i for i,m in enumerate(meta) if m[0] in develop and m[1]<56*1440]
    validation=[i for i,m in enumerate(meta) if m[0] in develop and 56*1440<=m[1]<70*1440]
    try:
        from .model import CandidateForest
        if len(train)<256 or len(validation)<100:raise ValueError('insufficient chronological training/validation data')
        model=CandidateForest(seed=seed,version=f'isolation-shadow-v2-{seed}')
        selected=np.asarray(train)[::max(1,len(train)//20000)]
        model.fit(matrix[selected]);score=model.score(matrix)
        threshold=float(np.quantile(score[validation],.995))
        path=ROOT/'logs'/'innovation'/'models';path.mkdir(parents=True,exist_ok=True);model.save(path/f'{model.version}.joblib')
        (path/f'{model.version}.json').write_text(json.dumps(dict(seed=seed,schema=FEATURE_SCHEMA,featureKeys=FEATURE_KEYS,threshold=threshold,trainDays=[1,56],validationDays=[57,70],trainedWindows=len(selected),mode='shadow',dataVersion='trajectory-v2',heldOutMeters=sorted(heldout)),ensure_ascii=False,indent=2),encoding='utf8')
    except (ImportError,ValueError) as exc:model_error=str(exc)
    start=70*1440 if days>70 else max(0,(days-7)*1440);end=days*1440
    alerts={k:list(quality_alerts) for k in ('A','B','C')};c_high={};last_slot={};extra=defaultdict(int)
    for i,(meter,minute,a,b,hard) in enumerate(meta):
        if a:alerts['A'].append(dict(meterId=meter,time=minute))
        if b:alerts['B'].append(dict(meterId=meter,time=minute))
        candidate=score is not None and score[i]>threshold
        c_high[meter]=c_high.get(meter,0)+1 if candidate and minute-last_slot.get(meter,minute-15)==15 else int(candidate);last_slot[meter]=minute
        if b or hard or c_high[meter]>=3:alerts['C'].append(dict(meterId=meter,time=minute))
    summaries={k:event_metrics(truth,v,list(range(meters)),start,end) for k,v in alerts.items()}
    if score is None:summaries['C']=None
    by_kind={kind:{k:event_metrics([e for e in truth if e['kind']==kind],[a for a in alerts[k] if any(e['meterId']==a['meterId'] and e['start']<=a['time']<=e['end'] and e['kind']==kind for e in truth)],list(range(meters)),start,end) for k in ('A','B','C')} for kind in ('small_flow','high_flow','communication','quality','repair_outcomes')}
    tracks={name:{k:event_metrics([e for e in truth if e['meterId'] in group],[a for a in v if a['meterId'] in group],sorted(group),start,end) for k,v in alerts.items()} for name,group in [('knownMeters',develop),('heldOutMeters',heldout)]}
    for values in by_kind.values():
        for value in values.values():
            for key in ('fp','precision','normalMeterDays','falsePositiveMeterDayRate','falseEventsPer100MeterDays'):value.pop(key,None)
            value['sampleSize']=value['tp']+value['fn'];value['sufficientSamples']=value['sampleSize']>=30
    result=dict(schema='innovation-evaluation-v2',source='isolated-simulation',seed=seed,meters=meters,days=days,
        generatedObservations=meters*(days*96+1),testStartMinute=start,testEndMinute=end,summary=summaries,perKind=by_kind,tracks=tracks,
        modelVersion=model.version if score is not None else None,modelError=model_error,threshold=threshold,
        abstainedWindows=sum(abstained.values()),events=truth,
        heldOutMeters=sorted(heldout),limitations=['模拟数据结论不代表真实设备效果','固定阈值A采用现有10 m³/h设备规则，三组共享报文质量校验；分类型仅报告召回，不虚构可归属的误报',
            'C仅候选模型离线评估，在线仍为影子评分','本报告衡量检测，不将离线连续窗口当作真实主动复测请求；采集成本需结合复测任务日志验收',
            '每类别独立事件少于30时标注样本不足，不作效果达标结论'],
        acceptance='experimental_only',recommendedMode='off_until_validated')
    if progress:progress(1,'实验完成；请核验各类事件数量及失效场景')
    return result


def main():
    p=argparse.ArgumentParser();p.add_argument('--seed',type=int,default=20260920);p.add_argument('--meters',type=int,default=60);p.add_argument('--days',type=int,default=84);p.add_argument('--output',default='logs/innovation/evaluation.json');args=p.parse_args()
    path=(ROOT/args.output).resolve();allowed=(ROOT/'logs'/'innovation').resolve()
    if not path.is_relative_to(allowed):raise ValueError('Output must remain within project logs/innovation')
    report=run(args.seed,args.meters,args.days,lambda n,s: print(f'{n:.0%} {s}',flush=True) if n>.65 else None)
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps({'output':str(path),'summary':report['summary'],'modelError':report['modelError']},ensure_ascii=False))


if __name__=='__main__':main()
