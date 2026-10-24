package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.DeliveryCreateRequest;
import com.foodieexpress.orderservice.dto.DeliveryCreateResponse;
import com.foodieexpress.orderservice.exception.DependencyUnavailableException;
import com.foodieexpress.orderservice.exception.UpstreamResponseException;
import com.foodieexpress.orderservice.security.InternalDeliveryTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpDeliveryProvisioningClient implements DeliveryProvisioningClient {

    private final RestClient restClient;
    private final InternalDeliveryTokenService tokenService;

    public HttpDeliveryProvisioningClient(
            @Qualifier("deliveryServiceRestClient") RestClient restClient,
            InternalDeliveryTokenService tokenService) {
        this.restClient = restClient;
        this.tokenService = tokenService;
    }

    @Override
    public DeliveryCreateResponse create(String orderId, DeliveryCreateRequest request) {
        try {
            return restClient.post()
                    .uri("/api/deliveries")
                    .header("Idempotency-Key", orderId)
                    .headers(headers -> headers.setBearerAuth(tokenService.issueCreateToken()))
                    .body(request)
                    .retrieve()
                    .body(DeliveryCreateResponse.class);
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
    }

    @Override
    public void cancel(String deliveryId, String reason) {
        try {
            restClient.patch()
                    .uri("/api/deliveries/{deliveryId}/cancel", deliveryId)
                    .headers(headers -> headers.setBearerAuth(tokenService.issueCancelToken()))
                    .body(new CancelDeliveryRequest(reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException | ResourceAccessException exception) {
            throw new DependencyUnavailableException();
        } catch (RestClientException exception) {
            throw new UpstreamResponseException();
        }
    }

    private record CancelDeliveryRequest(String reason) {
    }
}
