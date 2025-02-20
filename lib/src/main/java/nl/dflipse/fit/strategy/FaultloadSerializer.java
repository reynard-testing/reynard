package nl.dflipse.fit.strategy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class FaultloadSerializer {
  private final static ObjectMapper mapper = new ObjectMapper();

  public static String serialize(List<Fault> faultload) {
    String jsonData = serializeJson(faultload);
    String encodedJson = URLEncoder.encode(jsonData, StandardCharsets.UTF_8);
    return encodedJson;
  }

  public static String serializeJson(List<Fault> faultload) {
    ArrayNode array = mapper.createArrayNode();

    for (Fault fault : faultload) {
      array.add(serializeFault(fault));
    }

    return array.toString();
  }

  public static ArrayNode serializeFault(Fault fault) {
    // [spanId, faultType, ...args]
    ArrayNode array = mapper.createArrayNode();

    array.add(fault.spanUid);
    array.add(fault.faultMode.getType());
    for (String arg : fault.faultMode.getArgs()) {
      array.add(arg);
    }

    return array;

  }
}
