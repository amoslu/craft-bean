package com.craftbean;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContextAndHealthIT {

    @Autowired
    TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void healthReportsDbUp() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/v1/health", Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat((Map<String, Object>) resp.getBody()).containsEntry("code", 0);
        Map<String, String> data = (Map<String, String>) resp.getBody().get("data");
        assertThat(data).containsEntry("db", "up");
    }
}
