package com.xebia.ace.shopping.identity.registration;

import java.util.UUID;

public record NewUser(UUID userId, String email, String mobile, String status, String clientContext) {

    public static final String STATUS_ACTIVE = "ACTIVE";
}
