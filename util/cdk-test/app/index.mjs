import http from "node:http";

const targetUrl = process.env.TARGET_URL;
const repeats = Number(process.env.TARGET_REPEATS ?? 3);

// Without a TARGET_URL this is a leaf service, otherwise it calls the target `repeats` times.
async function handle(req) {
  if (req.url === "/health" || !targetUrl) {
    return { status: 200, body: "OK" };
  }

  // Forward the trace context, so the downstream proxy links its span to ours
  const headers = {};
  for (const key of ["traceparent", "tracestate"]) {
    if (req.headers[key]) headers[key] = req.headers[key];
  }

  let response;
  for (let i = 0; i < repeats; i++) {
    try {
      const res = await fetch(targetUrl, { headers });
      response = { status: res.status, body: await res.text() };
    } catch (err) {
      return { status: 500, body: String(err) };
    }

    if (response.status !== 200) {
      console.log("Returning early error", response);
      return response;
    }
  }

  return response;
}

http
  .createServer(async (req, res) => {
    const { status, body } = await handle(req);
    res.writeHead(status).end(body);
  })
  .listen(Number(process.env.PORT ?? 8080));
