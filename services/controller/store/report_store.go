package store

import (
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

func (rs *ReportStore) RemoveByTraceIdAndSpanId(TraceId faultload.TraceID, SpanId faultload.SpanID) {
	rs.mu.Lock()
	defer rs.mu.Unlock()

	// check if the traceId exists
	_, exists := rs.reportsByTraceId[TraceId]
	if !exists {
		return
	}

	// Remove the report from the reports slice
	for i, report := range rs.reports {
		if report.TraceId == TraceId && report.SpanId == SpanId {
			rs.reports = append(rs.reports[:i], rs.reports[i+1:]...)
			break
		}
	}

	// Remove the report from the reportsBySpanId ma
	if faultMap, ok := rs.reportsByTraceIdBySpanId[TraceId]; ok {
		delete(faultMap, SpanId)
		if len(faultMap) == 0 {
			delete(rs.reportsByTraceIdBySpanId, TraceId)
		}
	}

}

func (rs *ReportStore) Add(report trace.TraceReport) trace.TraceReport {
	// Remove it it already exists
	rs.RemoveByTraceIdAndSpanId(report.TraceId, report.SpanId)

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
