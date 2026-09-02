package com.craftbean.security;

import static org.assertj.core.api.Assertions.assertThat;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("craftbean-dev-only-secret-key-change-me-0123456789abcdef");
        props.setExpireHours(12);
        jwtService = new JwtService(props);
    }

    @Test
    void generateAndParseRoundTrip() {
        String token = jwtService.generateToken("amos", "ADMIN");
        Claims claims = jwtService.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("amos");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void tamperedTokenRejected() {
        String token = jwtService.generateToken("amos", "ADMIN");
        String tampered = token.substring(0, token.length() - 2) + "ab";
        Assertions.assertThrows(JwtException.class, () -> jwtService.parseToken(tampered));
    }
}
