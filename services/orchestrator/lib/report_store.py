from .models import ReportedSpan, FaultUid


class ReportStore:
    reports: list[ReportedSpan] = []
    reports_by_span_id: dict[str, list[ReportedSpan]] = {}
    reports_by_trace_id: dict[str, list[ReportedSpan]] = {}
    reports_by_trace_by_fault_uid: dict[str, dict[FaultUid, ReportedSpan]] = {}

    def add(self, report: ReportedSpan):
        self.reports.append(report)
        self.reports_by_span_id.setdefault(report.span_id, []).append(report)
        self.reports_by_trace_id.setdefault(report.trace_id, []).append(report)
        self.reports_by_trace_by_fault_uid.setdefault(report.trace_id, {}) \
            [report.uid] = report
        return report

    def has_fault_uid_for_trace(self, trace_id: str, fid: FaultUid) -> bool:
        return trace_id in self.reports_by_trace_by_fault_uid and \
            fid in self.reports_by_trace_by_fault_uid.get(trace_id)

    def get_by_span_id(self, span_id: str) -> list[ReportedSpan]:
        return self.reports_by_span_id.get(span_id, [])

    def get_by_trace_and_fault_uid(self, trace_id: str, fid: FaultUid) -> ReportedSpan:
        reports_for_trace_by_fault_uid = self.reports_by_trace_by_fault_uid.get(trace_id)
        if reports_for_trace_by_fault_uid is None:
            return None
        return reports_for_trace_by_fault_uid.get(fid, None)

    def get_by_trace_id(self, trace_id: str) -> list[ReportedSpan]:
        return self.reports_by_trace_id.get(trace_id, [])
