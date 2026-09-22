// Local control-flow check only. No HTTP requests or k6 load are generated.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const counters = {};
class Counter {
  constructor(name) { this.name = name; counters[name] = 0; }
  add(value) { counters[this.name] += value; }
}
let requests = 0;
const source = fs.readFileSync(path.join(__dirname, 'queue-vu.js'), 'utf8')
  .replace(/^import .*;\r?\n/gm, '')
  .replace('export default function ()', 'function runUser()')
  .replace(/export /g, '');
const context = vm.createContext({
  __ENV: {SESSION_ID: 'session', SEAT_ID: 'seat', VUS: '1000', MODE: 'spike',
    TARGET: 'ticketing', FLOW: 'register'},
  open: () => JSON.stringify([{userId: 1, accessToken: ''}]),
  SharedArray: function (name, factory) { return factory(); },
  Counter, Trend: class { add() {} },
  check: (value, checks) => Object.values(checks).every(fn => fn(value)),
  exec: {vu: {iterationInScenario: 0}, scenario: {iterationInTest: 0},
    test: {abort: () => { throw new Error('Unexpected whole-test abort'); }}},
  http: {post: (url, body, params) => {
    assert.equal(params.timeout, '5s');
    assert.equal(params.headers['X-User-Id'], '1');
    assert.ok(url.startsWith('http://ticketing-service:8082/'));
    requests++;
    if (requests === 1) return {status: 0, error_code: 1050, error: 'request timeout',
      timings: {}, json: () => null};
    return {status: 200, timings: {}, json: () => ({code: 'SUCCESS',
      data: {userId: 1, sessionId: 'session'}})};
  }},
  console: {log() {}},
});
vm.runInContext(source + '\nrunUser(); runUser();', context);
assert.equal(requests, 2);
assert.equal(counters.users_started, 2);
assert.equal(counters.queue_registration_completed, 1);
assert.equal(counters.queue_registration_failed, 1);
assert.equal(counters.flow_errors, 1);
console.log('PASS: timeout counted, next registration completed, no global abort');
