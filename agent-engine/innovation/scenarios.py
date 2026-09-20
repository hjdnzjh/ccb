"""A seeded physical trajectory; observations contain no truth labels."""
import math
import random
from datetime import datetime, timedelta, timezone

TZ = timezone(timedelta(hours=8))
START = datetime(2026, 1, 1, tzinfo=TZ)
SCENARIOS = ('normal_peaks','night_business','small_flow','high_flow','communication',
             'quality','temporary_increase','repair_outcomes')


def validate_lab_database(name):
    if name != 'water_meter_innovation_lab':
        raise ValueError('Lab refuses the business database')
    return name


class Trajectory:
    def __init__(self, meter_id, seed, days=84):
        if not 1 <= days <= 84:
            raise ValueError('days must be between 1 and 84')
        self.meter_id, self.seed, self.days = meter_id, seed, days
        self.kind = 'residential' if meter_id % 5 < 3 else 'commercial' if meter_id % 5 == 3 else 'industrial'
        rng = random.Random(seed * 100003 + meter_id)
        self.events = []
        self.failures = []
        self.flow, self.totals = [], [100.0]
        # Distinct events vary independently of detector thresholds.
        for day in range(16, days, 4):
            scenario = SCENARIOS[(meter_id + day // 4) % len(SCENARIOS)]
            hour = rng.choice([1, 3, 10, 18])
            start = day*1440+hour*60
            duration = rng.choice([150, 240, 360])
            abnormal = scenario in ('small_flow','high_flow','communication','quality','repair_outcomes')
            event = dict(id=f'{meter_id}-{day}', meterId=meter_id,start=start,end=min(days*1440,start+duration),kind=scenario,
                         abnormal=abnormal,strength=rng.uniform(.10,.32),outcome=('restored','persistent','missing')[day//4%3])
            if scenario=='repair_outcomes':
                event['repairAt']=event['end']
                event['observationEnd']=min(days*1440,event['end']+24*60)
                if event['outcome']=='persistent':event['end']=event['observationEnd']
            self.events.append(event)
        total = 100.0
        for minute in range(days*1440+1):
            hour = minute % 1440 / 60
            if self.kind == 'residential':
                base=.018+.065*math.exp(-((hour-7.5)/1.3)**2)+.09*math.exp(-((hour-19)/1.8)**2)
            elif self.kind == 'commercial':
                base=.12+.38*(10<=hour<=23)
            else:
                base=.8+.15*math.sin(hour/24*2*math.pi)
            flow=max(0,base*(1+rng.uniform(-.18,.18)))
            for event in self.events:
                if event['start']<=minute<event['end']:
                    if event['kind'] in ('small_flow','repair_outcomes'):flow+=event['strength']
                    elif event['kind']=='high_flow':flow+=12
                    elif event['kind']=='temporary_increase':flow+=.13 if minute-event['start']<30 else 0
            self.flow.append(flow)
            if minute<days*1440:
                total+=flow/60
                self.totals.append(total)

    def observe(self, minute):
        minute=int(minute)
        if not 0<=minute<=self.days*1440:raise ValueError('outside trajectory')
        event=next((e for e in self.events if e['start']<=minute<e['end']),None)
        observed_total=self.totals[minute]-(2 if event and event['kind']=='quality' else 0)
        return dict(id=minute, reportedAt=(START+timedelta(minutes=minute)).isoformat(),
                    flow=round(self.flow[minute],3),total=round(observed_total,2),temperature=25,
                    valve=1,alarm=1 if event and event['kind']=='high_flow' and self.meter_id%2 else 0)

    def sample(self, step=15):
        if step not in (5,15,30,60):raise ValueError('unsupported sampling interval')
        return [self.observe(i) for i in range(0,self.days*1440+1,step)]

    def visible(self, minute):
        if any(e['kind']=='repair_outcomes' and e['outcome']=='missing' and e['repairAt']<=minute<e['observationEnd'] for e in self.events):return False
        event=next((e for e in self.events if e['start']<=minute<e['end']),None)
        return not (event and event['kind']=='communication')
