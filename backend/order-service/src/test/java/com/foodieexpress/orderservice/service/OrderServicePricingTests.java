package com.foodieexpress.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodieexpress.orderservice.dto.CreateOrderItemRequest;
import com.foodieexpress.orderservice.dto.CreateOrderRequest;
import com.foodieexpress.orderservice.dto.RestaurantQuoteResponse;
import com.foodieexpress.orderservice.dto.UserAddressResponse;
import com.foodieexpress.orderservice.exception.InvalidOrderException;
import com.foodieexpress.orderservice.model.Order;
import com.foodieexpress.orderservice.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class OrderServicePricingTests {

    private static final String CUSTOMER_ID = "671000000000000000000001";
    private static final String RESTAURANT_ID = "672000000000000000000001";
    private static final String ITEM_ID = "672000000000000000000010";
    private static final String ADDRESS_ID = "671000000000000000000010";

    @Test
    void createsAnInrOrderWithDecimalSubtotalAndNoDeliveryFee() {
        var repository = mock(OrderRepository.class);
        var addressClient = mock(AddressLookupClient.class);
        var quoteClient = mock(RestaurantQuoteClient.class);
        var service = service(repository, addressClient, quoteClient);
        when(repository.findByCustomerIdAndIdempotencyKey(CUSTOMER_ID, "checkout-key"))
                .thenReturn(Optional.empty());
        when(addressClient.getOwnedAddress(CUSTOMER_ID, ADDRESS_ID))
                .thenReturn(addressResponse());
        when(quoteClient.quote(any(), any())).thenReturn(quote("INR"));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = service.create(CUSTOMER_ID, "checkout-key", orderRequest()).order();

        assertEquals("INR", order.getCurrency());
        assertEquals("25.00", order.getSubtotal().toPlainString());
        assertEquals("0.00", order.getDeliveryFee().toPlainString());
        assertEquals("0.00", order.getDiscount().toPlainString());
        assertEquals("25.00", order.getTotal().toPlainString());
        assertEquals("25.00", order.getItems().getFirst().lineTotal().toPlainString());
        verify(repository).save(any(Order.class));
    }

    @Test
    void rejectsLegacyUsdMenuQuotesBeforeSavingANewOrder() {
        var repository = mock(OrderRepository.class);
        var addressClient = mock(AddressLookupClient.class);
        var quoteClient = mock(RestaurantQuoteClient.class);
        var service = service(repository, addressClient, quoteClient);
        when(repository.findByCustomerIdAndIdempotencyKey(CUSTOMER_ID, "checkout-key"))
                .thenReturn(Optional.empty());
        when(addressClient.getOwnedAddress(CUSTOMER_ID, ADDRESS_ID))
                .thenReturn(addressResponse());
        when(quoteClient.quote(any(), any())).thenReturn(quote("USD"));

        assertThrows(InvalidOrderException.class,
                () -> service.create(CUSTOMER_ID, "checkout-key", orderRequest()));
        verify(repository, never()).save(any(Order.class));
    }

    private OrderService service(
            OrderRepository repository,
            AddressLookupClient addressClient,
            RestaurantQuoteClient quoteClient) {
        return new OrderService(
                repository,
                mock(MongoTemplate.class),
                addressClient,
                quoteClient,
                mock(RestaurantOwnerClient.class),
                mock(DeliveryProvisioningClient.class));
    }

    private CreateOrderRequest orderRequest() {
        var item = new CreateOrderItemRequest();
        item.setMenuItemId(ITEM_ID);
        item.setQuantity(2);
        var request = new CreateOrderRequest();
        request.setRestaurantId(RESTAURANT_ID);
        request.setAddressId(ADDRESS_ID);
        request.setItems(List.of(item));
        return request;
    }

    private UserAddressResponse addressResponse() {
        return new UserAddressResponse(CUSTOMER_ID, new UserAddressResponse.Address(
                ADDRESS_ID, "Customer", "10 Main St", null, "Mumbai",
                "Maharashtra", "400001", "IN", "+919876543210"));
    }

    private RestaurantQuoteResponse quote(String currency) {
        return new RestaurantQuoteResponse(RESTAURANT_ID, List.of(
                new RestaurantQuoteResponse.Item(
                        ITEM_ID, RESTAURANT_ID, "Paneer Bowl", 2, "12.50", currency, true)));
    }
}
