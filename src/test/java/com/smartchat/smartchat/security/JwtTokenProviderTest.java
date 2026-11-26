package com.smartchat.smartchat.security;

import com.smartchat.smartchat.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test: no Spring context, runs in milliseconds. */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256";

    private static JwtTokenProvider provider(String secret, long expirationMs) {
        return new JwtTokenProvider(new AppProperties(
                new AppProperties.Jwt(secret, expirationMs), "http://localhost", List.of(), 1L));
    }

    @Test
    void tokenRoundTripsTheEmail() {
        JwtTokenProvider provider = provider(SECRET, 60_000);
        String token = provider.generateToken("surya@example.com");

        assertThat(provider.extractEmail(token)).contains("surya@example.com");
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtTokenProvider provider = provider(SECRET, 60_000);
        String token = provider.generateToken("surya@example.com");

        // Flip one character in the middle of the signature (the last base64 char may only hold padding bits).
        char[] chars = token.toCharArray();
        int index = token.lastIndexOf('.') + 10;
        chars[index] = chars[index] == 'a' ? 'b' : 'a';
        String tampered = new String(chars);

        assertThat(provider.extractEmail(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = provider("another-secret-that-is-also-long-enough-xx", 60_000).generateToken("a@b.com");

        assertThat(provider(SECRET, 60_000).extractEmail(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider provider = provider(SECRET, -1_000);
        String token = provider.generateToken("surya@example.com");

        assertThat(provider.extractEmail(token)).isEmpty();
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThatThrownBy(() -> provider("too-short", 60_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
