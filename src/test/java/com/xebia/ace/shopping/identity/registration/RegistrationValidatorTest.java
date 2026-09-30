package com.xebia.ace.shopping.identity.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationValidatorTest {

    private final RegistrationValidator validator = new RegistrationValidator();

    @Test
    void acceptsSyntacticallyValidEmailAndMobile() {
        assertThat(validator.validate(new RegistrationRequest("jane.doe@example.com", "+14155552671", null))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requiresEmail(String email) {
        assertThat(validator.validate(new RegistrationRequest(email, "+14155552671", null)))
                .containsExactlyEntriesOf(java.util.Map.of("email", RegistrationValidator.EMAIL_REQUIRED));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requiresMobile(String mobile) {
        assertThat(validator.validate(new RegistrationRequest("jane@example.com", mobile, null)))
                .containsExactlyEntriesOf(java.util.Map.of("mobile", RegistrationValidator.MOBILE_REQUIRED));
    }

    @Test
    void reportsBothMissingFields() {
        assertThat(validator.validate(new RegistrationRequest(null, null, null)))
                .containsOnlyKeys("email", "mobile");
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainaddress", "no-at.example.com", "a@b", "a b@example.com", "@example.com", "jane@example."})
    void rejectsInvalidEmail(String email) {
        assertThat(validator.validate(new RegistrationRequest(email, "+14155552671", null)))
                .containsEntry("email", RegistrationValidator.EMAIL_INVALID);
    }

    @Test
    void rejectsEmailLongerThanColumnLimit() {
        String email = "a".repeat(60) + "@" + "b".repeat(300) + ".com";
        assertThat(validator.validate(new RegistrationRequest(email, "+14155552671", null)))
                .containsEntry("email", RegistrationValidator.EMAIL_INVALID);
    }

    @Test
    void rejectsEmailWithOverlongLocalPart() {
        String email = "a".repeat(65) + "@example.com";
        assertThat(validator.validate(new RegistrationRequest(email, "+14155552671", null)))
                .containsEntry("email", RegistrationValidator.EMAIL_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "abcdefghij", "+1-800-FLOWERS", "1234567890123456", "++14155552671"})
    void rejectsInvalidMobile(String mobile) {
        assertThat(validator.validate(new RegistrationRequest("jane@example.com", mobile, null)))
                .containsEntry("mobile", RegistrationValidator.MOBILE_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"+1 (415) 555-2671", "415.555.2671", "07911 123456"})
    void acceptsCommonMobileFormatting(String mobile) {
        assertThat(validator.validate(new RegistrationRequest("jane@example.com", mobile, null))).isEmpty();
    }

    @Test
    void normalizesMobileBySeparatorRemoval() {
        assertThat(validator.normalizeMobile(" +1 (415) 555-2671 ")).isEqualTo("+14155552671");
    }

    @Test
    void normalizesEmailByTrimming() {
        assertThat(validator.normalizeEmail("  Jane@Example.com ")).isEqualTo("Jane@Example.com");
    }
}
