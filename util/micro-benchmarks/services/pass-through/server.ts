import express, { Express } from "express";
import axios from "axios";

const PORT: number = parseInt(process.env.PORT || "8080");
const app: Express = express();

const TARGET_SERVICE_URL: string = process.env.TARGET_SERVICE_URL ?? "";
const LOCAL_FALLBACK_VALUE: string | null = process.env.FALLBACK_VALUE ?? null;

if (!TARGET_SERVICE_URL) {
  console.error(
    "No target service URL provided. Please set TARGET_SERVICE_URL."
  );
  process.exit(1);
}

app.get("/", (req, res) => {
  axios
    .get(TARGET_SERVICE_URL)
    .then((response) => {
      res.status(response.status).send(response.data);
    })
    .catch((error: any) => {
      if (LOCAL_FALLBACK_VALUE) {
        // Local resilience pattern: use default response
        res.status(200).send(LOCAL_FALLBACK_VALUE);
        return;
      }
      res.status(500).send("Internal Server Error");
    });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Listening for requests on http://0.0.0.0:${PORT}`);
});
