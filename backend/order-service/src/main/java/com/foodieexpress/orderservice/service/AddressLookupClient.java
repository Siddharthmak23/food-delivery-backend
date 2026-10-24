package com.foodieexpress.orderservice.service;

import com.foodieexpress.orderservice.dto.UserAddressResponse;

public interface AddressLookupClient {

    UserAddressResponse getOwnedAddress(String userId, String addressId);
}
