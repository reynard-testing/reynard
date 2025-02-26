package nl.dflipse.fit.util;

public class HttpResponse {
    public final String body;
    public final int statusCode;

    public HttpResponse(String body, int statusCode) {
        this.body = body;
        this.statusCode = statusCode;
    }
}
