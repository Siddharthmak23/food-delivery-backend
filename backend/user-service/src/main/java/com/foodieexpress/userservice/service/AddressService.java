package com.foodieexpress.userservice.service;

import com.foodieexpress.userservice.dto.AddressRequest;
import com.foodieexpress.userservice.dto.AddressResponse;
import com.foodieexpress.userservice.dto.InternalAddressResponse;
import com.foodieexpress.userservice.dto.PageResponse;
import com.foodieexpress.userservice.exception.ResourceNotFoundException;
import com.foodieexpress.userservice.model.Address;
import com.foodieexpress.userservice.repository.AddressRepository;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserService userService;

    public AddressService(AddressRepository addressRepository, UserService userService) {
        this.addressRepository = addressRepository;
        this.userService = userService;
    }

    public PageResponse<AddressResponse> list(String userId, int page, int size, Sort.Direction direction) {
        userService.getActiveUser(userId);
        String sort = "createdAt," + direction.name().toLowerCase();
        return PageResponse.from(
                addressRepository.findAllByUserId(
                        userId,
                        PageRequest.of(page, size, Sort.by(direction, "createdAt", "id"))),
                sort).map(AddressResponse::from);
    }

    public Address create(String userId, AddressRequest request) {
        userService.getActiveUser(userId);
        Instant now = Instant.now();
        Address address = new Address();
        address.setUserId(userId);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        apply(address, request);
        return addressRepository.save(address);
    }

    public Address update(String userId, String addressId, AddressRequest request) {
        Address address = ownedAddress(userId, addressId);
        apply(address, request);
        address.setUpdatedAt(Instant.now());
        return addressRepository.save(address);
    }

    public void delete(String userId, String addressId) {
        addressRepository.delete(ownedAddress(userId, addressId));
    }

    public InternalAddressResponse getForCheckout(String userId, String addressId) {
        Address address = ownedAddress(userId, addressId);
        return new InternalAddressResponse(userId, new InternalAddressResponse.Address(
                address.getId(),
                address.getRecipientName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getRegion(),
                address.getPostalCode(),
                address.getCountryCode(),
                address.getPhone()));
    }

    private Address ownedAddress(String userId, String addressId) {
        userService.getActiveUser(userId);
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private void apply(Address address, AddressRequest request) {
        address.setLabel(request.label().trim());
        address.setRecipientName(request.recipientName().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(request.line2() == null || request.line2().isBlank() ? null : request.line2().trim());
        address.setCity(request.city().trim());
        address.setRegion(request.region().trim());
        address.setPostalCode(request.postalCode().trim());
        address.setCountryCode(request.countryCode());
        String phone = request.phone().replaceAll("[ -]", "");
        address.setPhone(phone.startsWith("+91") ? phone : "+91" + phone);
    }
}
