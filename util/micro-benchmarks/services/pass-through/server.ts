import express, { Express } from "express";
import axios from "axios";

const PORT: number = parseInt(process.env.PORT || "8080");
const app: Express = express();

const TARGET_SERVICE_URL: string =
  process.env.TARGET_SERVICE_URL || "http://localhost:8080";

app.get("/get", (req, res) => {
  axios
    .get(TARGET_SERVICE_URL)
    .then((response) => {
      res.status(response.status).send(response.data);
    })
    .catch((error: any) => {
      res.status(500).send("Internal Server Error");
    });
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Listening for requests on http://0.0.0.0:${PORT}`);
});
