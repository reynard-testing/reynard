package store

import (
	"log"
	"sync"

	"dflipse.nl/ds-fit/shared/faultload"
	"dflipse.nl/ds-fit/shared/trace"
)

type SpanId string
type TraceId string

type ReportStore struct {
	mu                       sync.RWMutex
	reports                  []trace.TraceReport
	reportsByTraceId         map[faultload.TraceID][]trace.TraceReport
	reportsByTraceIdBySpanId map[faultload.TraceID]map[faultload.SpanID]trace.TraceReport
}

func NewReportStore() *ReportStore {
	return &ReportStore{
		reports:                  []trace.TraceReport{},
		reportsByTraceId:         make(map[faultload.TraceID][]trace.TraceReport),
		reportsByTraceIdBySpanId: make(map[faultload.TraceID]map[faultload.SpanID]trace.TraceReport),
	}
}

var Reports = NewReportStore()

func (rs *ReportStore) Clear() {
	rs.mu.Lock()
	defer rs.mu.Unlock()

	rs.reports = []trace.TraceReport{}
	rs.reportsByTraceId = make(map[faultload.TraceID][]trace.TraceReport)
	rs.reportsByTraceIdBySpanId = make(map[faultload.TraceID]map[faultload.SpanID]trace.TraceReport)
}

func (rs *ReportStore) RemoveByTraceId(TraceId faultload.TraceID) {
	rs.mu.Lock()
	defer rs.mu.Unlock()

	traceReports, exists := rs.reportsByTraceId[TraceId]
	if !exists {
		return
	}

	delete(rs.reportsByTraceId, TraceId)

	for _, traceReport := range traceReports {
		rs.RemoveByTraceIdAndSpanId(traceReport.TraceId, traceReport.SpanId)
	}
}

func remove(s []trace.TraceReport, i int) []trace.TraceReport {
	// swap the element with the last element
	// and then remove the last element
	// this is more efficient than using append
	s[i] = s[len(s)-1]
	return s[:len(s)-1]
}

func (rs *ReportStore) RemoveByTraceIdAndSpanId(TraceId faultload.TraceID, SpanId faultload.SpanID) int {
	rs.mu.Lock()
	defer rs.mu.Unlock()

	index := -1

	// Remove the report from the reports slice
	for i := 0; i < len(rs.reports); i++ {
		if rs.reports[i].TraceId == TraceId && rs.reports[i].SpanId == SpanId {
			rs.reports = remove(rs.reports, i)
			index = i
			break
		}
	}

	// Remove the report from the reportsByTraceId map
	if byTraceId, ok := rs.reportsByTraceId[TraceId]; ok {
		for i := 0; i < len(byTraceId); i++ {
			if byTraceId[i].SpanId == SpanId {
				byTraceId = remove(byTraceId, i)
				break
			}
		}

		if len(byTraceId) == 0 {
			delete(rs.reportsByTraceId, TraceId)
		} else {
			rs.reportsByTraceId[TraceId] = byTraceId
		}
	}

	// Remove the report from the reportsByTraceIdBySpanId map
	if faultMap, ok := rs.reportsByTraceIdBySpanId[TraceId]; ok {
		delete(faultMap, SpanId)
		if len(faultMap) == 0 {
			delete(rs.reportsByTraceIdBySpanId, TraceId)
		}
	}

	return index
}

func (rs *ReportStore) Upsert(report trace.TraceReport) bool {
	if rs.HasSpanIdForTraceId(report.TraceId, report.SpanId) {
		rs.Replace(report)
		return true
	} else {
		rs.Add(report)
		return false
	}
}

func (rs *ReportStore) Replace(report trace.TraceReport) {
	rs.mu.Lock()
	defer rs.mu.Unlock()

	found := false
	// Replace the old report with the new one
	for i := 0; i < len(rs.reports); i++ {
		if rs.reports[i].Matches(&report) {
			rs.reports[i] = report
			found = true
			break
		}
	}

	if !found {
		rs.reports = append(rs.reports, report)
		log.Printf("Could not find report to replace: (%s,%s), added instead\n", report.TraceId, report.SpanId)
	}

	found = false

	if byTraceId, ok := rs.reportsByTraceId[report.TraceId]; ok {
		for i := 0; i < len(byTraceId); i++ {
			if byTraceId[i].Matches(&report) {
				byTraceId[i] = report
				found = true
				break
			}
		}

		rs.reportsByTraceId[report.TraceId] = byTraceId
	}

	if !found {
		log.Printf("Could not find report to replace by traceId: (%s,%s), added instead\n", report.TraceId, report.SpanId)
		rs.reportsByTraceId[report.TraceId] = append(rs.reportsByTraceId[report.TraceId], report)
	}

	if _, exists := rs.reportsByTraceIdBySpanId[report.TraceId]; !exists {
		log.Printf("Could not mapping for traceId: %s, added instead\n", report.TraceId)
		rs.reportsByTraceIdBySpanId[report.TraceId] = make(map[faultload.SpanID]trace.TraceReport)
	}

	rs.reportsByTraceIdBySpanId[report.TraceId][report.SpanId] = report
}

func (rs *ReportStore) Add(report trace.TraceReport) trace.TraceReport {
	rs.mu.Lock()
	defer rs.mu.Unlock()
	rs.reports = append(rs.reports, report)
	rs.reportsByTraceId[report.TraceId] = append(rs.reportsByTraceId[report.TraceId], report)

	if _, exists := rs.reportsByTraceIdBySpanId[report.TraceId]; !exists {
		rs.reportsByTraceIdBySpanId[report.TraceId] = make(map[faultload.SpanID]trace.TraceReport)
	}
	rs.reportsByTraceIdBySpanId[report.TraceId][report.SpanId] = report

	return report
}

func (rs *ReportStore) HasSpanIdForTraceId(TraceId faultload.TraceID, spanId faultload.SpanID) bool {
	rs.mu.RLock()
	defer rs.mu.RUnlock()

	if faultMap, exists := rs.reportsByTraceIdBySpanId[TraceId]; exists {
		_, exists := faultMap[spanId]
		return exists
	}

	return false
}

func (rs *ReportStore) GetByTraceAndSpanId(traceId faultload.TraceID, spanId faultload.SpanID) *trace.TraceReport {
	rs.mu.RLock()
	defer rs.mu.RUnlock()

	if faultMap, exists := rs.reportsByTraceIdBySpanId[traceId]; exists {
		if report, exists := faultMap[spanId]; exists {
			return &report
		}
	}

	return nil
}

func (rs *ReportStore) HasTraceId(traceId faultload.TraceID) bool {
	rs.mu.RLock()
	defer rs.mu.RUnlock()

	_, exists := rs.reportsByTraceId[traceId]
	return exists
}

func (rs *ReportStore) GetByTraceId(TraceId faultload.TraceID) []trace.TraceReport {
	rs.mu.RLock()
	defer rs.mu.RUnlock()

	reports, exists := rs.reportsByTraceId[TraceId]
	if !exists {
		return []trace.TraceReport{}
	}

	return reports
}
