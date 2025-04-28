import asyncio
import os
import requests
from flask import Flask, request

from lib.models import TraceReport, ResponseData, FaultUid, InjectionPoint
from lib.report_store import ReportStore

from frozendict import frozendict

app = Flask(__name__)

proxy_list: list[str] = [proxy for proxy in os.getenv(
    'PROXY_LIST', '').split(',') if proxy]
proxy_retry_count: int = int(os.getenv('PROXY_RETRY_COUNT', 3))
debug_flag_set = os.getenv('DEBUG', "true").lower() == "true"

# Local in-memory storage
report_store = ReportStore()
trace_ids: set[str] = set()

# --- DATA ENDPOINTS ---


@app.route('/v1/trace/<trace_id>', methods=['GET'])
def get_reports_by_trace_id(trace_id):
    reports = report_store.get_by_trace_id(trace_id)
    return {"reports": reports}, 200


# --- PROXY ENDPOINTS ---

def get_completed_events(parent_report: TraceReport) -> dict[str, int]:
    reports = report_store.get_by_trace_id(parent_report.trace_id)
    completed_events: dict[str, int] = {}

    for report in reports:
        if report == parent_report:
            continue

        if report.response is None:
            continue

        report_parent_stack = report.uid.stack[:-1]
        if report_parent_stack != parent_report.uid.stack:
            continue

        report_point = report.uid.stack[-1]
        report_partial = report_point.as_partial()
        report_key = report_partial.to_str()

        current_count = completed_events.get(report_key, 0)
        completed_events[report_key] = max(
            current_count, report_point.count)

    return completed_events


@app.route('/v1/proxy/get-parent-uid', methods=['POST'])
async def get_fault_uid():
    data = request.get_json()
    print("Received request for parent uid", data, flush=True)
    parent_id = data.get('parent_span_id')
    report = report_store.get_by_span_id(parent_id)

    if report is None:
        return [], 404

    completed_events = get_completed_events(report)

    return {"stack": report.uid.stack, 'completed_events': completed_events}, 200


@app.route('/v1/proxy/report', methods=['POST'])
async def report_span_id():
    data = request.get_json()

    trace_id = data.get('trace_id')
    span_id = data.get('span_id')
    uid = data.get('uid')
    injected_fault = data.get('injected_fault')
    is_initial = data.get('is_initial')
    response = data.get('response')
    concurrent_to = data.get('concurrent_to')

    if trace_id not in trace_ids:
        print(
            f"Trace id ({trace_id}) not registered anymore for uid {uid}", flush=True)
        return "Trace not registered", 404

    # Convert data to tracereport
    responseData = None
    if response:
        responseData = ResponseData(**response)

    stack = tuple([InjectionPoint(
        destination=fip['destination'],
        signature=fip['signature'],
        payload=fip['payload'],
        call_stack=frozendict(fip['call_stack']),
        count=fip['count'],
    ) for fip in uid.get('stack', [])])
    fault_uid = FaultUid(stack=stack)

    span_report = TraceReport(
        trace_id=trace_id,
        span_id=span_id,
        uid=fault_uid,
        injected_fault=injected_fault,
        is_initial=is_initial,
        concurrent_to=concurrent_to,
        response=responseData,
    )

    if report_store.has_fault_uid_for_trace(trace_id, fault_uid):
        existing_report = report_store.get_by_trace_and_fault_uid(
            trace_id, fault_uid)
        existing_report.response = responseData
        existing_report.injected_fault = injected_fault
        existing_report.concurrent_to = concurrent_to
        print("Updated reported span", span_report, flush=True)
    else:
        report_store.add(span_report)
        print("Added reported span", span_report, flush=True)

    return "OK", 200

# --- FAULTLOAD (UN)REGISTER ENDPOINTS ---


async def with_retry(func: callable, retries: int):
    last_error = None
    for i in range(retries):
        try:
            return await func()
        except Exception as e:
            last_error = e
            print(
                f"Failed to execute function with retry {i}: {e}", flush=True)
    raise last_error


async def register_faultload_at_proxy(proxy: str, payload):
    url = f"http://{proxy}/v1/faultload/register"
    response = requests.post(url, json=payload)

    if response.status_code != 200:
        print(
            f"Failed to register faultload at proxy {proxy}: {response.status_code} {response.text}", flush=True)
        raise Exception(
            f"Failed to register faultload at proxy {proxy}: {response.status_code} {response.text}")
    else:
        print(f"Registered faultload at proxy {proxy}", flush=True)


@app.route("/v1/faultload/register", methods=['POST'])
async def register_faultload():
    payload = request.get_json()
    trace_id = payload.get('trace_id')
    trace_ids.add(trace_id)

    print(f"Registering at: {proxy_list}", flush=True)
    tasks = [with_retry(lambda proxy=proxy: register_faultload_at_proxy(
        proxy, payload), proxy_retry_count) for proxy in proxy_list]
    await asyncio.gather(*tasks)

    print(f"Registered trace {trace_id}", flush=True)
    return "OK", 200


async def unregister_faultload_at_proxy(proxy: str, payload):
    url = f"http://{proxy}/v1/faultload/unregister"
    response = requests.post(url, json=payload)

    if response.status_code != 200:
        raise Exception(
            f"Failed to register faultload at proxy {proxy}: {response.status_code} {response.text}")


@app.route("/v1/faultload/unregister", methods=['POST'])
async def unregister_faultload():
    payload = request.get_json()
    trace_id = payload.get('trace_id')

    if not trace_id in trace_ids:
        return f"Trace id {trace_id} not known", 404

    tasks = [with_retry(lambda proxy=proxy: unregister_faultload_at_proxy(
        proxy, payload), proxy_retry_count) for proxy in proxy_list]
    await asyncio.gather(*tasks)

    trace_ids.remove(trace_id)
    if not debug_flag_set:
        try:
            report_store.remove_by_trace_id(trace_id)
        except:
            pass
    print(f"Unregistered trace {trace_id}", flush=True)
    return "OK", 200


@app.route("/v1/clear", methods=['GET'])
async def clear_all():
    trace_ids.clear()
    report_store.clear()

    return "OK", 200

if __name__ == '__main__':
    print("Registered proxies: ", proxy_list, flush=True)
    print("Debug?: ", debug_flag_set, flush=True)
    print("Starting orchestrator", flush=True)
    loop = asyncio.get_event_loop()
    loop.run_until_complete(app.run(host='0.0.0.0', port=5000))
