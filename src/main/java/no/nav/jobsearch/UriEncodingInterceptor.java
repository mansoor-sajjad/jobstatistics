package no.nav.jobsearch;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

/**
 * Interceptor for encoding URI components.
 * This interceptor is used to encode the URI query parameters
 * to ensure that the URI is correctly formatted.
 * It ensures that the '+' character is encoded as '%2B' in the URI.
 * The interceptor is added to the RestTemplate bean in the AppConfig class.
 */
public class UriEncodingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        HttpRequest encodedRequest = new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                URI uri = super.getURI();
                String escapedQuery = uri.getRawQuery().replace("+", "%2B");
                return UriComponentsBuilder.fromUri(uri)
                        .replaceQuery(escapedQuery)
                        .build(true).toUri();
            }
        };
        return execution.execute(encodedRequest, body);
    }
}