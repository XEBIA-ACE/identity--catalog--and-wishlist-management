package com.xebia.ace.shopping.identity.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class PiiMasker {

    private static final int FINGERPRINT_HEX_LENGTH = 16;
    private static final int VISIBLE_MOBILE_DIGITS = 2;

    private PiiMasker() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }
        int at = email.lastIndexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return "<empty>";
        }
        if (mobile.length() <= VISIBLE_MOBILE_DIGITS) {
            return "***";
        }
        return "***" + mobile.substring(mobile.length() - VISIBLE_MOBILE_DIGITS);
    }

    public static String fingerprint(String email, String mobile) {
        String canonical = (email == null ? "" : email.toLowerCase(Locale.ROOT)) + "|" + (mobile == null ? "" : mobile);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, FINGERPRINT_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
