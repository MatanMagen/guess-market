// Guess Market web client (exercise 4). Serves the page and passes every API call through to the
// exercise 3 server, so the browser talks to one address only.
//
//   node server.js            then open http://localhost:3000
//
// Only Node's own modules are used, so there is nothing to install. The API is forwarded under the
// same path Tomcat serves it on (/guess-market/...), which is what lets Tomcat's session cookie,
// set for that path, come back on every request exactly as it does for the JavaFX client.

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT) || 3000;
const TOMCAT = { host: process.env.GM_SERVER_HOST || 'localhost', port: Number(process.env.GM_SERVER_PORT) || 8080 };
const API_PREFIX = '/guess-market/';
const PUBLIC = path.join(__dirname, 'public');
const TYPES = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8' };

function forward(req, res) {
  const upstream = http.request(
    { host: TOMCAT.host, port: TOMCAT.port, path: req.url, method: req.method,
      headers: { ...req.headers, host: `${TOMCAT.host}:${TOMCAT.port}` } },
    reply => {
      res.writeHead(reply.statusCode, reply.headers);
      reply.pipe(res);
    });
  upstream.on('error', () => {
    res.writeHead(502, { 'content-type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: `The Guess Market server at http://${TOMCAT.host}:${TOMCAT.port}/guess-market `
        + 'cannot be reached. Make sure Tomcat is running with guess-market.war deployed.' }));
  });
  req.pipe(upstream);
}

function serveFile(req, res) {
  const wanted = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const file = path.normalize(path.join(PUBLIC, wanted === '/' ? 'index.html' : wanted));
  if (!file.startsWith(PUBLIC)) {
    res.writeHead(403).end();
    return;
  }
  fs.readFile(file, (err, data) => {
    if (err) {
      res.writeHead(404, { 'content-type': 'text/plain' }).end('Not found');
      return;
    }
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream' });
    res.end(data);
  });
}

http.createServer((req, res) => (req.url.startsWith(API_PREFIX) ? forward(req, res) : serveFile(req, res)))
  .listen(PORT, () => {
    console.log(`Guess Market web client: open http://localhost:${PORT}`);
    console.log(`Passing API calls to http://${TOMCAT.host}:${TOMCAT.port}${API_PREFIX}`);
  });
