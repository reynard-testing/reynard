package dev.reynard.junit.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import dev.reynard.junit.faultload.Fault;
import dev.reynard.junit.faultload.FaultUid;
import dev.reynard.junit.faultload.Faultload;
import dev.reynard.junit.faultload.modes.FailureMode;

public class TrackedFaultloadSerializer {
  private final static ObjectMapper mapper = new ObjectMapper();

  public static String serializeJson(TrackedFaultload faultload) {
    var obj = mapper.createObjectNode();
    obj.set("faults", serializeFaults(faultload.getFaultload()));
    obj.set("trace_id", stringNode(faultload.getTraceId()));

    return obj.toString();
  }

  public static JsonNode serializeFaults(Faultload faultload) {
    ArrayNode array = mapper.createArrayNode();

    for (Fault fault : faultload.faultSet()) {
      array.add(serializeFault(fault));
    }

    return array;
  }

  public static JsonNode stringNode(String value) {
    return mapper.createObjectNode().textNode(value);
  }

  public static JsonNode serializeFaultUid(FaultUid faultUid) {
    return mapper.valueToTree(faultUid);
  }

  public static JsonNode serializeFaultMode(FailureMode faultMode) {
    return mapper.valueToTree(faultMode);
  }

  public static JsonNode serializeFault(Fault fault) {
    return mapper.valueToTree(fault);
  }
}
