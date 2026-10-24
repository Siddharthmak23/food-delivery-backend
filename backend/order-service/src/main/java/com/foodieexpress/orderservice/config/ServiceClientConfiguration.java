package com.foodieexpress.orderservice.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ServiceClientConfiguration {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Qualifier("userServiceRestClient")
    RestClient userServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.clients.user-service.base-url}") String baseUrl,
            @Value("${app.clients.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${app.clients.read-timeout:PT3S}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    @Qualifier("restaurantServiceRestClient")
    RestClient restaurantServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.clients.restaurant-service.base-url}") String baseUrl,
            @Value("${app.clients.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${app.clients.read-timeout:PT3S}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    @Qualifier("deliveryServiceRestClient")
    RestClient deliveryServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.clients.delivery-service.base-url}") String baseUrl,
            @Value("${app.clients.connect-timeout:PT2S}") Duration connectTimeout,
            @Value("${app.clients.read-timeout:PT3S}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    private RestClient build(
            RestClient.Builder builder, String baseUrl, Duration connectTimeout, Duration readTimeout) {
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalStateException("Service client timeouts must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
