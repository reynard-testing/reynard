package io.github.delanoflipse.fit.suite.integration.micro;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record ActionComposition(String order, List<Object> actions) {
    public static ActionComposition Parallel(Object... actions) {
        return new ActionComposition("parallel", List.of(actions));
    }

    public static ActionComposition Sequential(Object... actions) {
        return new ActionComposition("sequential", List.of(actions));
    }

}
