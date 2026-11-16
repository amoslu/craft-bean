package com.craftbean.archive;

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

/** 冲煮方式档案：预置种子、增改启停删、删除引用拦截、权限 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrewMethodIT {
    @Autowired TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void seedsArePreloaded() {
        Map<String, Object> page = list(null, null);
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertThat(((Number) page.get("total")).intValue()).isGreaterThanOrEqualTo(5);
        for (String expected : new String[]{"手冲", "冰滴", "意式", "法压", "冷萃"}) {
            assertThat(list.stream().map(m -> (String) m.get("name"))).contains(expected);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateNameRejected() {
        String suffix = unique();
        String name = "重复冲煮-" + suffix;
        assertThat(create(name).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> dup = create(name);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) dup.getBody().get("message")).contains("已存在");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateStatusAndDeleteUnreferenced() {
        String suffix = unique();
        String name = "启停冲煮-" + suffix;
        int id = idOf(create(name));

        // 改名
        String renamed = "改名冲煮-" + suffix;
        ResponseEntity<Map> updateResp = rest.exchange("/api/v1/archive/brew-methods/" + id,
                HttpMethod.PUT, new HttpEntity<>(Map.of("name", renamed, "status", "ACTIVE"), admin()), Map.class);
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 停用
        ResponseEntity<Map> statusResp = rest.exchange("/api/v1/archive/brew-methods/" + id + "/status?status=DISABLED",
                HttpMethod.POST, new HttpEntity<>(admin()), Map.class);
        assertThat(statusResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> disabled = (List<Map<String, Object>>) list(renamed, "DISABLED").get("list");
        assertThat(disabled).hasSize(1);
        assertThat(disabled.get(0).get("status")).isEqualTo("DISABLED");

        // 管理员删除未引用的冲煮方式
        ResponseEntity<Map> delResp = rest.exchange("/api/v1/archive/brew-methods/" + id,
                HttpMethod.DELETE, new HttpEntity<>(admin()), Map.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) list(renamed, null).get("total")).intValue()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotDeleteReferencedBrewMethod() {
        String suffix = unique();
        int methodId = idOf(create("被引用冲煮-" + suffix));
        // 生豆把该冲煮方式设为「推荐方向」
        ResponseEntity<Map> beanResp = rest.exchange("/api/v1/archive/green-beans", HttpMethod.POST,
                new HttpEntity<>(Map.of("beanType", "BLEND", "name", "引用豆-" + suffix,
                        "brewMethodIds", List.of(methodId)), admin()), Map.class);
        assertThat(beanResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> delResp = rest.exchange("/api/v1/archive/brew-methods/" + methodId,
                HttpMethod.DELETE, new HttpEntity<>(admin()), Map.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) delResp.getBody().get("message")).contains("引用");
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffCannotDeleteAndReadonlyCannotCreate() {
        String suffix = unique();
        int methodId = idOf(create("权限冲煮-" + suffix));

        String staffName = "it_bm_staff_" + suffix;
        rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", staffName, "name", "员工测试",
                        "password", "Staff@1234", "role", "STAFF", "phone", "13900000000"), admin()), Map.class);
        ResponseEntity<Map> staffDel = rest.exchange("/api/v1/archive/brew-methods/" + methodId,
                HttpMethod.DELETE, new HttpEntity<>(login(staffName, "Staff@1234")), Map.class);
        assertThat(staffDel.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String readonlyName = "it_bm_ro_" + suffix;
        rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", readonlyName, "name", "只读测试",
                        "password", "Read@1234", "role", "READONLY", "phone", "13900000000"), admin()), Map.class);
        ResponseEntity<Map> roCreate = rest.exchange("/api/v1/archive/brew-methods", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "只读冲煮-" + suffix), login(readonlyName, "Read@1234")), Map.class);
        assertThat(roCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- helpers ----------

    private String unique() {
        return String.valueOf(System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> create(String name) {
        return rest.exchange("/api/v1/archive/brew-methods", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "status", "ACTIVE"), admin()), Map.class);
    }

    @SuppressWarnings("unchecked")
    private int idOf(ResponseEntity<Map> resp) {
        return ((Number) ((Map<String, Object>) resp.getBody().get("data")).get("id")).intValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> list(String keyword, String status) {
        String url = "/api/v1/archive/brew-methods?page=1&size=100";
        if (keyword != null) url += "&keyword=" + keyword;
        if (status != null) url += "&status=" + status;
        ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders admin() {
        return login("admin", "Admin@123456");
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders login(String username, String password) {
        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password, "phoneTail", "0000"), Map.class);
        String token = (String) ((Map<String, Object>) login.getBody().get("data")).get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
