package com.foodieexpress.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 30)
        @Pattern(
                regexp = INDIAN_PHONE_REGEX,
                message = PHONE_ERROR_MESSAGE)
        String phone) {

    private static final String INDIAN_PHONE_REGEX =
            "^(?:\\s*|(?:\\+91[ -]?)?(?!([0-9])(?:[ -]?\\1){9}$)[6-9](?:[ -]?\\d){9})$";
    private static final String PHONE_ERROR_MESSAGE =
            "Phone must be a valid Indian mobile number with 10 digits starting from 6 to 9, optionally prefixed by +91";
    private static final java.util.regex.Pattern INDIAN_PHONE_PATTERN =
            java.util.regex.Pattern.compile(INDIAN_PHONE_REGEX);

    public static boolean isValidPhone(String phone) {
        return phone == null || (phone.length() <= 30 && INDIAN_PHONE_PATTERN.matcher(phone).matches());
    }

    public static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = phone.replaceAll("[ -]", "");
        return normalized.startsWith("+91") ? normalized : "+91" + normalized;
    }
}
