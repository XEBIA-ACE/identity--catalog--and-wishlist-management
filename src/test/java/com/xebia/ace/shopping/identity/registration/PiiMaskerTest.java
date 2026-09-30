package com.xebia.ace.shopping.identity.registration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    void masksEmailLocalPart() {
        assertThat(PiiMasker.maskEmail("jane.doe@example.com")).isEqualTo("j***@example.com");
    }

    @Test
    void masksMobileKeepingLastTwoDigits() {
        assertThat(PiiMasker.maskMobile("+14155552671")).isEqualTo("***71");
    }

    @Test
    void handlesEmptyValues() {
        assertThat(PiiMasker.maskEmail(null)).isEqualTo("<empty>");
        assertThat(PiiMasker.maskMobile("")).isEqualTo("<empty>");
    }

    @Test
    void fingerprintIsStableCaseInsensitiveAndDoesNotLeakInput() {
        String fingerprint = PiiMasker.fingerprint("Jane@Example.com", "+14155552671");
        assertThat(fingerprint)
                .hasSize(16)
                .isEqualTo(PiiMasker.fingerprint("jane@example.com", "+14155552671"))
                .doesNotContain("jane", "4155552671");
    }
}
