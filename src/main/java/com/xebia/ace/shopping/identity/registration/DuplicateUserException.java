package com.xebia.ace.shopping.identity.registration;

public class DuplicateUserException extends RuntimeException {

    private final String field;

    public DuplicateUserException(String field, Throwable cause) {
        super("Duplicate user " + field, cause);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
