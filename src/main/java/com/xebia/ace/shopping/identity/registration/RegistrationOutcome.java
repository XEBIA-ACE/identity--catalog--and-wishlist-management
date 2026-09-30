package com.xebia.ace.shopping.identity.registration;

import org.springframework.http.HttpStatus;

public record RegistrationOutcome(Status status, RegistrationResult result) {

    public enum Status {
        CREATED(HttpStatus.CREATED),
        INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
        INSECURE_TRANSPORT(HttpStatus.BAD_REQUEST),
        UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

        private final HttpStatus httpStatus;

        Status(HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
        }

        public HttpStatus httpStatus() {
            return httpStatus;
        }
    }
}
