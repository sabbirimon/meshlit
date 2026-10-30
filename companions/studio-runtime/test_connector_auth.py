"""Connector credential transport contracts; no live provider account or agent."""
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import agent_host


class ConnectorAuthTest(unittest.TestCase):
    def setUp(self):
        self.token = 'omb_sess_fixture_000000000000000000000000'
        self.config = {'name': 'maus', 'type': 'stdio', 'command': sys.executable, 'args': ['mcp'],
                       'auth': {'kind': 'openmausbot-session', 'port': 8799},
                       'env': {'OMB_PORT': '8799', 'OPENMAUSBOT_URL': 'http://127.0.0.1:8799', 'OPENMAUSBOT_TOKEN': self.token}}

    def test_only_bound_environment_reaches_stdio_transport(self):
        with patch.object(agent_host, 'McpStdioServer') as server:
            agent_host.connectors([self.config])
        self.assertEqual(server.call_args.kwargs['env'], self.config['env'])
        self.assertEqual(server.call_args.kwargs['args'], ['mcp'])

    def test_rejects_other_hosts_ports_variables_auth_and_secret_shapes(self):
        for mutate in [lambda c: c['env'].update(OPENMAUSBOT_URL='https://remote.test'),
                       lambda c: c['env'].update(OMB_PORT='8800'),
                       lambda c: c['env'].update(PATH='/different'),
                       lambda c: c['env'].update(OPENMAUSBOT_TOKEN='invalid'),
                       lambda c: c.update(args=['mcp', 'extra']),
                       lambda c: c.update(type='http'),
                       lambda c: c.update(auth={'kind': 'other', 'port': 8799}),
                       lambda c: c.update(auth=None)]:
            config = copy.deepcopy(self.config); mutate(config)
            with self.subTest(config_keys=list(config)), self.assertRaises(ValueError) as failure:
                agent_host.connector_environment(config)
            self.assertNotIn(self.token, str(failure.exception))

    def test_plain_connector_gets_no_environment(self):
        self.assertEqual(agent_host.connector_environment({'type': 'stdio'}), {})
        with self.assertRaises(ValueError):
            agent_host.connector_environment({'type': 'stdio', 'env': {'TOKEN': 'hidden'}})


if __name__ == '__main__': unittest.main()
