package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.UserAddressResponse;
import com.foodieexpress.orderservice.exception.DependencyUnavailableException;
import com.foodieexpress.orderservice.exception.ResourceNotFoundException;
import com.foodieexpress.orderservice.exception.UpstreamResponseException;
import com.foodieexpress.orderservice.security.InternalUserTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAddressLookupClient implements AddressLookupClient {

    private final RestClient restClient;
    private final InternalUserTokenService tokenService;

    public HttpAddressLookupClient(
            @Qualifier("userServiceRestClient") RestClient restClient,
            InternalUserTokenService tokenService) {
        this.restClient = restClient;
        this.tokenService = tokenService;
    }

    @Override
    public UserAddressResponse getOwnedAddress(String userId, String addressId) {
        UserAddressResponse response;
        try {
            response = restClient.get()
                    .uri("/internal/users/{userId}/addresses/{addressId}", userId, addressId)
                    .headers(headers -> headers.setBearerAuth(tokenService.issueAddressReadToken()))
                    .retrieve()
                    .body(UserAddressResponse.class);
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
        if (response == null || response.address() == null
                || !userId.equalsIgnoreCase(response.userId())
                || !addressId.equalsIgnoreCase(response.address().id())
                || blank(response.address().recipientName())
                || blank(response.address().line1())
                || blank(response.address().city())
                || blank(response.address().region())
                || blank(response.address().postalCode())
                || !"IN".equals(response.address().countryCode())
                || blank(response.address().phone())) {
            throw new UpstreamResponseException();
        }
        return response;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
