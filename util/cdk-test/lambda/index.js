const https = require("https");

const targetUrl = process.env.TARGET_URL;
const repeats = Number(process.env.TARGET_REPEATS ?? 3);

function requestToLeaf(forwardHeaders) {
  const url = new URL(targetUrl);
  const options = {
    hostname: url.hostname,
    path: url.pathname + url.search,
    headers: forwardHeaders,
  };
  console.log("Forwarding request to leaf lambda", "options:", options);

  return new Promise((resolve, reject) => {
    https
      .get(options, (res) => {
        let data = "";
        res.on("data", (chunk) => {
          data += chunk;
        });
        res.on("end", () => {
          console.log("Received response from leaf lambda:", data);
          resolve({ statusCode: res.statusCode, body: data });
        });
      })
      .on("error", (err) => {
        resolve({ statusCode: 500, body: String(err) });
      });
  });
}

exports.handler = async (event) => {
  // TODO: setup lambda correctly that it does not start a new trace...
  const headers = (event && event.headers) || {};
  const forwardHeaders = {};
  if (headers["traceparent"])
    forwardHeaders["traceparent"] = headers["traceparent"];
  if (headers["tracestate"])
    forwardHeaders["tracestate"] = headers["tracestate"];

  console.log("Received request in forwarder lambda", "event:", event);

  let response;
  for (let i = 0; i < repeats; i++) {
    response = await requestToLeaf(forwardHeaders);
    if (response.statusCode !== 200) {
      console.log(
        "Returning early error from forwarder lambda",
        "response:",
        response,
      );
      return response;
    }
  }
  console.log(
    "Returning response from forwarder lambda",
    "response:",
    response,
  );
  return response;
};
