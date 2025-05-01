package io.github.delanoflipse.fit.trace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraceState {
    private final Logger logger = LoggerFactory.getLogger(TraceState.class);
    private Map<String, String> state = new HashMap<String, String>();

    public TraceState(String header) {
        if (header.length() == 0) {
            return;
        }

        String[] parts = header.split(",");
        if (parts.length == 0) {
            return;
        }

        for (String part : parts) {
            String[] keyValue = part.split("=");
            if (keyValue.length == 2) {
                this.state.put(keyValue[0], keyValue[1]);
            } else {
                throw new IllegalArgumentException("Invalid tracestate header");
            }
        }
    }

    public TraceState() {
    }

    public void set(String key, String value) {
        this.state.put(key, value);
    }

    public void set(String key, int value) {
        this.state.put(key, String.valueOf(value));
    }

    public String get(String key) {
        return this.state.get(key);
    }

    public void unset(String key) {
        this.state.remove(key);
    }

    public String toString() {
        List<String> values = new ArrayList<String>();
        for (var entry : this.state.entrySet()) {
            String value = entry.getKey() + "=" + entry.getValue();
            values.add(value);
        }

        String result = String.join(",", values);

        if (result.length() > 256) {
            logger.warn("TraceState header too long: " + result.length());
        }

        return result;
    }
}
