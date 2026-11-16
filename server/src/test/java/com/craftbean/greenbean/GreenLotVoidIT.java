package com.craftbean.greenbean;

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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 生豆批次作废：单据走 status=VOID，不物理删除；
 * 未领用部分用反向流水 PURCHASE_VOID 冲销，保证库存流水台账净额归零。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreenLotVoidIT {
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void voidUnconsumedLotMarksVoidAndReversesFullQuantity() {
        HttpHeaders admin = admin();
        String suffix = unique();
        int supplierId = createSupplier(suffix, admin);
        int beanId = createGreenBean(suffix, admin);
        Map<String, Object> lot = purchase(supplierId, beanId, "5.000", "20.00", admin);
        int lotId = (int) lot.get("id");

        ResponseEntity<Map> resp = voidLot(lotId, "入库建错", admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("status")).isEqualTo("VOID");

        // 反向流水把台账冲平：+5.000 入库，-5.000 作废
        List<Map<String, Object>> movements = movements(lotId, admin);
        assertThat(movements).hasSize(2);
        Map<String, Object> reversal = movements.stream()
                .filter(m -> "PURCHASE_VOID".equals(m.get("bizType"))).findFirst().orElseThrow();
        assertThat(asDecimal(reversal.get("changeKg"))).isEqualByComparingTo(new BigDecimal("-5.000"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void voidPartiallyConsumedLotReversesOnlyRemaining() {
        HttpHeaders admin = admin();
        String suffix = unique();
        int supplierId = createSupplier(suffix, admin);
        int beanId = createGreenBean(suffix, admin);
        Map<String, Object> lot = purchase(supplierId, beanId, "5.000", "20.00", admin);
        int lotId = (int) lot.get("id");

        // 目前尚无领用出库功能（S3 只做采购入库），直接改余额模拟「已领用 3kg」
        jdbc.update("UPDATE green_lot SET remaining_kg = ? WHERE id = ?", new BigDecimal("2.000"), lotId);

        ResponseEntity<Map> resp = voidLot(lotId, "只剩 2kg 报废", admin);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> movements = movements(lotId, admin);
        Map<String, Object> reversal = movements.stream()
                .filter(m -> "PURCHASE_VOID".equals(m.get("bizType"))).findFirst().orElseThrow();
        // 只冲销剩余量，已领用的 3kg 保留历史
        assertThat(asDecimal(reversal.get("changeKg"))).isEqualByComparingTo(new BigDecimal("-2.000"));
    }

    @Test
    void cannotVoidLotTwice() {
        HttpHeaders admin = admin();
        String suffix = unique();
        int supplierId = createSupplier(suffix, admin);
        int beanId = createGreenBean(suffix, admin);
        int lotId = (int) purchase(supplierId, beanId, "5.000", "20.00", admin).get("id");

        assertThat(voidLot(lotId, null, admin).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> again = voidLot(lotId, null, admin);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) again.getBody().get("message")).contains("已作废");
    }

    @Test
    void voidUnknownLotReturns404() {
        ResponseEntity<Map> resp = voidLot(999_999_999, null, admin());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffCannotVoidLot() {
        HttpHeaders admin = admin();
        String suffix = unique();
        int supplierId = createSupplier(suffix, admin);
        int beanId = createGreenBean(suffix, admin);
        int lotId = (int) purchase(supplierId, beanId, "5.000", "20.00", admin).get("id");

        String staffName = "it_staff_" + suffix;
        rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", staffName, "name", "员工测试",
                        "password", "Staff@1234", "role", "STAFF", "phone", "13900000000"), admin), Map.class);

        ResponseEntity<Map> resp = voidLot(lotId, null, headers(staffName, "Staff@1234"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 批次未被改动
        assertThat(lotStatus(lotId)).isEqualTo("AVAILABLE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void greenBeanBecomesDeletableAfterItsLotIsVoided() {
        HttpHeaders admin = admin();
        String suffix = unique();
        int supplierId = createSupplier(suffix, admin);
        int beanId = createGreenBean(suffix, admin);
        int lotId = (int) purchase(supplierId, beanId, "5.000", "20.00", admin).get("id");

        // 有可用批次时生豆删不掉
        ResponseEntity<Map> blocked = rest.exchange("/api/v1/archive/green-beans/" + beanId,
                HttpMethod.DELETE, new HttpEntity<>(admin), Map.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(voidLot(lotId, "误建采购", admin).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 批次作废后生豆可删（守卫只看是否还有可用批次）
        ResponseEntity<Map> deleted = rest.exchange("/api/v1/archive/green-beans/" + beanId,
                HttpMethod.DELETE, new HttpEntity<>(admin), Map.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @SuppressWarnings("unchecked")
    void lotAndMovementListsShowPlaceholderAfterGreenBeanDeleted() {
        HttpHeaders admin = admin();
        String suffix = unique();
        int supplierId = createSupplier(suffix, admin);
        int beanId = createGreenBean(suffix, admin);
        Map<String, Object> lot = purchase(supplierId, beanId, "5.000", "20.00", admin);
        int lotId = (int) lot.get("id");
        String lotNo = (String) lot.get("lotNo");

        assertThat(voidLot(lotId, "误建采购", admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.exchange("/api/v1/archive/green-beans/" + beanId, HttpMethod.DELETE,
                new HttpEntity<>(admin), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 生豆已删，但作废批次与历史流水仍需可读：生豆名回退成占位符而非空白
        ResponseEntity<Map> lotsResp = rest.exchange(
                "/api/v1/greenbean/inventory/lots?keyword=" + lotNo,
                HttpMethod.GET, new HttpEntity<>(admin), Map.class);
        List<Map<String, Object>> lotList = (List<Map<String, Object>>) data(lotsResp).get("list");
        assertThat(lotList).hasSize(1);
        assertThat(lotList.get(0).get("greenBeanName")).isEqualTo("(已删除)");

        assertThat(movements(lotId, admin))
                .isNotEmpty()
                .allSatisfy(m -> assertThat(m.get("greenBeanName")).isEqualTo("(已删除)"));
    }

    // ---------- helpers ----------

    private String unique() {
        return String.valueOf(System.currentTimeMillis());
    }

    private BigDecimal asDecimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> voidLot(int lotId, String reason, HttpHeaders headers) {
        Map<String, Object> body = reason == null ? Map.of() : Map.of("reason", reason);
        return rest.exchange("/api/v1/greenbean/lots/" + lotId + "/void",
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> movements(int lotId, HttpHeaders headers) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/greenbean/inventory/movements?lotId=" + lotId,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return (List<Map<String, Object>>) data(resp).get("list");
    }

    private String lotStatus(int lotId) {
        return jdbc.queryForObject("SELECT status FROM green_lot WHERE id = ?", String.class, lotId);
    }

    @SuppressWarnings("unchecked")
    private int createSupplier(String suffix, HttpHeaders admin) {
        Map<String, Object> supplier = data(rest.exchange("/api/v1/archive/suppliers", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "作废供货商-" + suffix, "status", "ACTIVE"), admin), Map.class));
        return (int) supplier.get("id");
    }

    @SuppressWarnings("unchecked")
    private int createGreenBean(String suffix, HttpHeaders admin) {
        Map<String, Object> bean = data(rest.exchange("/api/v1/archive/green-beans", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "作废生豆-" + suffix, "status", "ACTIVE"), admin), Map.class));
        return (int) bean.get("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> purchase(int supplierId, int beanId, String quantityKg,
                                        String unitPrice, HttpHeaders admin) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/greenbean/purchases", HttpMethod.POST,
                new HttpEntity<>(Map.of("supplierId", supplierId, "greenBeanId", beanId,
                        "purchaseDate", "2026-09-05", "quantityKg", new BigDecimal(quantityKg),
                        "unitPrice", new BigDecimal(unitPrice)), admin), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return data(resp);
    }

    private HttpHeaders admin() {
        return headers("admin", "Admin@123456");
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders headers(String username, String password) {
        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", username, "password", password, "phoneTail", "0000"), Map.class);
        String token = (String) ((Map<String, Object>) login.getBody().get("data")).get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
