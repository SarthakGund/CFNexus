// Stage 5 code-run smoke: dev-login, then POST /api/code/run (C++ hello) -> Judge0.
const BASE = 'http://localhost:8080';
function makeJar() {
  const jar = {};
  return {
    header: () => Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; '),
    absorb: (res) => { for (const c of (res.headers.getSetCookie() || [])) { const [p]=c.split(';'); const i=p.indexOf('='); jar[p.slice(0,i)]=p.slice(i+1); } },
    get: (k) => jar[k],
  };
}
async function api(jar, path, { method='GET', body } = {}) {
  const headers = { Cookie: jar.header() };
  if (body) headers['Content-Type']='application/json';
  const x = jar.get('XSRF-TOKEN'); if (x && method!=='GET') headers['X-XSRF-TOKEN']=x;
  const res = await fetch(BASE+path, { method, headers, body: body?JSON.stringify(body):undefined });
  jar.absorb(res);
  const t = await res.text(); let j; try { j=JSON.parse(t); } catch { j=t; }
  return { status: res.status, body: j };
}
(async () => {
  const jar = makeJar();
  await api(jar, '/api/dev/login?handle=alice', { method:'POST' });
  await api(jar, '/api/auth/me');
  const code = '#include <iostream>\nint main(){ int a,b; std::cin>>a>>b; std::cout<<a+b<<"\\n"; return 0; }\n';
  const r = await api(jar, '/api/code/run', { method:'POST', body: { language:'cpp', code, stdin:'2 3\n' } });
  console.log('run status:', r.status);
  console.log('result:', JSON.stringify(r.body));
  const ok = r.status===200 && r.body && String(r.body.stdout).trim()==='5';
  console.log(ok ? 'CODE-RUN SMOKE: PASS (2+3=5)' : 'CODE-RUN SMOKE: FAIL/PENDING');
  process.exit(ok?0:1);
})().catch(e => { console.error('FAILED:', e.message); process.exit(1); });
