package com.foodieexpress.userservice.service;

import com.foodieexpress.userservice.dto.AddressRequest;
import com.foodieexpress.userservice.dto.AddressResponse;
import com.foodieexpress.userservice.dto.InternalAddressResponse;
import com.foodieexpress.userservice.dto.PageResponse;
import com.foodieexpress.userservice.exception.DuplicateAddressException;
import com.foodieexpress.userservice.exception.ResourceNotFoundException;
import com.foodieexpress.userservice.model.Address;
import com.foodieexpress.userservice.repository.AddressRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
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
        rejectDuplicateLocation(userId, null, request);
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
        rejectDuplicateLocation(userId, addressId, request);
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

    private void rejectDuplicateLocation(String userId, String addressId, AddressRequest request) {
        AddressLocation requested = AddressLocation.from(request);
        boolean duplicateExists = addressRepository.findAllByUserId(userId).stream()
                .filter(address -> userId.equals(address.getUserId()))
                .filter(address -> addressId == null || !Objects.equals(address.getId(), addressId))
                .map(AddressLocation::from)
                .anyMatch(requested::equals);
        if (duplicateExists) {
            throw new DuplicateAddressException();
        }
    }

    private record AddressLocation(
            String deliveryLines,
            String city,
            String region,
            String postalCode,
            String countryCode) {

        private static AddressLocation from(AddressRequest request) {
            return new AddressLocation(
                    normalize(request.line1() + " " + (request.line2() == null ? "" : request.line2())),
                    normalize(request.city()),
                    normalize(request.region()),
                    normalize(request.postalCode()),
                    normalize(request.countryCode()));
        }

        private static AddressLocation from(Address address) {
            return new AddressLocation(
                    normalize(address.getLine1() + " " +
                            (address.getLine2() == null ? "" : address.getLine2())),
                    normalize(address.getCity()),
                    normalize(address.getRegion()),
                    normalize(address.getPostalCode()),
                    normalize(address.getCountryCode()));
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }
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
