import { createServer } from "node:http";

const port = Number(process.env.PORT ?? 9091);
const latencyMs = Number(process.env.LATENCY_MS ?? 200);

const server = createServer((request, response) => {
  const match = request.method === "GET" && request.url.match(/^\/identite\/(\d{10})$/);
  if (!match) {
    response.writeHead(404).end();
    return;
  }
  const npi = match[1];
  setTimeout(() => {
    if (npi.startsWith("000")) {
      response.writeHead(404).end();
      return;
    }
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ nom: "CANDIDAT", prenoms: `Npi ${npi.slice(-4)}` }));
  }, latencyMs);
});

server.listen(port, () => console.log(`Service d'identification simulé sur :${port}, latence ${latencyMs} ms`));
