package nl.dflipse.fit.faultload;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import nl.dflipse.fit.faultload.faultmodes.FaultMode;

public class FaultloadSerializer {
  private final static ObjectMapper mapper = new ObjectMapper();

  public static String serializeJson(Faultload faultload) {
    var obj = mapper.createObjectNode();
    obj.set("faults", serializeFaults(faultload.getFaults()));
    obj.set("trace_id", stringNode(faultload.getTraceId()));

    return obj.toString();
  }

  public static JsonNode serializeFaults(Set<Fault> faults) {
    ArrayNode array = mapper.createArrayNode();

    for (Fault fault : faults) {
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

  public static JsonNode serializeFaultMode(FaultMode faultMode) {
    return mapper.valueToTree(faultMode);
  }

  public static JsonNode serializeFault(Fault fault) {
    return mapper.valueToTree(fault);
  }
}
