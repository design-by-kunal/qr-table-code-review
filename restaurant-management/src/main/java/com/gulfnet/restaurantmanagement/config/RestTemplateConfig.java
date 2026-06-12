package com.gulfnet.restaurantmanagement.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(RestTemplateConfig.class);
    private static final int MAX_LOG_BODY_CHARS = 4000;

    /**
     * Creates and configures a RestTemplate bean for HTTP client operations.
     * Configures timeouts, buffering for request/response logging, and ensures
     * FormHttpMessageConverter is prioritized for form-encoded data (required for Omise API).
     * Adds a logging interceptor for Omise API calls.
     * Uses @LoadBalanced to enable Eureka service discovery for inter-service communication.
     *
     * @return configured RestTemplate instance with Omise logging interceptor and load balancing
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return createOmiseReadyRestTemplate();
    }

    /**
     * Creates and configures a RestTemplate bean specifically for external API calls (e.g., Omise).
     * This RestTemplate does NOT use @LoadBalanced, so it makes direct HTTP calls without
     * going through Spring Cloud LoadBalancer service discovery.
     * 
     * This is required for external APIs like Omise that are not registered in the service registry.
     * 
     * @return configured RestTemplate instance for external API calls (without load balancing)
     */
    @Bean(name = "omiseRestTemplate")
    public RestTemplate omiseRestTemplate() {
        return createOmiseReadyRestTemplate();
    }

    /**
     * Creates and configures a RestTemplate bean specifically for GMO Link Plus calls.
     * Having a dedicated bean avoids coupling GMO configuration to Omise-specific settings
     * and makes it easier to tune timeouts, interceptors, and TLS in the future.
     *
     * Currently shares the same base configuration as {@link #omiseRestTemplate()}, but uses
     * a separate bean name so GMO-specific customizations can be applied independently.
     */
    @Bean(name = "gmoRestTemplate")
    public RestTemplate gmoRestTemplate() {
        return createOmiseReadyRestTemplate();
    }

    /**
     * Shared factory for {@link #restTemplate()} and {@link #omiseRestTemplate()}: timeouts, buffering,
     * form converter ordering, and Omise request/response logging.
     */
    private RestTemplate createOmiseReadyRestTemplate() {
        SimpleClientHttpRequestFactory baseFactory = new SimpleClientHttpRequestFactory();
        baseFactory.setConnectTimeout(5000); // 5 seconds
        baseFactory.setReadTimeout(10000); // 10 seconds

        ClientHttpRequestFactory factory = new BufferingClientHttpRequestFactory(baseFactory);

        RestTemplate restTemplate = new RestTemplate(factory);

        List<HttpMessageConverter<?>> messageConverters = restTemplate.getMessageConverters();
        messageConverters.removeIf(FormHttpMessageConverter.class::isInstance);
        messageConverters.add(0, new FormHttpMessageConverter());

        restTemplate.getInterceptors().add(omiseLoggingInterceptor());
        return restTemplate;
    }

    /**
     * Creates a logging interceptor that logs detailed request and response information
     * for Omise API calls only. Redacts authorization headers for security.
     * Logs request method, URI, headers, and body (truncated to max length).
     * Logs response status, headers, and body (truncated to max length).
     *
     * @return ClientHttpRequestInterceptor that logs Omise API calls
     */
    private ClientHttpRequestInterceptor omiseLoggingInterceptor() {
        return (HttpRequest request, byte[] body, ClientHttpRequestExecution execution) -> {
            String host = request.getURI() != null ? request.getURI().getHost() : null;
            boolean isOmise = host != null && host.equalsIgnoreCase("api.omise.co");

            if (!isOmise) {
                return execution.execute(request, body);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.putAll(request.getHeaders());
            if (headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                headers.set(HttpHeaders.AUTHORIZATION, "REDACTED");
            }

            String bodyStr = body == null ? "" : new String(body, StandardCharsets.UTF_8);
            log.info("🧾 OMISE OUTGOING REQUEST → {} {}", request.getMethod(), request.getURI());
            log.info("🧾 OMISE OUTGOING HEADERS → {}", headers);
            log.info("🧾 OMISE OUTGOING BODY → {}", truncate(bodyStr, MAX_LOG_BODY_CHARS));

            ClientHttpResponse response = execution.execute(request, body);
            String respBody = "";
            try {
                respBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // ignore
            }

            log.info("🧾 OMISE INCOMING RESPONSE ← HTTP {}", response.getStatusCode().value());
            log.info("🧾 OMISE INCOMING HEADERS ← {}", response.getHeaders());
            log.info("🧾 OMISE INCOMING BODY ← {}", truncate(respBody, MAX_LOG_BODY_CHARS));

            return response;
        };
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) return null;
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "...(truncated)";
    }
}
