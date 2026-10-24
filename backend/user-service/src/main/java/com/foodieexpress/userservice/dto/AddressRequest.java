package com.foodieexpress.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 40) String label,
        @NotBlank @Size(max = 100) String recipientName,
        @NotBlank @Size(max = 150) String line1,
        @Size(max = 150) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "^(Andaman and Nicobar Islands|Andhra Pradesh|Arunachal Pradesh|Assam|Bihar|Chandigarh|Chhattisgarh|Dadra and Nagar Haveli and Daman and Diu|Delhi|Goa|Gujarat|Haryana|Himachal Pradesh|Jammu and Kashmir|Jharkhand|Karnataka|Kerala|Ladakh|Lakshadweep|Madhya Pradesh|Maharashtra|Manipur|Meghalaya|Mizoram|Nagaland|Odisha|Puducherry|Punjab|Rajasthan|Sikkim|Tamil Nadu|Telangana|Tripura|Uttar Pradesh|Uttarakhand|West Bengal)$") String region,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String postalCode,
        @NotBlank @Pattern(regexp = "^IN$") String countryCode,
        @NotBlank @Size(max = 32) @Pattern(regexp = "^(?:\\+91[ -]?)?[6-9](?:[ -]?\\d){9}$") String phone) {
}
