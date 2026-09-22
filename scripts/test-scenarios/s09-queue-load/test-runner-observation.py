"""Exercise runner observation handling without importing its executable load workflow."""
import ast
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import Mock

source = Path(__file__).with_name('run-vu.py')
tree = ast.parse(source.read_text(encoding='utf-8-sig'))
scope = dict(json=json, time=time)
for name in ('save', 'capture_observation'):
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
    exec(compile(ast.Module(body=[node], type_ignores=[]), str(source), 'exec'), scope)


class ObservationTest(unittest.TestCase):
    def test_timeout_is_missing_evidence_and_does_not_cancel_next_observation(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            observe = scope['capture_observation']
            self.assertIsNone(observe(path, 'periodic', Mock(side_effect=TimeoutError('timed out'))))
            self.assertEqual({'waiting': 7}, observe(path, 'periodic', lambda: {'waiting': 7}))
            errors = json.loads((path / 'collection-errors.json').read_text())
            self.assertEqual(1, len(errors))
            self.assertEqual('TimeoutError', errors[0]['errorType'])

    def test_success_creates_no_collection_error_file(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder)
            self.assertEqual(0, scope['capture_observation'](path, 'periodic', lambda: 0))
            self.assertFalse((path / 'collection-errors.json').exists())


if __name__ == '__main__':
    unittest.main()
