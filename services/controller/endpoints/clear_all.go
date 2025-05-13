package endpoints

import (
	"net/http"

	"dflipse.nl/ds-fit/controller/store"
)

func ClearAll(w http.ResponseWriter, r *http.Request) {
	store.Reports.Clear()
	store.TraceIds.Clear()

}
