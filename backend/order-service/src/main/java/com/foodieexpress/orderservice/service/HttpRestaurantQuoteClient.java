package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.RestaurantQuoteRequest;
import com.foodieexpress.orderservice.dto.RestaurantQuoteResponse;
import com.foodieexpress.orderservice.exception.DependencyUnavailableException;
import com.foodieexpress.orderservice.exception.ResourceNotFoundException;
import com.foodieexpress.orderservice.exception.UpstreamResponseException;
import com.foodieexpress.orderservice.security.InternalRestaurantTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpRestaurantQuoteClient implements RestaurantQuoteClient {

    private final RestClient restClient;
    private final InternalRestaurantTokenService tokenService;

    public HttpRestaurantQuoteClient(
            @Qualifier("restaurantServiceRestClient") RestClient restClient,
            InternalRestaurantTokenService tokenService) {
        this.restClient = restClient;
        this.tokenService = tokenService;
    }

    @Override
    public RestaurantQuoteResponse quote(String restaurantId, RestaurantQuoteRequest request) {
        RestaurantQuoteResponse response;
        try {
            response = restClient.post()
                    .uri("/internal/restaurants/{restaurantId}/menu-quote", restaurantId)
                    .headers(headers -> headers.setBearerAuth(tokenService.issueMenuReadToken()))
                    .body(request)
                    .retrieve()
                    .body(RestaurantQuoteResponse.class);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException();
            }
            if (exception.getStatusCode().is5xxServerError()) {
                throw new DependencyUnavailableException();
            }
            throw new UpstreamResponseException();
        } catch (ResourceAccessException exception) {
            throw new DependencyUnavailableException();
        } catch (RestClientException exception) {
            throw new UpstreamResponseException();
        }
        if (response == null || response.items() == null
                || !restaurantId.equalsIgnoreCase(response.restaurantId())) {
            throw new UpstreamResponseException();
        }
        return response;
    }
}
