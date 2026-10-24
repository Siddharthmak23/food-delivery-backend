package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.DeliveryCreateRequest;
import com.foodieexpress.orderservice.dto.DeliveryCreateResponse;

public interface DeliveryProvisioningClient {

    DeliveryCreateResponse create(String orderId, DeliveryCreateRequest request);

    void cancel(String deliveryId, String reason);
}
