package com.foodieexpress.deliveryservice.service;

import com.foodieexpress.deliveryservice.dto.OrderStatusCallbackRequest;
import com.foodieexpress.deliveryservice.exception.OrderCallbackException;
import com.foodieexpress.deliveryservice.model.DeliveryEvent;
import com.foodieexpress.deliveryservice.security.InternalOrderTokenService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpOrderStatusClient implements OrderStatusClient {

    private final RestClient restClient;
    private final InternalOrderTokenService tokenService;

    public HttpOrderStatusClient(
            @Qualifier("orderServiceRestClient") RestClient restClient,
            InternalOrderTokenService tokenService) {
        this.restClient = restClient;
        this.tokenService = tokenService;
    }

    @Override
    public void report(String orderId, String deliveryId, DeliveryEvent event) {
        try {
            restClient.post()
                    .uri("/internal/orders/{orderId}/delivery-status", orderId)
                    .headers(headers -> headers.setBearerAuth(tokenService.issueStatusUpdateToken()))
                    .body(new OrderStatusCallbackRequest(
                            deliveryId, event.eventId(), callbackStatus(event.type()), event.occurredAt()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpStatusCodeException | ResourceAccessException exception) {
            throw new OrderCallbackException();
        } catch (RestClientException exception) {
            throw new OrderCallbackException();
        }
    }

    private String callbackStatus(String eventType) {
        return switch (eventType) {
            case "PICKED_UP" -> "OUT_FOR_DELIVERY";
            case "DELIVERED" -> "DELIVERED";
            case "CANCELLED" -> "CANCELLED";
            default -> throw new IllegalArgumentException("Delivery event does not update Order status");
        };
    }
}
