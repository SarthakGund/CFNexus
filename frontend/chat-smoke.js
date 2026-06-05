// Stage 5 chat smoke: alice sends an E2E-encrypted (opaque) payload; bob receives
// the identical ciphertext over /topic and chat-history backfills it. Proves the
// server is a pure relay that never sees plaintext (spec §11).
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
  await api(jar, '/api/auth/me');
  return { jar, user: r.body };
}

// Open a STOMP connection, subscribe to chat, resolve with a controller that can
// send and collects received MESSAGE bodies.
function stompClient(jar, roomCode, onMessage) {
  return new Promise((resolve, reject) => {
    const ws = new FayeWebSocket.Client('ws://localhost:8080/ws/websocket', null,
      { headers: { Cookie: jar.header() } });
    const t = setTimeout(() => reject(new Error('connect timeout')), 5000);
    ws.on('open', () => ws.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n' + NULL));
    ws.on('message', (e) => {
      const data = String(e.data);
      if (data.startsWith('CONNECTED')) {
        clearTimeout(t);
        ws.send(`SUBSCRIBE\nid:sub-0\ndestination:/topic/duel/${roomCode}/chat\n\n` + NULL);
        resolve({
          send: (obj) => ws.send(
            `SEND\ndestination:/app/duel/${roomCode}/chat\ncontent-type:application/json\n\n`
            + JSON.stringify(obj) + NULL),
          close: () => { try { ws.close(); } catch {} },
        });
      } else if (data.startsWith('MESSAGE')) {
        const body = data.slice(data.indexOf('\n\n') + 2).replace(/\x00$/, '');
        try { onMessage(JSON.parse(body)); } catch { onMessage(body); }
      } else if (data.startsWith('ERROR')) {
        reject(new Error('STOMP ERROR: ' + data.replace(/\n/g, ' ').slice(0, 120)));
      }
    });
    ws.on('error', (e) => reject(new Error('WS error: ' + e.message)));
  });
}

(async () => {
  const alice = await loginUser('alice');
  const bob = await loginUser('bob');
  const created = await api(alice.jar, '/api/duels/create',
    { method: 'POST', body: { type: 'RATED_1V1', problemRating: 800 } });
  const roomCode = created.body.roomCode;
  await api(bob.jar, `/api/duels/${roomCode}/join`, { method: 'POST', body: {} });
  console.log('room:', roomCode);

  const received = [];
  const bobConn = await stompClient(bob.jar, roomCode, (m) => received.push(m));
  const aliceConn = await stompClient(alice.jar, roomCode, () => {});
  await new Promise((r) => setTimeout(r, 400)); // let SUBSCRIBE register

  // Opaque ciphertext payload — server must never need plaintext.
  const payload = { ciphertext: 'BASE64CIPHERTEXTxyz==', iv: 'random-iv-123', senderPublicKeyB64: 'PUBKEYbob==' };
  aliceConn.send(payload);
  await new Promise((r) => setTimeout(r, 800));

  const relayed = received.find((m) => m && m.ciphertext === payload.ciphertext);
  console.log('bob received relayed ciphertext:', !!relayed);
  console.log('  payload matches (pure relay):', JSON.stringify(relayed) === JSON.stringify(payload));

  const hist = await api(bob.jar, `/api/duels/${roomCode}/chat-history`);
  console.log('chat-history status:', hist.status, 'entries:', Array.isArray(hist.body) ? hist.body.length : hist.body);
  const histHasCipher = Array.isArray(hist.body) && hist.body.some((s) => String(s).includes(payload.ciphertext));
  console.log('  history backfills ciphertext:', histHasCipher);

  bobConn.close(); aliceConn.close();
  const pass = !!relayed && histHasCipher;
  console.log(pass ? 'CHAT SMOKE: PASS' : 'CHAT SMOKE: FAIL');
  process.exit(pass ? 0 : 1);
})().catch((e) => { console.error('CHAT SMOKE FAILED:', e.message); process.exit(1); });
