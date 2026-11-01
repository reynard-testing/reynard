import { z } from 'zod';

/** Options of one exploration, see ExplorerConfig in the explorer. Absent fields keep their default. */
export type ExplorerConfig = {
  readonly name?: string;
  readonly outputDir?: string;
  readonly maskPayload?: boolean;
  readonly withPredecessors?: boolean;
  readonly hashBody?: boolean;
  readonly logHeaders?: boolean;
  readonly failStop?: boolean;
  readonly optimizeForRetries?: boolean;
  readonly optimizeForImpactless?: boolean;
  readonly checkAllPruners?: boolean;
  readonly maxTestCases?: number;
  readonly maxTimeS?: number;
  readonly maxFaultloadSize?: number;
  readonly initialGetTraceDelay?: number;
  readonly pointOrder?:
    | 'DEPTH_FIRST_PRE_ORDER'
    | 'DEPTH_FIRST_REVERSE_PRE_ORDER'
    | 'DEPTH_FIRST_POST_ORDER'
    | 'DEPTH_FIRST_REVERSE_POST_ORDER'
    | 'BREADTH_FIRST'
    | 'BREADTH_FIRST_REVERSE'
    | 'RANDOM';
  readonly depthFirstSearchOrder?: boolean;
};

const sessionSchema = z.object({ session_id: z.string() });

const runSchema = z
  .object({
    run_id: z.string(),
    headers: z.object({ traceparent: z.string(), tracestate: z.string() }),
  })
  .transform(({ run_id, headers }) => ({ runId: run_id, headers }));

const reportSchema = z.object({
  name: z.string(),
  done: z.boolean(),
  runs: z.number(),
  lost: z.number(),
  failures: z.array(z.string()),
});

/** One run of the test. Send `headers` with the request under test, and only with that request. */
export type Run = z.infer<typeof runSchema>;
export type Report = z.infer<typeof reportSchema>;

export type ExploreOptions = {
  /** Base URL of the explorer HTTP API, e.g. `http://localhost:7070` */
  readonly explorerUrl: string;
  readonly config?: ExplorerConfig;
  /** Performs the interaction and asserts its invariant. A thrown error marks the run as failed. */
  readonly test: (run: Run) => Promise<void> | void;
};

/** The explorer API returned an unexpected response. */
export class ExplorerError extends Error {
  override name = 'ExplorerError';
}

/** One or more faultloads made the test fail. */
export class InvariantViolatedError extends Error {
  override name = 'InvariantViolatedError';

  constructor(
    readonly report: Report,
    options?: ErrorOptions,
  ) {
    super(
      `${report.failures.length} of ${report.runs} faultloads violated the invariant:\n` +
        report.failures.map((failure) => `  ${failure}`).join('\n'),
      options,
    );
  }
}

/** Runs `test` once per faultload the explorer plans, and throws an InvariantViolatedError if any run failed. */
export async function explore({ explorerUrl, config = {}, test }: ExploreOptions): Promise<Report> {
  const api = new URL('/v1/sessions/', explorerUrl);
  const { session_id } = sessionSchema.parse(await call(api, { method: 'POST', body: config }));
  const session = new URL(`${encodeURIComponent(session_id)}/`, api);

  let firstFailure: unknown;
  for (;;) {
    const next = await call(new URL('next', session), { method: 'POST' });
    if (next === undefined) {
      break;
    }

    const run = runSchema.parse(next);
    const verdict = await Promise.resolve()
      .then(() => test(run))
      .then(
        () => ({ passed: true }),
        (error: unknown) => {
          firstFailure ??= error;
          return { passed: false, message: String(error) };
        },
      );
    await call(new URL(`runs/${encodeURIComponent(run.runId)}`, session), { method: 'POST', body: verdict });
  }

  const report = reportSchema.parse(await call(new URL('report', session), { method: 'GET' }));
  if (report.failures.length > 0) {
    throw new InvariantViolatedError(report, { cause: firstFailure });
  }
  return report;
}

/** Calls the explorer API, returns the parsed JSON body or undefined on 204. */
async function call(url: URL, { method, body }: { method: string; body?: unknown }): Promise<unknown> {
  const response = await fetch(url, {
    method,
    headers: { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }).catch((error: unknown) => {
    throw new ExplorerError(`Cannot reach the explorer at ${url}`, { cause: error });
  });

  if (!response.ok) {
    throw new ExplorerError(`${method} ${url} returned ${response.status}: ${await response.text()}`);
  }
  return response.status === 204 ? undefined : response.json();
}
