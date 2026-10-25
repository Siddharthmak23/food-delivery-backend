package com.foodieexpress.userservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodieexpress.userservice.dto.AddressRequest;
import com.foodieexpress.userservice.exception.DuplicateAddressException;
import com.foodieexpress.userservice.exception.ResourceNotFoundException;
import com.foodieexpress.userservice.model.Address;
import com.foodieexpress.userservice.model.User;
import com.foodieexpress.userservice.repository.AddressRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddressServiceTests {

    private static final String USER_ID = "671000000000000000000001";
    private static final String ADDRESS_ID = "671000000000000000000010";

    private final AddressRepository addressRepository = mock(AddressRepository.class);
    private final UserService userService = mock(UserService.class);
    private final AddressService addressService = new AddressService(addressRepository, userService);

    @BeforeEach
    void setUp() {
        when(userService.getActiveUser(USER_ID))
                .thenReturn(new User("person@example.com", "hash", "Person", Instant.now()));
    }

    @Test
    void createPersistsAndReturnsAllAddressFields() {
        AddressRequest request = request("Mumbai");
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Address saved = addressService.create(USER_ID, request);

        assertAddress(saved, request);
        assertEquals(USER_ID, saved.getUserId());
        verify(addressRepository).save(saved);
    }

    @Test
    void updatePersistsAndReturnsTheChangedOwnedAddress() {
        Address existing = address(ADDRESS_ID, USER_ID);
        AddressRequest request = request("Pune");
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Address updated = addressService.update(USER_ID, ADDRESS_ID, request);

        assertAddress(updated, request);
        assertEquals(ADDRESS_ID, updated.getId());
        assertEquals(USER_ID, updated.getUserId());
        verify(addressRepository).save(existing);
    }

    @Test
    void updateForAnotherUsersAddressDoesNotSaveOrModifyAnything() {
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> addressService.update(USER_ID, ADDRESS_ID, request("Pune")));

        verify(addressRepository).findByIdAndUserId(ADDRESS_ID, USER_ID);
        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void createRejectsSameLocationIgnoringCaseWhitespaceRecipientAndPhone() {
        Address existing = location("address-existing", USER_ID, "12, Green Park", null,
                "Ahmedabad", "Gujarat", "380001", "IN");
        when(addressRepository.findAllByUserId(USER_ID)).thenReturn(java.util.List.of(existing));
        AddressRequest duplicate = requestLocation(
                " 12,   GREEN PARK ", null, " Ahmedabad ", " GUJARAT ",
                "380001", "IN", "Different Recipient", "+919999999999");

        assertThrows(DuplicateAddressException.class, () -> addressService.create(USER_ID, duplicate));

        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void allowsDifferentDeliveryLocationsWithinTheSameCityAndPostalCode() {
        java.util.List<Address> existing = java.util.List.of(
                location("address-1", USER_ID, "12, Green Park", null,
                        "Ahmedabad", "Gujarat", "380001", "IN"),
                location("address-2", USER_ID, "Flat 101", "Sunrise Society",
                        "Ahmedabad", "Gujarat", "380001", "IN"));
        when(addressRepository.findAllByUserId(USER_ID)).thenReturn(existing);
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Address streetNumber = addressService.create(USER_ID, requestLocation(
                "24, Green Park", null, "Ahmedabad", "Gujarat", "380001", "IN",
                "Customer", "+919876543210"));
        Address differentFlat = addressService.create(USER_ID, requestLocation(
                "Flat 202", "Sunrise Society", "Ahmedabad", "Gujarat", "380001", "IN",
                "Customer", "+919876543210"));

        assertEquals("24, Green Park", streetNumber.getLine1());
        assertEquals("Flat 202", differentFlat.getLine1());
        verify(addressRepository, org.mockito.Mockito.times(2)).save(any(Address.class));
    }

    @Test
    void duplicateDetectionIsScopedToTheAuthenticatedCustomer() {
        Address anotherCustomersAddress = location("address-other-user",
                "671000000000000000000002", "12, Green Park", null,
                "Ahmedabad", "Gujarat", "380001", "IN");
        when(addressRepository.findAllByUserId(USER_ID))
                .thenReturn(java.util.List.of(anotherCustomersAddress));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Address saved = addressService.create(USER_ID, requestLocation(
                "12, Green Park", null, "Ahmedabad", "Gujarat", "380001", "IN",
                "Customer", "+919876543210"));

        assertEquals(USER_ID, saved.getUserId());
        verify(addressRepository).save(saved);
        verify(addressRepository).findAllByUserId(USER_ID);
    }

    @Test
    void updateAllowsUnchangedLocationButRejectsAnotherExistingLocationWithoutMutation() {
        Address current = location(ADDRESS_ID, USER_ID, "12, Green Park", null,
                "Ahmedabad", "Gujarat", "380001", "IN");
        Address other = location("address-other", USER_ID, "24, Green Park", null,
                "Ahmedabad", "Gujarat", "380001", "IN");
        when(addressRepository.findByIdAndUserId(ADDRESS_ID, USER_ID)).thenReturn(Optional.of(current));
        when(addressRepository.findAllByUserId(USER_ID)).thenReturn(java.util.List.of(current, other));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Address unchanged = addressService.update(USER_ID, ADDRESS_ID, requestLocation(
                "12, GREEN PARK ", null, " AHMEDABAD ", "gujarat",
                "380001", "IN", "Different Recipient", "+919999999999"));
        assertEquals(ADDRESS_ID, unchanged.getId());
        assertEquals("12, GREEN PARK", unchanged.getLine1());

        clearInvocations(addressRepository);
        String priorName = current.getRecipientName();
        assertThrows(DuplicateAddressException.class, () -> addressService.update(
                USER_ID, ADDRESS_ID, requestLocation(" 24, Green Park", null,
                        "Ahmedabad", "Gujarat", "380001", "IN",
                        "Updated Recipient", "+919876543210")));
        assertEquals("12, GREEN PARK", current.getLine1());
        assertEquals(priorName, current.getRecipientName());
        verify(addressRepository, never()).save(any(Address.class));
    }

    private static AddressRequest request(String city) {
        return new AddressRequest(
                "Home", "Customer", "22 New Road", "Apartment 3", city,
                "Maharashtra", "411001", "IN", "+91 98765-43210");
    }

    private static AddressRequest requestLocation(
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String recipient,
            String phone) {
        return new AddressRequest(
                "Home", recipient, line1, line2, city, region, postalCode, countryCode, phone);
    }

    private static Address location(
            String id,
            String userId,
            String line1,
            String line2,
            String city,
            String region,
            String postalCode,
            String countryCode) {
        Address address = new Address();
        address.setId(id);
        address.setUserId(userId);
        address.setLabel("Home");
        address.setRecipientName("Customer");
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setRegion(region);
        address.setPostalCode(postalCode);
        address.setCountryCode(countryCode);
        address.setPhone("+919876543210");
        address.setCreatedAt(Instant.now());
        address.setUpdatedAt(Instant.now());
        return address;
    }

    private static Address address(String id, String userId) {
        Address address = new Address();
        address.setId(id);
        address.setUserId(userId);
        address.setLabel("Home");
        address.setRecipientName("Customer");
        address.setLine1("10 Main Road");
        address.setCity("Mumbai");
        address.setRegion("Maharashtra");
        address.setPostalCode("400001");
        address.setCountryCode("IN");
        address.setPhone("+919876543210");
        address.setCreatedAt(Instant.now());
        address.setUpdatedAt(Instant.now());
        return address;
    }

    private static void assertAddress(Address actual, AddressRequest expected) {
        assertEquals(expected.label(), actual.getLabel());
        assertEquals(expected.recipientName(), actual.getRecipientName());
        assertEquals(expected.line1(), actual.getLine1());
        assertEquals(expected.line2(), actual.getLine2());
        assertEquals(expected.city(), actual.getCity());
        assertEquals(expected.region(), actual.getRegion());
        assertEquals(expected.postalCode(), actual.getPostalCode());
        assertEquals(expected.countryCode(), actual.getCountryCode());
        assertEquals("+919876543210", actual.getPhone());
    }
}
