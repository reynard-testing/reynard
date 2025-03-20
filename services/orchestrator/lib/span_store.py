from .models import Span, TraceTreeNode


class SpanStore:
    spans: list[Span] = []
    spans_by_span_id: dict[str, Span] = {}
    spans_by_trace_id: dict[str, list[Span]] = {}

    def clear(self):
        self.spans = []
        self.spans_by_span_id = {}
        self.spans_by_trace_id = {}

    def remove_by_trace_id(self, trace_id: str):
        spans = self.spans_by_trace_id.get(trace_id, [])
        for span in spans:
            if span in self.spans:
                self.spans.remove(span)
                del self.spans_by_span_id[span.span_id]

        del self.spans_by_trace_id[trace_id]

    def add(self, span: Span):
        self.spans.append(span)
        self.spans_by_span_id[span.span_id] = span
        self.spans_by_trace_id.setdefault(span.trace_id, []).append(span)
        return span

    def has_span_id(self, span_id: str) -> bool:
        return span_id in self.spans_by_span_id

    def get_by_span_id(self, span_id: str) -> Span:
        return self.spans_by_span_id.get(span_id, None)

    def get_by_trace_id(self, trace_id: str) -> list[Span]:
        return list(self.spans_by_trace_id.get(trace_id, []))
