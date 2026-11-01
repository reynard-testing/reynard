import { afterAll, beforeAll, describe, expect, test } from 'vitest';
import { explore, InvariantViolatedError } from '../src/explore.js';
import { startSystem, type InstrumentedSystem } from './instrumented-system.js';

describe('explore', () => {
  const response = 'response of b';
  const fallbackValue = 'fallback of a';
  let system: InstrumentedSystem;

  beforeAll(async () => {
    system = await startSystem({ fallbackValue, response });
  });

  afterAll(async () => {
    await system?.stop();
  });

  test('resolves with the report when every faultload keeps the invariant', async () => {
    // Given - a falls back when b fails
    // When - we explore whether a always succeeds
    const report = await explore({
      explorerUrl: system.explorerUrl,
      config: { name: 'a succeeds' },
      test: async ({ headers }) => {
        const result = await fetch(system.entryUrl, { headers });
        expect(result.status).toBe(200);
      },
    });

    // Then - the fault in b was explored without violations
    expect(report.done).toBe(true);
    expect(report.runs).toBeGreaterThan(1);
  });

  test('throws when a faultload violates the invariant', async () => {
    // Given - a falls back when b fails
    // When - we explore whether a always returns the response of b
    const exploration = explore({
      explorerUrl: system.explorerUrl,
      config: { name: 'a returns b' },
      test: async ({ headers }) => {
        const result = await fetch(system.entryUrl, { headers });
        expect(await result.text()).toContain(response);
      },
    });

    // Then - the fault in b is reported
    await expect(exploration).rejects.toBeInstanceOf(InvariantViolatedError);
  });
});
