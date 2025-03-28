package nl.dflipse.fit;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.FaultUid;
import nl.dflipse.fit.faultload.Faultload;
import nl.dflipse.fit.faultload.faultmodes.ErrorFault;
import nl.dflipse.fit.faultload.faultmodes.FaultMode;
import nl.dflipse.fit.faultload.faultmodes.HttpError;
import nl.dflipse.fit.faultload.faultmodes.OmissionFault;
import nl.dflipse.fit.instrument.FaultController;
import nl.dflipse.fit.instrument.controller.RemoteController;
import nl.dflipse.fit.strategy.FaultloadResult;
import nl.dflipse.fit.strategy.TrackedFaultload;
import nl.dflipse.fit.strategy.util.TraceAnalysis;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * FI test the app
 */
public class OTELTest {
    private static final RemoteController controller = new RemoteController("http://localhost:5000");

    private static final int PORT = 8080;
    private static final String USER_ID = "6894131c-cab3-4dfe-a5f1-a7086b9f7376";
    private static final String CURRENCY = "USD";
    private static final ObjectMapper mapper = new ObjectMapper();

    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    public static final MediaType JSON = MediaType.get("application/json");

    public static FaultController getController() {
        return controller;
    }

    private void addItemToCart() throws IOException {
        var obj = mapper.createObjectNode();
        var itemNode = mapper.createObjectNode();
        itemNode.put("productId", "0PUK6V6EV0");
        itemNode.put("quantity", 1);
        obj.set("item", itemNode);
        obj.put("userId", USER_ID);

        String endpoint = "http://localhost:" + PORT + "/api/cart?currencyCode=" + CURRENCY;

        RequestBody body = RequestBody.create(mapper.writeValueAsBytes(obj), JSON);
        Request request = new Request.Builder()
                // .addHeader("Content-Type", "application/json")
                .url(endpoint)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return;
        }
    }

    @FiTest(maskPayload = true)
    public void testShipping(TrackedFaultload faultload) throws IOException {

        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost")
                .port(PORT)
                .addPathSegments("api/shipping")
                .addQueryParameter("itemList",
                        "[{\"productId\":\"66VCHSJNUP\",\"quantity\":1}]")
                .addQueryParameter("currencyCode", CURRENCY)
                .addQueryParameter("address",
                        "{\"streetAddress\":\"1600+Amphitheatre+Parkway\",\"city\":\"Mountain+View\",\"state\":\"CA\",\"country\":\"United+States\",\"zipCode\":\"94043\"}")
                .build();

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

            boolean containsError = faultload.hasFaultMode(ErrorFault.FAULT_TYPE, OmissionFault.FAULT_TYPE);
            int expectedResponse = containsError ? 500 : 200;
            int actualResponse = response.code();
            assertEquals(expectedResponse, actualResponse);
        }
    }

    @FiTest(maskPayload = true)
    public void testRecommendations(TrackedFaultload faultload)
            throws IOException {

        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost")
                .port(PORT)
                .addPathSegments("api/recommendations")
                .addQueryParameter("productIds", "")
                .addQueryParameter("currencyCode", CURRENCY)
                .build();

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

            TraceAnalysis result = getController().getTrace(faultload);
            boolean injectedFaults = !result.getInjectedFaults().isEmpty();

            if (injectedFaults) {
                assert (response.code() >= 500);
                assert (response.code() < 600);
            } else {
                assertEquals(200, response.code());
            }
        }
    }

    @FiTest(maskPayload = true)
    public void testCheckout(TrackedFaultload faultload) throws IOException {
        addItemToCart();

        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host("localhost")
                .port(PORT)
                .addPathSegments("api/checkout")
                .addQueryParameter("productIds", "")
                .addQueryParameter("currencyCode", CURRENCY)
                .build();

        String payload = "{\"userId\":\"" + USER_ID
                + "\",\"email\":\"someone@example.com\",\"address\":{\"streetAddress\":\"1600 Amphitheatre Parkway\",\"state\":\"CA\",\"country\":\"United States\",\"city\":\"Mountain View\",\"zipCode\":\"94043\"},\"userCurrency\":\""
                + CURRENCY
                + "\",\"creditCard\":{\"creditCardCvv\":672,\"creditCardExpirationMonth\":1,\"creditCardExpirationYear\":2030,\"creditCardNumber\":\"4432-8015-6152-0454\"}}";
        RequestBody body = RequestBody.create(payload, JSON);

        var traceparent = faultload.getTraceParent().toString();
        var tracestate = faultload.getTraceState().toString();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("traceparent", traceparent)
                .addHeader("tracestate", tracestate)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String inspectUrl = controller.apiHost + "/v1/trace/" + faultload.getTraceId();
            String traceUrl = "http://localhost:16686/trace/" + faultload.getTraceId();

            var traceData = controller.getTrace(faultload);

            boolean containsError = traceData.hasFaultMode(ErrorFault.FAULT_TYPE,
                    OmissionFault.FAULT_TYPE);
            int actualResponse = response.code();

            // if (containsError) {
            // assertTrue(actualResponse >= 500 && actualResponse < 600);
            // } else {
            // assertEquals(200, actualResponse);
            // }
        }

    }

    @Test
    public void testCounterExample() throws URISyntaxException, IOException {
        // checkout>email:/send_order_confirmation(*)#0(HTTP_ERROR
        // [502]),checkout>cart:oteldemo.CartService/EmptyCart(*)#0(HTTP_ERROR
        // [504]),frontend>product-catalog:oteldemo.ProductCatalogService/GetProduct(*)#0(HTTP_ERROR
        // [502])
        FaultUid fid1 = new FaultUid("checkout", "email", "/send_order_confirmation", "*", 0);
        FaultUid fid2 = new FaultUid("checkout", "cart", "oteldemo.CartService/EmptyCart", "*", 0);
        FaultUid fid3 = new FaultUid("frontend", "product-catalog", "oteldemo.ProductCatalogService/GetProduct",
                "*",
                0);

        FaultMode mode1 = ErrorFault.fromError(HttpError.BAD_GATEWAY);
        FaultMode mode2 = ErrorFault.fromError(HttpError.GATEWAY_TIMEOUT);

        Fault f1 = new Fault(fid1, mode1);
        Fault f2 = new Fault(fid2, mode2);
        Fault f3 = new Fault(fid3, mode1);

        Faultload faultload = new Faultload(Set.of(f1, f2, f3));
        TrackedFaultload trackedFaultload = new TrackedFaultload(faultload)
                .withMaskPayload();

        testCheckout(trackedFaultload);
    }
}
