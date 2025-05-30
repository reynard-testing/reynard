import express, { Express } from "express";

const PORT: number = parseInt(process.env.PORT || "8080");
const app: Express = express();

const RESPONSE = process.env.RESPONSE ?? "Hello World!";

app.get("/", (req, res) => {
  res.send(RESPONSE);
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Listening for requests on http://0.0.0.0:${PORT}`);
});
