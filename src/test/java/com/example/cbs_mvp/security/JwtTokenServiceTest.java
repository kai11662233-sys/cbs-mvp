package com.example.cbs_mvp.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTest {

    @Test
    void init_shouldThrowException_whenDefaultSecretUsedInProdProfile() {
        JwtTokenService service = new JwtTokenService();
        ReflectionTestUtils.setField(service, "jwtSecret", "default-secret-test-key-32-bytes-long");
        ReflectionTestUtils.setField(service, "activeProfile", "prod");
        ReflectionTestUtils.setField(service, "expirationHours", 24);

        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    void init_shouldThrowException_whenDefaultSecretUsedInRealProfile() {
        JwtTokenService service = new JwtTokenService();
        ReflectionTestUtils.setField(service, "jwtSecret", "default-secret-test-key-32-bytes-long");
        ReflectionTestUtils.setField(service, "activeProfile", "real");
        ReflectionTestUtils.setField(service, "expirationHours", 24);

        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    void init_shouldNotThrowException_whenDefaultSecretUsedInStubProfile() {
        JwtTokenService service = new JwtTokenService();
        ReflectionTestUtils.setField(service, "jwtSecret", "default-secret-test-key-32-bytes-long");
        ReflectionTestUtils.setField(service, "activeProfile", "stub");
        ReflectionTestUtils.setField(service, "expirationHours", 24);

        assertDoesNotThrow(service::init);
    }

    @Test
    void init_shouldNotThrowException_whenNonDefaultSecretUsedInProdProfile() {
        JwtTokenService service = new JwtTokenService();
        ReflectionTestUtils.setField(service, "jwtSecret", "secure-and-long-enough-secret-for-production");
        ReflectionTestUtils.setField(service, "activeProfile", "prod");
        ReflectionTestUtils.setField(service, "expirationHours", 24);

        assertDoesNotThrow(service::init);
    }

    @Test
    void init_shouldThrowException_whenSecretIsTooShort() {
        JwtTokenService service = new JwtTokenService();
        ReflectionTestUtils.setField(service, "jwtSecret", "short");
        ReflectionTestUtils.setField(service, "activeProfile", "stub");
        ReflectionTestUtils.setField(service, "expirationHours", 24);

        assertThrows(IllegalStateException.class, service::init);
    }
}
