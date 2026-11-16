package com.craftbean.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
class GreenBeanDeleteIT {
    @Autowired TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void adminCanDeleteUnreferencedGreenBean() {
        String suffix = unique();
        int beanId = createGreenBean("可删生豆-" + suffix);

        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/green-beans/" + beanId,
                HttpMethod.DELETE, new HttpEntity<>(admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 逻辑删除后列表不再出现（已删同名不可复用，与供货商语义一致）
        ResponseEntity<Map> list = rest.exchange(
                "/api/v1/archive/green-beans?keyword=" + "可删生豆-" + suffix, HttpMethod.GET,
                new HttpEntity<>(admin()), Map.class);
        Map<String, Object> page = (Map<String, Object>) list.getBody().get("data");
        assertThat(((List<?>) page.get("list"))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cannotDeleteGreenBeanWhileItHasAvailableLot() {
        String suffix = unique();
        int supplierId = createSupplier("采购供货商-" + suffix);
        int beanId = createGreenBean(supplierId, "被采购生豆-" + suffix);

        rest.exchange("/api/v1/greenbean/purchases", HttpMethod.POST,
                new HttpEntity<>(Map.of("supplierId", supplierId, "greenBeanId", beanId,
                        "purchaseDate", "2026-09-05", "quantityKg", new BigDecimal("5.000"),
                        "unitPrice", new BigDecimal("20.00")), admin()), Map.class);

        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/green-beans/" + beanId,
                HttpMethod.DELETE, new HttpEntity<>(admin()), Map.class);
        // 守卫只看是否还有「可用批次」；作废批次后即可删除，见 GreenLotVoidIT
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("message")).contains("可用批次");
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffCannotDeleteGreenBean() {
        String suffix = unique();
        int beanId = createGreenBean("员工禁删生豆-" + suffix);
        HttpHeaders admin = admin();
        String staffName = "it_staff_" + suffix;
        rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", staffName, "name", "员工测试",
                        "password", "Staff@1234", "role", "STAFF", "phone", "13900000000"), admin), Map.class);

        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/green-beans/" + beanId,
                HttpMethod.DELETE, new HttpEntity<>(login(staffName, "Staff@1234")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 仍未被删除
        ResponseEntity<Map> list = rest.exchange(
                "/api/v1/archive/green-beans?keyword=" + "员工禁删生豆-" + suffix, HttpMethod.GET,
                new HttpEntity<>(admin), Map.class);
        Map<String, Object> page = (Map<String, Object>) list.getBody().get("data");
        assertThat(((List<?>) page.get("list"))).hasSize(1);
    }

    // ---------- helpers ----------

    private String unique() {
        return String.valueOf(System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private int createSupplier(String name) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/suppliers", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "status", "ACTIVE"), admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (int) ((Map<String, Object>) resp.getBody().get("data")).get("id");
    }

    @SuppressWarnings("unchecked")
    private int createGreenBean(int defaultSupplierId, String name) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/green-beans", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "status", "ACTIVE",
                        "defaultSupplierId", defaultSupplierId), admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (int) ((Map<String, Object>) resp.getBody().get("data")).get("id");
    }

    @SuppressWarnings("unchecked")
    private int createGreenBean(String name) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/green-beans", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "status", "ACTIVE"), admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (int) ((Map<String, Object>) resp.getBody().get("data")).get("id");
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
