import express, { Express } from "express";
import axios from "axios";

const PORT: number = parseInt(process.env.PORT || "8080");
const app: Express = express();

const TARGET_SERVICE_URLS: string = process.env.TARGET_SERVICE_URLS ?? "";
const TARGET_SERVICE_URL_LIST: string[] = TARGET_SERVICE_URLS.split(",");

const LOCAL_FALLBACK_VALUE: string | null = process.env.FALLBACK_VALUE ?? null;

if (TARGET_SERVICE_URL_LIST.length === 0) {
  console.error(
    "No target service URLs provided. Please set TARGET_SERVICE_URLS."
  );
  process.exit(1);
}

app.get("/", (req, res) => {
  Promise.allSettled(
    TARGET_SERVICE_URL_LIST.map((url) =>
      axios.get(url).then((response) => ({
        status: response.status,
        data: response.data,
      }))
    )
  )
    .then((results) => {
      // --- 1. if any of the requests fail, return 500
      const hasFailedResponse = results.some(
        (result) => result.status === "rejected"
      );

      if (hasFailedResponse) {
        if (LOCAL_FALLBACK_VALUE) {
          // 1.a Local resilience pattern: use default response
          res.status(200).send(LOCAL_FALLBACK_VALUE);
        } else {
          res.status(500).send("Internal Server Error");
        }
        return;
      }

      // --- 2. if all requests succeed, return the concatenated responses
      const successfulResponses = results
        .map(
          (result) =>
            (result as PromiseFulfilledResult<{ status: number; data: any }>)
              .value.data
        )
        .join("\n");
      res.status(200).send(successfulResponses);
    })
    .catch((error) => {
      console.error("Unexpected error:", error);
      res.status(500).send("Internal Server Error");
    });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Listening for requests on http://0.0.0.0:${PORT}`);
});
