from .models import ReportedSpan, Span


class ReportStore:
    reports: list[ReportedSpan] = []
    reports_by_span_id: dict[str, ReportedSpan] = {}
    reports_by_trace_id: dict[str, list[ReportedSpan]] = {}

    def add(self, report: ReportedSpan):
        self.reports.append(report)
        self.reports_by_span_id[report.span_id] = report
        self.reports_by_trace_id.setdefault(report.trace_id, []).append(report)
        return report

    def has_span_id(self, span_id: str) -> bool:
        return span_id in self.reports_by_span_id

    def get_by_span_id(self, span_id: str) -> ReportedSpan:
        return self.reports_by_span_id.get(span_id, None)

    def get_by_trace_id(self, trace_id: str) -> list[ReportedSpan]:
        return self.reports_by_trace_id.get(trace_id, [])
