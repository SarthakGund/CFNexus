// Stage 4 headless driver: two dev users run a full rated 1v1 ending in resign.
// Verifies the core duel loop + persistence without the UI.
const FayeWebSocket = require('faye-websocket');
const BASE = 'http://localhost:8080';
const NULL = '\x00';

function makeJar() {
  const jar = {};
  return {
    header: () => Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; '),
    absorb: (res) => {
      for (const c of (res.headers.getSetCookie() || [])) {
        const [pair] = c.split(';');
        const i = pair.indexOf('=');
        jar[pair.slice(0, i)] = pair.slice(i + 1);
      }
    },
    get: (k) => jar[k],
  };
}

async function api(jar, path, { method = 'GET', body } = {}) {
  const headers = { Cookie: jar.header() };
  if (body) headers['Content-Type'] = 'application/json';
  const xsrf = jar.get('XSRF-TOKEN');
  if (xsrf && method !== 'GET') headers['X-XSRF-TOKEN'] = xsrf;
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  jar.absorb(res);
  const text = await res.text();
  let json; try { json = JSON.parse(text); } catch { json = text; }
  return { status: res.status, body: json };
}

async function loginUser(handle) {
  const jar = makeJar();
  const r = await api(jar, `/api/dev/login?handle=${handle}`, { method: 'POST' });
  await api(jar, '/api/auth/me'); // triggers XSRF-TOKEN cookie
  console.log(`login ${handle}: ${r.status} duelRating=${r.body.duelRating} id=${r.body.id}`);
  return { jar, user: r.body };
}

// Minimal raw-STOMP send over Spring's /ws/websocket transport.
function stompResign(jar, roomCode) {
  return new Promise((resolve, reject) => {
    const ws = new FayeWebSocket.Client(`ws://localhost:8080/ws/websocket`, null,
      { headers: { Cookie: jar.header() } });
    let connected = false;
    const fail = (m) => { try { ws.close(); } catch {} reject(new Error(m)); };
    ws.on('open', () => ws.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n' + NULL));
    ws.on('message', (e) => {
      const data = String(e.data);
      if (data.startsWith('CONNECTED')) {
        connected = true;
        ws.send(`SEND\ndestination:/app/duel/${roomCode}/resign\ncontent-length:0\n\n` + NULL);
        console.log('  -> sent resign for', roomCode);
        setTimeout(() => { try { ws.close(); } catch {} resolve(true); }, 1500);
      } else if (data.startsWith('ERROR')) {
        fail('STOMP ERROR: ' + data.replace(/\n/g, ' ').slice(0, 120));
      }
    });
    ws.on('error', (e) => fail('WS error: ' + e.message));
    setTimeout(() => { if (!connected) fail('STOMP connect timeout'); }, 5000);
  });
}

(async () => {
  const alice = await loginUser('alice');
  const bob = await loginUser('bob');

  const created = await api(alice.jar, '/api/duels/create',
    { method: 'POST', body: { type: 'RATED_1V1', problemRating: 800 } });
  console.log('create:', created.status, JSON.stringify(created.body));
  const roomCode = created.body.roomCode;
  if (!roomCode) throw new Error('no roomCode');

  const joined = await api(bob.jar, `/api/duels/${roomCode}/join`, { method: 'POST', body: {} });
  console.log('join:', joined.status, JSON.stringify(joined.body).slice(0, 200));

  const started = await api(alice.jar, `/api/duels/${roomCode}/start`, { method: 'POST' });
  console.log('start:', started.status);

  const problem = await api(alice.jar, `/api/duels/${roomCode}/problem`);
  console.log('problem:', problem.status, JSON.stringify(problem.body).slice(0, 160));

  // Bob resigns -> Alice wins.
  await stompResign(bob.jar, roomCode);

  // Re-fetch both users to see rating movement.
  const a2 = await api(alice.jar, '/api/auth/me');
  const b2 = await api(bob.jar, '/api/auth/me');
  console.log(`AFTER: alice duelRating=${a2.body.duelRating} wins=${a2.body.duelWins} | bob duelRating=${b2.body.duelRating} losses=${b2.body.duelLosses}`);
  process.exit(0);
})().catch((e) => { console.error('DRIVER FAILED:', e.message); process.exit(1); });
