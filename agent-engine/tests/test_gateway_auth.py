import os
import unittest
from unittest.mock import patch
from api.routes import create_app


class GatewayAuthTest(unittest.TestCase):
    def test_health_is_public_but_engine_requires_internal_secret(self):
        with patch.dict(os.environ, {'AGENT_INTERNAL_TOKEN': 'unit-secret'}):
            client = create_app().test_client()
            self.assertEqual(client.get('/api/health').status_code, 200)
            self.assertEqual(client.get('/api/status').status_code, 401)
            self.assertEqual(client.get('/api/status', headers={'X-Agent-Token': 'wrong'}).status_code, 401)
            # Authorization passed; no orchestrator in this unit fixture.
            self.assertEqual(client.get('/api/status', headers={'X-Agent-Token': 'unit-secret'}).status_code, 500)

    def test_missing_secret_fails_closed(self):
        with patch.dict(os.environ, {'AGENT_INTERNAL_TOKEN': ''}):
            self.assertEqual(create_app().test_client().get('/api/status').status_code, 503)
