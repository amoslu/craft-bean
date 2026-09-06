package com.craftbean.auth;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.craftbean.system.SysUser;
import com.craftbean.system.SysUserMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LockoutIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    SysUserMapper userMapper;

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postLogin(String username, String password, String phoneTail) {
        HttpEntity<Map<String, String>> body = new HttpEntity<>(Map.of(
                "username", username, "password", password, "phoneTail", phoneTail));
        return rest.postForEntity("/api/v1/auth/login", body, Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void locksAfterFiveFailures() {
        String username = "lock_" + System.currentTimeMillis();
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setName("锁定测试");
        u.setPasswordHash(new BCryptPasswordEncoder().encode("correct123"));
        u.setRole("STAFF");
        u.setStatus("ACTIVE");
        u.setFailedAttempts(0);
        userMapper.insert(u);

        for (int i = 0; i < 5; i++) {
            postLogin(username, "wrong", "0000");
        }

        ResponseEntity<Map> locked = postLogin(username, "correct123", "0000");
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(locked.getBody().get("message").toString()).contains("锁定");
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrongPhoneTailLocksAfterFiveFailures() {
        String username = "tail_" + System.currentTimeMillis();
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setName("尾号锁定测试");
        u.setPhone("13800138000");
        u.setPasswordHash(new BCryptPasswordEncoder().encode("Pass@1234"));
        u.setRole("STAFF");
        u.setStatus("ACTIVE");
        u.setFailedAttempts(0);
        userMapper.insert(u);

        // 密码正确但手机尾号连续错 5 次 → 同样锁定（否则 4 位尾号可被无限爆破）
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> attempt = postLogin(username, "Pass@1234", "9999");
            assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        ResponseEntity<Map> locked = postLogin(username, "Pass@1234", "8000");
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(locked.getBody().get("message").toString()).contains("锁定");
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulLoginResetsCounter() {
        String username = "ok_" + System.currentTimeMillis();
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setName("正常测试");
        u.setPasswordHash(new BCryptPasswordEncoder().encode("correct123"));
        u.setRole("STAFF");
        u.setStatus("ACTIVE");
        u.setFailedAttempts(4);
        userMapper.insert(u);

        // 连续失败 4 次后，用正确密码登录应成功并清零计数
        ResponseEntity<Map> ok = postLogin(username, "correct123", "0000");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
