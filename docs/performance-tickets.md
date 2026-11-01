# Performance tickets

The tickets are ordered by expected gain and implementation effort (S/M/L). Profile shares come from a JFR profile of `StrategyBenchmark` (1,500 tests, width 6, depth 4), taken before the 1a/1b fixes. They show each item's share of total run time.

Measure each change with the benchmark:

```sh
cd library
mvn test -Dtest=StrategyBenchmark -Dbench.width=8 -Dbench.depth=4 -Dbench.tests=4000
```

Compare `search ms`. The `explored` count and the fingerprint must stay the same; if they change, the change altered which faultloads get explored. Current numbers, after the pruner early-exit and expected-behaviour cache fixes: width 6 / 1,500 tests ≈ 11–13 s search time; width 8 / 4,000 tests ≈ 109 s (baseline 152 s). Expect ±15% variation between runs.

| # | Ticket | Gain | Effort | Description | Evidence / location |
|---|---|---|---|---|---|
| 1 | Skip re-pruning kept faultloads | High | S | `generateAndPruneTillNext` prunes a faultload again after the generator has already pruned it. | ~11% of run; [StrategyRunner.java](../library/src/main/java/dev/reynard/junit/strategy/StrategyRunner.java) (`TODO: generator also prunes`) |
| 2 | Index past results in `DynamicReductionPruner` | High | M | Each candidate is checked against every past result, so the cost grows with the number of tests run. Index past results by behaviour so only relevant ones are checked. | ~32% of run; [DynamicReductionPruner.java](../library/src/main/java/dev/reynard/junit/strategy/components/pruners/DynamicReductionPruner.java) |
| 3 | Index upstream responses by cause | High | M | `findUpstream` does a linear subset check over causes on every model unfold. | ~23% of run; [ImplicationsStore.java](../library/src/main/java/dev/reynard/junit/strategy/store/ImplicationsStore.java), `ImplicationsModel.getUpstream` |
| 4 | Make `FailureMode.equals` cheap | Medium | S | Intern failure modes so equality is an identity check before falling back to comparing argument lists. | ~20% of run, inside `Behaviour.matches` |
| 5 | Aggregate `StrategyStatistics` timings per tag | Medium | S | One timing entry is stored per pruner per candidate, so memory grows without bound on large searches. Keep a sum and count per tag instead. | [StrategyStatistics.java](../library/src/main/java/dev/reynard/junit/strategy/StrategyStatistics.java) `registerTime` |
| 6 | Remove the controller's global `reports` slice | Medium | S | Every report upsert and removal scans all live reports across all traces. The per-trace maps already cover every lookup. | [report_store.go](../instrumentation/controller/store/report_store.go) `Replace`, `removeByTraceIdAndSpanId` |
| 7 | Fix the controller's unregister request | Correctness | S | Unregister posts to the proxy's `/v1/faultload/register` instead of `/unregister`, so proxies never free a trace's faults or in-flight state. | [register_faultload.go:141](../instrumentation/controller/endpoints/register_faultload.go#L141) |
| 8 | Cache `TreeNode` hash | Low–Medium | S | `hashCode` builds a new `LinkedHashSet` on every call, and the record hashes of `Fault` and `FaultUid` are recomputed deeply each time. | ~2–4% of run; [TreeNode.java](../library/src/main/java/dev/reynard/junit/strategy/components/generators/TreeNode.java) |
| 9 | Use an index map when building the tree report | Low | S | `buildTreeReport` calls `visited.indexOf` for every node, which is O(n²) at the end of a run. | [DynamicExplorationGenerator.java](../library/src/main/java/dev/reynard/junit/strategy/components/generators/DynamicExplorationGenerator.java) |
| 10 | Run cheap pruners first | Low | S | Order pruners from cheapest to most expensive (size, no-impact, then the model-based ones), so the early exit skips the expensive ones. | [FiTestExtension.java](../library/src/main/java/dev/reynard/junit/FiTestExtension.java) |
| 11 | Index redundant subsets by element | Medium at scale | M | Redundant fault and point subsets are scanned linearly for each candidate. Only subsets that share an element with the candidate can match. | [DynamicAnalysisStore.java](../library/src/main/java/dev/reynard/junit/strategy/store/DynamicAnalysisStore.java) `hasFaultSubset`, `hasFaultUidSubset` |
| 12 | Parent-pointer tree nodes; keep report-only structures only when needed | Memory | M | Each node copies its full fault list, and `expansionTree` and `prunedNodes` exist only for the report. Store each node as a parent pointer plus one fault, and keep the report structures only when an output dir is set. | [DynamicExplorationGenerator.java](../library/src/main/java/dev/reynard/junit/strategy/components/generators/DynamicExplorationGenerator.java) |
| 13 | Faults as int IDs, faultloads as `BitSet`s | High at scale | L | Map each distinct `Fault` to an int ID, so set equality, hashing and subset checks become word-level operations. | Affects the generator, stores and pruners |
