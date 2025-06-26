package endpoints

import (
	"net/http"

	"go.reynard.dev/instrumentation/controller/store"
)

func ClearAll(w http.ResponseWriter, r *http.Request) {
	store.Reports.Clear()
	store.TraceIds.Clear()
}
