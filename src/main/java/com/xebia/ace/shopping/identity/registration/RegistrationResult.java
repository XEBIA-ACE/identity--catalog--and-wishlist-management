package com.xebia.ace.shopping.identity.registration;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.UUID;

public record RegistrationResult(
        String outcome,
        @JsonInclude(JsonInclude.Include.ALWAYS) UUID userId,
        String message,
        Map<String, String> fieldErrors) {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    static final String MESSAGE_CREATED = "Account created.";
    static final String MESSAGE_FIX_FIELDS = "Please correct the highlighted fields.";
    static final String MESSAGE_UNAVAILABLE = "We can't create your account right now. Please try again.";
    static final String MESSAGE_INSECURE = "Secure connection required.";
    public static final String MESSAGE_INVALID_REQUEST = "Invalid registration request.";

    static RegistrationResult created(UUID userId) {
        return new RegistrationResult(SUCCESS, userId, MESSAGE_CREATED, null);
    }

    static RegistrationResult invalid(Map<String, String> fieldErrors) {
        return new RegistrationResult(FAILURE, null, MESSAGE_FIX_FIELDS, Map.copyOf(fieldErrors));
    }

    static RegistrationResult unavailable() {
        return new RegistrationResult(FAILURE, null, MESSAGE_UNAVAILABLE, null);
    }

    static RegistrationResult insecureTransport() {
        return new RegistrationResult(FAILURE, null, MESSAGE_INSECURE, null);
    }

    public static RegistrationResult invalidRequest() {
        return new RegistrationResult(FAILURE, null, MESSAGE_INVALID_REQUEST, null);
    }
}
