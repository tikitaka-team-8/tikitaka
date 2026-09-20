import importlib.util
import pathlib
import unittest
import json
import asyncio

spec = importlib.util.spec_from_file_location('platform_stub', pathlib.Path(__file__).with_name('platform-stub.py'))
stub = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stub)


class StubTest(unittest.TestCase):
    def setUp(self):
        stub.FIXTURES.clear()
        stub.COUNTS.clear()
        stub.FAULT.update(delayMs=0, status=200)

    def test_fault_is_local_validated_and_preserves_preflight(self):
        config = json.dumps({'delayMs':0,'status':503})
        self.assertEqual(403, stub.route('POST','/fault',config,'10.0.0.1')[0])
        self.assertEqual(400, stub.route('POST','/fault','{"delayMs":999,"status":200}','127.0.0.1')[0])
        self.assertEqual(200, stub.route('POST','/fault',config,'127.0.0.1')[0])
        sid = '11111111-1111-1111-1111-111111111111'
        stub.route('POST','/fixtures',json.dumps({'sessionId':sid}),'127.0.0.1')
        path = '/api/v1/internal/event-sessions/' + sid + '/sales-status'
        async def check():
            self.assertEqual(200, (await stub.respond('GET',path,b'','10.0.0.1'))[0])
            self.assertEqual(503, (await stub.respond('GET',path,b'','10.0.0.1'))[0])
            self.assertEqual(200, (await stub.respond('GET','/health',b'','10.0.0.1'))[0])
        asyncio.run(check())
        self.assertEqual(2, stub.COUNTS[sid])

    def test_delay_is_applied_only_to_measured_calls(self):
        from unittest.mock import AsyncMock, patch
        stub.FAULT.update(delayMs=500,status=200)
        sid = '11111111-1111-1111-1111-111111111111'
        stub.route('POST','/fixtures',json.dumps({'sessionId':sid}),'127.0.0.1')
        path = '/api/v1/internal/event-sessions/' + sid + '/sales-status'
        async def check():
            with patch.object(stub.asyncio,'sleep',new_callable=AsyncMock) as sleep:
                await stub.respond('GET',path,b'','10.0.0.1')
                sleep.assert_not_awaited()
                self.assertEqual(200,(await stub.respond('GET',path,b'','10.0.0.1'))[0])
                sleep.assert_awaited_once_with(0.5)
        asyncio.run(check())

    def test_fixture_is_required_and_can_only_be_registered_locally(self):
        sid = '11111111-1111-1111-1111-111111111111'
        path = '/api/v1/internal/event-sessions/' + sid + '/sales-status'
        stub.FIXTURES.clear()
        fixture = {'sessionId':sid,'sessionStatus':'CANCELLED','queueEnabled':False,
                   'salesOpenAt':'2026-01-01T00:00:00Z','salesCloseAt':'2026-01-02T00:00:00Z'}
        self.assertEqual(404, stub.route('GET',path,b'','10.0.0.1')[0])
        self.assertEqual(403, stub.route('POST','/fixtures',json.dumps(fixture),'10.0.0.1')[0])
        self.assertEqual(200, stub.route('POST','/fixtures',json.dumps(fixture),'127.0.0.1')[0])
        self.assertEqual((200,fixture), stub.route('GET',path,b'','10.0.0.1'))
        self.assertEqual(1, stub.COUNTS[sid])

    def test_http_connection_can_be_reused(self):
        async def check():
            server = await asyncio.start_server(stub.handle,'127.0.0.1',0)
            async with server:
                reader,writer = await asyncio.open_connection('127.0.0.1',server.sockets[0].getsockname()[1])
                for _ in range(2):
                    writer.write(b'GET /health HTTP/1.1\r\nHost: localhost\r\n\r\n')
                    await writer.drain()
                    headers = await reader.readuntil(b'\r\n\r\n')
                    self.assertIn(b'200',headers.split(b'\r\n')[0])
                    size = int(next(l.split(b':')[1] for l in headers.split(b'\r\n') if l.startswith(b'Content-Length:')))
                    self.assertEqual({'status':'UP'},json.loads(await reader.readexactly(size)))
                writer.close()
                await writer.wait_closed()
        asyncio.run(check())


if __name__ == '__main__':
    unittest.main()
