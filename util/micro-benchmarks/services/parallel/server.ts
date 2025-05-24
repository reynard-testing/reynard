import express, { Express } from "express";
import axios from "axios";

const PORT: number = parseInt(process.env.PORT || "8080");
const app: Express = express();

const TARGET_SERVICE_URLS: string = process.env.TARGET_SERVICE_URLS ?? "";
const TARGET_SERVICE_URL_LIST: string[] = TARGET_SERVICE_URLS.split(",");

app.get("/get", (req, res) => {
  Promise.all(
    TARGET_SERVICE_URL_LIST.map((url) =>
      axios.get(url).then((response) => {
        return {
          status: response.status,
          data: response.data,
        };
      })
    )
  )
    .then((responses) => {
      const combinedData = responses
        .map((response) => response.data)
        .join("\n");
      res.status(200).send(combinedData);
    })
    .catch((error) => {
      console.error("Error fetching data from target services:", error);
      res.status(500).send("Internal Server Error");
    });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Listening for requests on http://0.0.0.0:${PORT}`);
});
