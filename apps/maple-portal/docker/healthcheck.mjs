// Loopback needs no external proxy and never checks or consumes auction quota.
import http from 'node:http';
const probe = path => new Promise((ok, fail) => {
  const req = http.get(path, { timeout:3000 }, res => { res.resume(); res.statusCode === 200 ? ok() : fail(); });
  req.on('error', fail); req.on('timeout', () => req.destroy(new Error('timeout')));
});
try { await Promise.all([probe('http://127.0.0.1:8877/healthz'), probe('http://127.0.0.1:7900/vnc.html')]); }
catch { process.exit(1); }
