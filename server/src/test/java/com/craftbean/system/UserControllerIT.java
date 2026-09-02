package com.craftbean.system;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
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
class UserControllerIT {

    @Autowired
    TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        HttpEntity<Map<String, String>> body = new HttpEntity<>(Map.of("username", username, "password", password));
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("token");
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login("admin", "admin123"));
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsersReturnsAdmin() {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/system/users?page=1&size=50",
                HttpMethod.GET, new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        assertThat((Integer) data.get("total")).isGreaterThanOrEqualTo(1);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertThat(list).anyMatch(u -> "admin".equals(u.get("username")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createUpdateResetDeleteFlow() {
        HttpHeaders admin = adminHeaders();
        String username = "it_u_" + System.currentTimeMillis();

        // create
        Map<String, String> createBody = Map.of("username", username, "name", "测试", "password", "pass123", "role", "STAFF");
        ResponseEntity<Map> createResp = rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(createBody, admin), Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> created = (Map<String, Object>) createResp.getBody().get("data");
        Integer id = (Integer) created.get("id");

        // update role
        Map<String, String> updateBody = Map.of("name", "测试2", "role", "READONLY", "status", "ACTIVE");
        ResponseEntity<Map> updateResp = rest.exchange("/api/v1/system/users/" + id, HttpMethod.PUT,
                new HttpEntity<>(updateBody, admin), Map.class);
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) updateResp.getBody().get("data")).get("role")).isEqualTo("READONLY");

        // reset password
        Map<String, String> resetBody = Map.of("newPassword", "newpass456");
        ResponseEntity<Map> resetResp = rest.exchange("/api/v1/system/users/" + id + "/reset-password",
                HttpMethod.POST, new HttpEntity<>(resetBody, admin), Map.class);
        assertThat(resetResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // login with new password works
        assertThat(login(username, "newpass456")).isNotBlank();

        // delete
        ResponseEntity<Map> delResp = rest.exchange("/api/v1/system/users/" + id, HttpMethod.DELETE,
                new HttpEntity<>(admin), Map.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotDeleteSelf() {
        HttpHeaders admin = adminHeaders();
        // 管理员自己的 id 从 /auth/me 获取
        ResponseEntity<Map> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(admin), Map.class);
        Integer selfId = (Integer) ((Map<String, Object>) me.getBody().get("data")).get("id");

        ResponseEntity<Map> delResp = rest.exchange("/api/v1/system/users/" + selfId, HttpMethod.DELETE,
                new HttpEntity<>(admin), Map.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(delResp.getBody().get("message")).isEqualTo("不能删除自己");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotDisableSelf() {
        HttpHeaders admin = adminHeaders();
        ResponseEntity<Map> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(admin), Map.class);
        Map<String, Object> self = (Map<String, Object>) me.getBody().get("data");
        Integer selfId = (Integer) self.get("id");

        Map<String, String> updateBody = Map.of("name", "管理员", "role", "ADMIN", "status", "DISABLED");
        ResponseEntity<Map> updResp = rest.exchange("/api/v1/system/users/" + selfId, HttpMethod.PUT,
                new HttpEntity<>(updateBody, admin), Map.class);
        assertThat(updResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(updResp.getBody().get("message")).isEqualTo("不能停用自己");
    }
}
