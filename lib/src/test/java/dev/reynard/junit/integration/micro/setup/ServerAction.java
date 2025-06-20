package dev.reynard.junit.integration.micro.setup;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public record ServerAction(String endpoint, String method, String body, Object onFailure) {
    public ServerAction(String endpoint) {
        this(endpoint, "GET", null, null);
    }

    // Note: onFailure can be a ActionComposition, ServerAction, or a String
    public ServerAction(String endpoint, Object onFailure) {
        this(endpoint, "GET", null, onFailure);
    }
}
