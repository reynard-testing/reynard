package io.github.delanoflipse.fit.suite.suites.micro;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public record ServerAction(String endpoint, String method, String body, Object onFailure) {
    public ServerAction(String endpoint) {
        this(endpoint, "GET", null, null);
    }

    public ServerAction(String endpoint, Object onFailure) {
        this(endpoint, "GET", null, onFailure);
    }
}
