"""Local test fixture responder. Not a replacement for Platform authorization/validation."""
import asyncio
import json
import re

FIXTURES = {}
COUNTS = {}
FAULT = {'delayMs': 0, 'status': 200}


async def respond(method, path, body, peer):
    status, data = route(method, path, body, peer)
    match = re.fullmatch(r'/api/v1/internal/event-sessions/([0-9a-f-]{36})/sales-status', path)
    # The first fixture lookup is the runner's dependency preflight, not load.
    if method == 'GET' and match and status == 200 and COUNTS[match[1]] > 1:
        fault = FAULT.copy()
        await asyncio.sleep(fault['delayMs'] / 1000)
        if fault['status'] != 200:
            return fault['status'], {'code': 'INJECTED_PLATFORM_FAILURE'}
    return status, data


def route(method, path, body, peer):
    if path == '/fault':
        if peer not in ('127.0.0.1', '::1'):
            return 403, {}
        if method == 'POST':
            config = json.loads(body)
            if config not in ({'delayMs': 0, 'status': 200}, {'delayMs': 500, 'status': 200}, {'delayMs': 0, 'status': 503}):
                return 400, {}
            FAULT.update(config)
        if method in ('GET', 'POST'):
            return 200, dict(FAULT, preflightCallsExcludedPerSession=1)
    if method == 'GET' and path == '/health':
        return 200, {'status': 'UP'}
    if method == 'POST' and path == '/fixtures':
        if peer not in ('127.0.0.1', '::1'):
            return 403, {}
        fixture = json.loads(body)
        sid = fixture['sessionId']
        if not re.fullmatch(r'[0-9a-f-]{36}', sid):
            return 400, {}
        FIXTURES[sid] = fixture
        COUNTS[sid] = 0
        return 200, {'registered': sid}
    match = re.fullmatch(r'/api/v1/internal/event-sessions/([0-9a-f-]{36})/sales-status', path)
    if method == 'GET' and match and match[1] in FIXTURES:
        sid = match[1]
        COUNTS[sid] += 1
        return 200, FIXTURES[sid]
    if method == 'GET' and path == '/counts' and peer in ('127.0.0.1', '::1'):
        return 200, COUNTS.copy()
    return 404, {}


async def handle(reader, writer):
    try:
        while True:
            raw = await asyncio.wait_for(reader.readuntil(b'\r\n\r\n'), 30)
            lines = raw.decode('ascii').split('\r\n')
            method, path, _ = lines[0].split(' ')
            headers = dict(line.split(':', 1) for line in lines[1:] if ':' in line)
            headers = {k.lower(): v.strip() for k, v in headers.items()}
            size = int(headers.get('content-length', '0'))
            if size > 16384:
                break
            body = await asyncio.wait_for(reader.readexactly(size), 5)
            status, data = await respond(method, path, body, writer.get_extra_info('peername')[0])
            payload = json.dumps(data).encode()
            writer.write(f'HTTP/1.1 {status} Result\r\nContent-Type: application/json\r\nContent-Length: {len(payload)}\r\n\r\n'.encode() + payload)
            await writer.drain()
            if headers.get('connection', '').lower() == 'close':
                break
    except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, TimeoutError, ConnectionError, ValueError, KeyError):
        pass
    finally:
        writer.close()
        try:
            await writer.wait_closed()
        except ConnectionError:
            pass


async def main():
    server = await asyncio.start_server(handle, '0.0.0.0', 8081, backlog=4096)
    async with server:
        await server.serve_forever()


if __name__ == '__main__':
    asyncio.run(main())
