import json
import os
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from innovation.lab import create_lab


class LabTest(unittest.TestCase):
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory()
        self.env=patch.dict(os.environ,{'AGENT_INTERNAL_TOKEN':'t'*44});self.env.start()
        self.app=create_lab(self.folder.name);self.client=self.app.test_client()
        self.headers={'X-Agent-Token':'t'*44}

    def tearDown(self):
        self.env.stop();self.folder.cleanup()

    def test_access_validation_and_restart_are_explicit(self):
        self.assertEqual(self.client.get('/api/lab/replays').status_code,401)
        self.assertEqual(self.client.post('/api/lab/replays',json={'days':True},headers=self.headers).status_code,400)
        key='1'*32
        Path(self.folder.name,key+'.json').write_text(json.dumps({'id':key,'status':'running'}),encoding='utf8')
        result=self.client.get('/api/lab/replays/'+key,headers=self.headers)
        self.assertEqual(result.json['status'],'interrupted')
        self.assertEqual(self.client.post('/api/lab/replays/'+key+'/control',json={'action':'resume'},headers=self.headers).status_code,409)
        self.assertEqual(self.client.get('/api/lab/replays/not-a-run',headers=self.headers).status_code,400)
        self.assertEqual(self.client.get('/api/lab/replays/'+key+'/report',headers=self.headers).status_code,409)

    def test_pause_step_resume_and_report(self):
        gate=threading.Event()
        def evaluate(**kwargs):
            gate.wait(5)
            for i in range(3):kwargs['progress'](i/3,'checkpoint')
            return {'summary':{'A':{'tp':1}},'events':[]}
        with patch('innovation.lab.run',evaluate):
            key=self.client.post('/api/lab/replays',json={},headers=self.headers).json['id']
            def control(action):return self.client.post(f'/api/lab/replays/{key}/control',json={'action':action},headers=self.headers)
            control('pause');gate.set()
            self.assertEqual(self.client.post('/api/lab/replays',json={},headers=self.headers).status_code,409)
            control('step')
            for _ in range(100):
                if self.client.get('/api/lab/replays/'+key,headers=self.headers).json['status']=='paused':break
                time.sleep(.01)
            self.assertEqual(self.client.get('/api/lab/replays/'+key,headers=self.headers).json['status'],'paused')
            control('resume')
            for _ in range(100):
                if self.client.get('/api/lab/replays/'+key,headers=self.headers).json['status']=='completed':break
                time.sleep(.01)
            self.assertEqual(self.client.get('/api/lab/replays/'+key+'/report',headers=self.headers).json['summary']['A']['tp'],1)
