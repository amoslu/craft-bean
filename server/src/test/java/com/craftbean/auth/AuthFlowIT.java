package com.craftbean.auth;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT {

    @Autowired
    TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private String loginAndGetToken() {
        HttpEntity<Map<String, String>> body = new HttpEntity<>(Map.of("username", "admin", "password", "admin123"));
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("token");
    }

    @Test
    void loginReturnsTokenAndMeWorks() {
        String token = loginAndGetToken();
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> user = (Map<String, Object>) ((Map<String, Object>) me.getBody()).get("data");
        assertThat(user).containsEntry("username", "admin");
    }

    @Test
    void wrongPasswordRejected() {
        HttpEntity<Map<String, String>> body = new HttpEntity<>(Map.of("username", "admin", "password", "bad"));
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<String, Object>) resp.getBody()).get("code")).isEqualTo(401);
    }

    @Test
    void meWithoutTokenRejected() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/v1/auth/me", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
