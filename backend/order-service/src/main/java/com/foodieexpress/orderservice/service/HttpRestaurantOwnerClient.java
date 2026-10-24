package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.RestaurantDeliveryDetailsResponse;
import com.foodieexpress.orderservice.exception.DependencyUnavailableException;
import com.foodieexpress.orderservice.exception.UpstreamResponseException;
import com.foodieexpress.orderservice.security.InternalRestaurantTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpRestaurantOwnerClient implements RestaurantOwnerClient {

    private final RestClient restClient;
    private final InternalRestaurantTokenService tokenService;

    public HttpRestaurantOwnerClient(
            @Qualifier("restaurantServiceRestClient") RestClient restClient,
            InternalRestaurantTokenService tokenService) {
        this.restClient = restClient;
        this.tokenService = tokenService;
    }

    @Override
    public boolean isOwner(String restaurantId, String userId) {
        try {
            restClient.get()
                    .uri("/internal/restaurants/{restaurantId}/owners/{userId}", restaurantId, userId)
                    .headers(headers -> headers.setBearerAuth(tokenService.issueOwnerVerificationToken()))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 404) {
                return false;
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
    }

    @Override
    public RestaurantDeliveryDetailsResponse getDeliveryDetails(String restaurantId) {
        RestaurantDeliveryDetailsResponse response;
        try {
            response = restClient.get()
                    .uri("/internal/restaurants/{restaurantId}/delivery-details", restaurantId)
                    .headers(headers -> headers.setBearerAuth(tokenService.issueDeliveryProvisionToken()))
                    .retrieve()
                    .body(RestaurantDeliveryDetailsResponse.class);
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new DependencyUnavailableException();
            }
            throw new UpstreamResponseException();
        } catch (ResourceAccessException exception) {
            throw new DependencyUnavailableException();
        } catch (RestClientException exception) {
            throw new UpstreamResponseException();
        }
        if (response == null || !restaurantId.equalsIgnoreCase(response.id())
                || blank(response.name()) || response.address() == null
                || blank(response.address().line1()) || blank(response.address().city())
                || blank(response.address().region()) || blank(response.address().postalCode())
                || blank(response.address().countryCode())) {
            throw new UpstreamResponseException();
        }
        return response;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
