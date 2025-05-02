package io.github.delanoflipse.fit.suite.strategy.components.analyzers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.strategy.FaultloadResult;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackContext;
import io.github.delanoflipse.fit.suite.strategy.components.FeedbackHandler;
import io.github.delanoflipse.fit.suite.strategy.util.Lists;
import io.github.delanoflipse.fit.suite.trace.tree.TraceReport;

public class ConditionalPointDetector implements FeedbackHandler {
    private final Logger logger = LoggerFactory.getLogger(ConditionalPointDetector.class);

    @Override
    public void handleFeedback(FaultloadResult result, FeedbackContext context) {
        Set<FaultUid> pointsInTrace = result.trace.getFaultUids();

        // -- New points in failure paths --
        Set<FaultUid> expectedPoints = context.getExpectedPoints(result.trace.getInjectedFaults());
        Set<Behaviour> expectedBehaviours = context.getExpectedBehaviours(result.trace.getInjectedFaults());

        // Analyse new paths that were not expected
        // but whose parent is expected
        // (this excludes nested points)
        List<FaultUid> appearedPoints = Lists.difference(pointsInTrace, List.copyOf(expectedPoints));
        List<TraceReport> appearedReports = result.trace.getReports(appearedPoints);
        var rootAppeared = appearedReports.stream()
                .filter(f -> expectedPoints.contains(result.trace.getParent(f.faultUid)))
                .toList();

        // group by their parent, as there might be multiple appeared points
        // for the same cause
        var rootByParent = rootAppeared.stream()
                .collect(Collectors.groupingBy(result.trace::getParent));

        // Report newly found points
        for (var entry : rootByParent.entrySet()) {
            handleAppeared(result, context, entry.getKey(), entry.getValue(), expectedBehaviours);
        }
    }

    protected void handleAppeared(FaultloadResult result, FeedbackContext context, TraceReport parent,
            List<TraceReport> newPoints,
            Set<Behaviour> expectedBehaviours) {

        // determine which reports can cause the new point
        List<TraceReport> parentalCauses = result.trace.getChildren(parent);
        List<Behaviour> directCauses = parentalCauses.stream()
                .map(TraceReport::getBehaviour)
                .filter(x -> x.isFault())
                .collect(Collectors.toList());

        // determine which reports are the actual causes
        // (i.e., the ones that are injected)
        // As we will visit starting at the root causes
        Set<Fault> actualCauses = new LinkedHashSet<>();
        for (var fault : result.trace.getDecendants(parent)) {
            if (fault.injectedFault != null) {
                actualCauses.add(fault.injectedFault);
            }
        }

        for (var point : newPoints) {
            boolean isNew = context.reportPreconditionOfFaultUid(directCauses, point.faultUid);
            if (isNew) {
                logger.info("Found conditional point: {} given {}", point.faultUid, directCauses);
            }
        }
    }
}
