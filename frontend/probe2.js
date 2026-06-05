// Probe Spring SockJS RAW websocket: speak STOMP directly (no SockJS framing).
const FayeWebSocket = require('faye-websocket');
const NULL = '\x00';

(async () => {
  const res = await fetch('http://localhost:8080/api/dev/login?handle=alice', { method: 'POST' });
  const cookie = (res.headers.getSetCookie() || []).map(c => c.split(';')[0]).join('; ');
  console.log('login', res.status, cookie);

  const ws = new FayeWebSocket.Client('ws://localhost:8080/ws/websocket', null, {
    headers: { Cookie: cookie },
  });
  ws.on('open', () => {
    console.log('WS OPEN');
    ws.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n' + NULL);
    console.log('-> sent raw STOMP CONNECT');
  });
  ws.on('message', (e) => console.log('RECV:', JSON.stringify(String(e.data)).slice(0, 300)));
  ws.on('close', (e) => { console.log('CLOSE', e.code, e.reason); process.exit(0); });
  ws.on('error', (e) => console.log('ERROR', e.message));
  setTimeout(() => { console.log('timeout'); ws.close(); }, 4000);
})();
