package main

import (
	"log"
	"os"

	"go.reynard.dev/instrumentation/controller/server"
	"go.reynard.dev/instrumentation/shared/util"
)

func main() {
	useTelemetry := os.Getenv("USE_OTEL") == "true"
	port := util.GetIntEnvOrDefault("CONTROLLER_PORT", 5000)

	if err := server.StartController(port, useTelemetry); err != nil {
		log.Fatalln(err)
	}
}
