package com.xebia.ace.shopping.identity.registration;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RegistrationValidator {

    public static final String EMAIL = "email";
    public static final String MOBILE = "mobile";

    static final String EMAIL_REQUIRED = "Email is required.";
    static final String EMAIL_INVALID = "Enter a valid email address.";
    static final String MOBILE_REQUIRED = "Mobile number is required.";
    static final String MOBILE_INVALID = "Enter a valid mobile number, including country code if applicable (7-15 digits).";

    private static final int EMAIL_MAX_LENGTH = 320;
    private static final int EMAIL_LOCAL_PART_MAX_LENGTH = 64;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern MOBILE_SEPARATORS = Pattern.compile("[\\s().-]");

    public Map<String, String> validate(RegistrationRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        String email = normalizeEmail(request.email());
        if (email.isEmpty()) {
            errors.put(EMAIL, EMAIL_REQUIRED);
        } else if (!isValidEmail(email)) {
            errors.put(EMAIL, EMAIL_INVALID);
        }

        String mobile = normalizeMobile(request.mobile());
        if (mobile.isEmpty()) {
            errors.put(MOBILE, MOBILE_REQUIRED);
        } else if (!MOBILE_PATTERN.matcher(mobile).matches()) {
            errors.put(MOBILE, MOBILE_INVALID);
        }

        return errors;
    }

    public String normalizeEmail(String email) {
        return email == null ? "" : email.strip();
    }

    public String normalizeMobile(String mobile) {
        return mobile == null ? "" : MOBILE_SEPARATORS.matcher(mobile.strip()).replaceAll("");
    }

    private static boolean isValidEmail(String email) {
        if (email.length() > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            return false;
        }
        return email.lastIndexOf('@') <= EMAIL_LOCAL_PART_MAX_LENGTH;
    }
}
