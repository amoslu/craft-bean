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
    private String login(String username, String password, String phoneTail) {
        HttpEntity<Map<String, String>> body = new HttpEntity<>(Map.of(
                "username", username, "password", password, "phoneTail", phoneTail));
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("token");
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login("admin", "Admin@123456", "0000"));
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsersReturnsAdmin() {
        // 按 keyword 精确定位 admin：开发库会累积历次测试建的用户，
        // 靠 page=1 翻到 admin 会随数据量增多而失败
        ResponseEntity<Map> resp = rest.exchange("/api/v1/system/users?keyword=admin&page=1&size=50",
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

        // create（手机号必填 + 强密码）
        Map<String, String> createBody = Map.of("username", username, "name", "测试", "password", "Pass@1234",
                "role", "STAFF", "phone", "13800138000");
        ResponseEntity<Map> createResp = rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(createBody, admin), Map.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> created = (Map<String, Object>) createResp.getBody().get("data");
        Integer id = (Integer) created.get("id");
        assertThat(created.get("phone")).isEqualTo("13800138000");

        // update role
        Map<String, String> updateBody = Map.of("name", "测试2", "role", "READONLY", "status", "ACTIVE");
        ResponseEntity<Map> updateResp = rest.exchange("/api/v1/system/users/" + id, HttpMethod.PUT,
                new HttpEntity<>(updateBody, admin), Map.class);
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<String, Object>) updateResp.getBody().get("data")).get("role")).isEqualTo("READONLY");

        // reset password（新密码同样须满足强密码策略）
        Map<String, String> resetBody = Map.of("newPassword", "NewPass@456");
        ResponseEntity<Map> resetResp = rest.exchange("/api/v1/system/users/" + id + "/reset-password",
                HttpMethod.POST, new HttpEntity<>(resetBody, admin), Map.class);
        assertThat(resetResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // login with new password works（该用户有手机号，尾号须为 13800138000 的末四位 8000）
        assertThat(login(username, "NewPass@456", "8000")).isNotBlank();

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

    @Test
    void createUserWithoutPhoneRejected() {
        HttpHeaders admin = adminHeaders();
        String username = "nophone_" + System.currentTimeMillis();
        Map<String, String> body = Map.of("username", username, "name", "无手机号",
                "password", "Pass@1234", "role", "STAFF");
        ResponseEntity<Map> resp = rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(body, admin), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsWeakPasswords() {
        HttpHeaders admin = adminHeaders();
        String[] weak = {"abcdefgh", "12345678", "abc12345", "a1!"}; // 缺特殊/缺字母/缺数字/过短
        for (int i = 0; i < weak.length; i++) {
            String username = "weak_" + System.nanoTime() + "_" + i;
            Map<String, String> body = Map.of("username", username, "name", "弱密码",
                    "password", weak[i], "role", "STAFF", "phone", "13800138000");
            ResponseEntity<Map> resp = rest.exchange("/api/v1/system/users", HttpMethod.POST,
                    new HttpEntity<>(body, admin), Map.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetPasswordRejectsWeakPassword() {
        HttpHeaders admin = adminHeaders();
        String username = "rpweak_" + System.currentTimeMillis();
        Map<String, String> createBody = Map.of("username", username, "name", "重置弱密码测试",
                "password", "Pass@1234", "role", "STAFF", "phone", "13800138000");
        ResponseEntity<Map> createResp = rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(createBody, admin), Map.class);
        Integer id = (Integer) ((Map<String, Object>) createResp.getBody().get("data")).get("id");

        Map<String, String> weakReset = Map.of("newPassword", "abcdefgh");
        ResponseEntity<Map> resetResp = rest.exchange("/api/v1/system/users/" + id + "/reset-password",
                HttpMethod.POST, new HttpEntity<>(weakReset, admin), Map.class);
        assertThat(resetResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
