import express, { Express } from "express";
import axios from "axios";
import fs from "fs";

const action = JSON.parse(
  fs.readFileSync(require.resolve("/action.json"), "utf-8")
);

const PORT: number = parseInt(process.env.PORT || "8080");
const app: Express = express();

type ActionOrder = "parallel" | "sequential";
type ActionComposition = {
  order: ActionOrder;
  actions: ServerAction[];
};
type ServerAction =
  | {
      endpoint: string;
      payload?: any;
      method: "GET" | "POST" | "PUT" | "DELETE";
      onFailure?: ServerAction;
    }
  | ActionComposition
  | string;

async function executeAction(action: ServerAction): Promise<any> {
  if (typeof action === "string") {
    // Handle LeafAction
    return action;
  }

  if ("order" in action) {
    // Handle ActionComposition
    if (action.order === "parallel") {
      const promises = action.actions.map(executeAction);
      return Promise.all(promises);
    } else {
      let result;
      for (const subAction of action.actions) {
        result = await executeAction(subAction);
      }
      return result;
    }
  } else {
    // Handle ServerAction
    try {
      const response = await axios({
        method: action.method,
        url: action.endpoint,
        data: action.payload,
      });
      return response.data;
    } catch (error) {
      if (action.onFailure) {
        return executeAction(action.onFailure);
      }
      throw error;
    }
  }
}

app.get("/", async (req, res) => {
  try {
    const result = await executeAction(action);
    res.status(200).send(result);
  } catch (error) {
    console.error("Error executing action:", error);
    res.status(500).send("Internal Server Error");
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`Listening for requests on http://0.0.0.0:${PORT}`);
});
