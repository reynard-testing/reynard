import express, { Express } from "express";
import axios from "axios";

const PORT: number = parseInt(process.env.PORT || "8080");
const app: Express = express();

const TARGET_SERVICE_URLS: string = process.env.TARGET_SERVICE_URLS ?? "";
const TARGET_SERVICE_URL_LIST: string[] = TARGET_SERVICE_URLS.split(",");

app.get("/get", (req, res) => {
  Promise.allSettled(
    TARGET_SERVICE_URL_LIST.map((url) =>
      axios.get(url).then((response) => ({
        status: response.status,
        data: response.data,
      }))
    )
  )
    .then((results) => {
      const failedResponses = results.filter(
        (result) => result.status === "rejected"
      );

      if (failedResponses.length > 0) {
        res.status(500).send("Internal Server Error");
        return;
      }

      const successfulResponses = results
        .filter((result) => result.status === "fulfilled")
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
