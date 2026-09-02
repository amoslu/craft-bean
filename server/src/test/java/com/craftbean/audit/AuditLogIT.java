package com.craftbean.audit;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.craftbean.system.SysUser;
import com.craftbean.system.SysUserMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditLogIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    SysUserMapper userMapper;

    @SuppressWarnings("unchecked")
    private String login(String username, String password) {
        HttpEntity<Map<String, String>> body = new HttpEntity<>(Map.of("username", username, "password", password));
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login", body, Map.class);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        return (String) data.get("token");
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonAdminOnlySeesOwnLogs() {
        String username = "audit_" + System.currentTimeMillis();
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setName("审计测试");
        u.setPasswordHash(new BCryptPasswordEncoder().encode("pass123"));
        u.setRole("STAFF");
        u.setStatus("ACTIVE");
        u.setFailedAttempts(0);
        userMapper.insert(u);

        String token = login(username, "pass123"); // 产生该用户的 LOGIN 审计

        ResponseEntity<Map> resp = rest.exchange("/api/v1/audit/logs?page=1&size=100",
                HttpMethod.GET, new HttpEntity<>(bearer(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertThat(list).isNotEmpty();
        // 非管理员只能看到自己的日志
        assertThat(list).allMatch(l -> "审计测试".equals(l.get("operatorName")));
    }
}
