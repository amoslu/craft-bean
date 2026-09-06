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

    /** 与 BootstrapDataInitializer 内置种子密码一致。 */
    private static final String ADMIN_PASSWORD = "Admin@123456";
    /** 无手机号账号（含内置 admin）的登录尾号兜底值。 */
    private static final String PHONE_LESS_TAIL = "0000";

    @Autowired
    TestRestTemplate rest;

    private HttpEntity<Map<String, String>> loginBody(String username, String password, String phoneTail) {
        return new HttpEntity<>(phoneTail == null
                ? Map.of("username", username, "password", password)
                : Map.of("username", username, "password", password, "phoneTail", phoneTail));
    }

    @SuppressWarnings("unchecked")
    private String loginAndGetToken() {
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login",
                loginBody("admin", ADMIN_PASSWORD, PHONE_LESS_TAIL), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("token");
    }

    @Test
    void loginRequiresPhoneTail() {
        // 三要素登录：缺手机号后四位应被参数校验拒绝（400），而非放行
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login",
                loginBody("admin", ADMIN_PASSWORD, null), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login",
                loginBody("admin", "bad", PHONE_LESS_TAIL), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<String, Object>) resp.getBody()).get("code")).isEqualTo(401);
    }

    @Test
    void wrongPhoneTailRejected() {
        // 密码正确但手机号后四位不符 → 复用同一条通用文案，不泄露哪一环失败
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login",
                loginBody("admin", ADMIN_PASSWORD, "9999"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().get("message").toString()).contains("用户名或密码错误");
    }

    @Test
    void meWithoutTokenRejected() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/v1/auth/me", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAndGetToken());
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginUsesStoredPhoneLastFourNotFallback() {
        // 管理员建号（带手机号）后，登录尾号须为真实手机号末四位；0000 兜底仅对无手机号账号生效
        String username = "phone_" + System.currentTimeMillis();
        ResponseEntity<Map> created = rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", username, "name", "手机号测试",
                        "password", "Pass@1234", "role", "STAFF", "phone", "13800138000"), adminHeaders()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> fallback = rest.postForEntity("/api/v1/auth/login",
                loginBody(username, "Pass@1234", "0000"), Map.class);
        assertThat(fallback.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> real = rest.postForEntity("/api/v1/auth/login",
                loginBody(username, "Pass@1234", "8000"), Map.class);
        assertThat(real.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
