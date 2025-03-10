package nl.dflipse.fit;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.net.URIBuilder;

import static org.junit.Assert.assertEquals;

import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.OmissionFault;
import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.instrument.controller.RemoteController;
import nl.dflipse.fit.util.HttpResponse;
import nl.dflipse.fit.util.HttpResponseHandler;

/**
 * FI test the app
 */
public class OTELTest {
    private static final RemoteController controller = new RemoteController("http://localhost:5000");

    public static FaultController getController() {
        return controller;
    }

    @FiTest
    public void testShipping(Faultload faultload) throws URISyntaxException {
        String port = "8080";
        // String port = "64839";

        String endpoint = "http://localhost:" + port
                + "/api/shipping";
        var builder = new URIBuilder(endpoint);
        builder.addParameter("itemList",
                "[{\"productId\":\"66VCHSJNUP\",\"quantity\":1}]");
        builder.addParameter("currencyCode", "USD");
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
        String inspectUrl = controller.collectorUrl + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

        boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
        int expectedResponse = containsError ? 500 : 200;
        int actualResponse = response.statusCode;
        assertEquals(expectedResponse, actualResponse);
    }

    @FiTest
    public void testRecommendations(Faultload faultload) throws URISyntaxException, IOException {
        String port = "8080";
        // String port = "64839";

        String endpoint = "http://localhost:" + port
                + "/api/recommendations";
        var builder = new URIBuilder(endpoint);
        builder.addParameter("productIds", "");
        builder.addParameter("currencyCode", "USD");

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();
        var url = builder.build();

        Response res = Request.get(url)
                .addHeader("Accept", "application/json")
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .execute();
        HttpResponse response = res.handleResponse(new HttpResponseHandler());

        String inspectUrl = controller.collectorUrl + "/v1/trace/" + faultload.getTraceId();
        String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

        boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
        int expectedResponse = containsError ? 500 : 200;
        int actualResponse = response.statusCode;
        assertEquals(expectedResponse, actualResponse);
    }
}
