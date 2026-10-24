package com.foodieexpress.deliveryservice.service;

import com.foodieexpress.deliveryservice.model.DeliveryEvent;

public interface OrderStatusClient {

    void report(String orderId, String deliveryId, DeliveryEvent event);
}
