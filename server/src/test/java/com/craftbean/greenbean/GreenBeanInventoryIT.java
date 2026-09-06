package com.craftbean.greenbean;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
class GreenBeanInventoryIT {
    @Autowired TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void purchaseCreatesLotAndMovementAndRejectsDisabledSupplierAndReadonlyWrite() {
        HttpHeaders admin = headers("admin", "Admin@123456");
        String suffix = String.valueOf(System.currentTimeMillis());
        Map<String, Object> supplierBody = Map.of("name", "IT供货商-" + suffix, "status", "ACTIVE");
        Map<String, Object> supplier = data(rest.exchange("/api/v1/archive/suppliers", HttpMethod.POST,
                new HttpEntity<>(supplierBody, admin), Map.class));
        Integer supplierId = (Integer) supplier.get("id");
        Map<String, Object> beanBody = Map.of("name", "IT生豆-" + suffix, "origin", "IT", "status", "ACTIVE");
        Map<String, Object> bean = data(rest.exchange("/api/v1/archive/green-beans", HttpMethod.POST,
                new HttpEntity<>(beanBody, admin), Map.class));
        Integer beanId = (Integer) bean.get("id");

        String lotNo = "GB-IT-" + suffix;
        Map<String, Object> purchase = Map.of("supplierId", supplierId, "greenBeanId", beanId,
                "purchaseDate", "2026-09-04", "lotNo", lotNo, "quantityKg", new BigDecimal("12.500"),
                "unitPrice", new BigDecimal("18.00"));
        ResponseEntity<Map> purchaseResponse = rest.exchange("/api/v1/greenbean/purchases", HttpMethod.POST,
                new HttpEntity<>(purchase, admin), Map.class);
        assertThat(purchaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> lot = data(purchaseResponse);
        assertThat(lot.get("lotNo")).isEqualTo(lotNo);
        assertThat(lot.get("remainingKg")).isEqualTo(12.5);

        ResponseEntity<Map> movementResponse = rest.exchange(
                "/api/v1/greenbean/inventory/movements?lotId=" + lot.get("id"), HttpMethod.GET,
                new HttpEntity<>(admin), Map.class);
        Map<String, Object> movementData = (Map<String, Object>) movementResponse.getBody().get("data");
        assertThat(movementData.get("total")).isEqualTo(1);

        rest.exchange("/api/v1/archive/suppliers/" + supplierId + "/status?status=DISABLED",
                HttpMethod.POST, new HttpEntity<>(admin), Map.class);
        ResponseEntity<Map> rejected = rest.exchange("/api/v1/greenbean/purchases", HttpMethod.POST,
                new HttpEntity<>(purchase, admin), Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String readonlyName = "it_ro_" + suffix;
        rest.exchange("/api/v1/system/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", readonlyName, "name", "只读测试",
                        "password", "Read@1234", "role", "READONLY", "phone", "13900000000"), admin), Map.class);
        HttpHeaders readonly = headers(readonlyName, "Read@1234");
        ResponseEntity<Map> denied = rest.exchange("/api/v1/archive/suppliers", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "readonly-" + suffix), readonly), Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
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
