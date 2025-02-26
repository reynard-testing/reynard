package nl.dflipse.fit.util;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;

public class HttpResponseHandler implements HttpClientResponseHandler<HttpResponse> {

    @Override
    public HttpResponse handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
        int statusCode = response.getCode();
        var entity = response.getEntity();
        String body = entity != null ? EntityUtils.toString(entity, "UTF-8") : "";

        return new HttpResponse(body, statusCode);
    }

}
