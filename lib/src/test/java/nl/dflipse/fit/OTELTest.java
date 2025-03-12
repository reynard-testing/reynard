package nl.dflipse.fit;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ContentType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.net.URIBuilder;

import static org.junit.Assert.assertEquals;

import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.OmissionFault;
import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.instrument.controller.RemoteController;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.util.HttpResponse;
import nl.dflipse.fit.util.HttpResponseHandler;

/**
 * FI test the app
 */
public class OTELTest {
    private static final RemoteController controller = new RemoteController("http://localhost:5000");

    private static final int PORT = 8080;
    private static final String USER_ID = "6894131c-cab3-4dfe-a5f1-a7086b9f7376";
    private static final String CURRENCY = "USD";
    private static final ObjectMapper mapper = new ObjectMapper();

    public static FaultController getController() {
        return controller;
    }

    private void addItemToCart() {
        var obj = mapper.createObjectNode();
        var itemNode = mapper.createObjectNode();
        itemNode.put("productId", "0PUK6V6EV0");
        itemNode.put("quantity", 1);
        obj.set("item", itemNode);
        obj.put("userId", USER_ID);

        String endpoint = "http://localhost:" + PORT + "/api/cart?currencyCode=" + CURRENCY;
        try {
            Request.post(new URI(endpoint))
                    .addHeader("Content-Type", "application/json")
                    .bodyByteArray(mapper.writeValueAsBytes(obj), ContentType.APPLICATION_JSON)
                    .execute();
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @FiTest(maskPayload = true)
    public void testShipping(TrackedFaultload faultload) throws URISyntaxException {
        String port = "8080";
        // String port = "64839";

        String endpoint = "http://localhost:" + port
                + "/api/shipping";
        var builder = new URIBuilder(endpoint);
        builder.addParameter("itemList",
                "[{\"productId\":\"66VCHSJNUP\",\"quantity\":1}]");
        builder.addParameter("currencyCode", CURRENCY);
        builder.addParameter("address",
                "{\"streetAddress\":\"1600+Amphitheatre+Parkway\",\"city\":\"Mountain+View\",\"state\":\"CA\",\"country\":\"United+States\",\"zipCode\":\"94043\"}");

        String fullUrl = builder.toString();
        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();
        var url = builder.build();
        Response res;
        HttpResponse response;
        try {
            res = Request.get(url)
                    .addHeader("Accept", "application/json")
                    .addHeader("traceparent", traceparent)
                    .addHeader("tracestate", tracestate)
                    .execute();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        try {
            response = res.handleResponse(new HttpResponseHandler());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

        boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
        int expectedResponse = containsError ? 500 : 200;
        int actualResponse = response.statusCode;
        assertEquals(expectedResponse, actualResponse);
    }

    @FiTest(maskPayload = true)
    public void testRecommendations(TrackedFaultload faultload) throws URISyntaxException, IOException {
        String port = "8080";
        // String port = "64839";

        String endpoint = "http://localhost:" + port
                + "/api/recommendations";
        var builder = new URIBuilder(endpoint);
        builder.addParameter("productIds", "");
        builder.addParameter("currencyCode", CURRENCY);

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();
        var url = builder.build();

        Response res = Request.get(url)
                .addHeader("Accept", "application/json")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .execute();
        HttpResponse response = res.handleResponse(new HttpResponseHandler());

        String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

        boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
        int expectedResponse = containsError ? 500 : 200;
        int actualResponse = response.statusCode;
        assertEquals(expectedResponse, actualResponse);
    }

    @FiTest(maskPayload = true)
    public void testCheckout(TrackedFaultload faultload) throws URISyntaxException, IOException {
        addItemToCart();

        String endpoint = "http://localhost:" + PORT + "/api/checkout";
        var builder = new URIBuilder(endpoint);
        builder.addParameter("currencyCode", CURRENCY);
        String payload = "{\"userId\":\"" + USER_ID
                + "\",\"email\":\"someone@example.com\",\"address\":{\"streetAddress\":\"1600 Amphitheatre Parkway\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"userCurrency\":\""
                + CURRENCY
                + "\",\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}";

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();
        var url = builder.build();

        Response res = Request.post(url)
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .bodyString(payload, ContentType.APPLICATION_JSON)
                .execute();
        HttpResponse response = res.handleResponse(new HttpResponseHandler());

        String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

        boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
        int expectedResponse = containsError ? 500 : 200;
        int actualResponse = response.statusCode;
        // assertEquals(expectedResponse, actualResponse);
    }
}
