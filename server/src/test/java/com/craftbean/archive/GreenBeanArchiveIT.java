package com.craftbean.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
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

/** 生豆档案：单品/拼配类型 + 新增字段 + 推荐方向（冲煮方式多选） */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreenBeanArchiveIT {
    @Autowired TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void createSingleOriginBeanPersistsAllFields() {
        String suffix = unique();
        Map<String, Object> created = createBean(singleOriginBody(suffix, new HashMap<>()));

        assertThat(created.get("beanType")).isEqualTo("SINGLE");
        assertThat(created.get("code")).isEqualTo("GBE-" + suffix);
        assertThat(created.get("region")).isEqualTo("耶加雪菲");
        assertThat(created.get("season")).isEqualTo("2025/2026");
        assertThat(((Number) created.get("moisturePct")).doubleValue()).isEqualTo(10.5);
        assertThat(((Number) created.get("altitudeM")).intValue()).isEqualTo(1950);
        assertThat(created.get("packageSpec")).isEqualTo("60kg/袋");
        assertThat(created.get("recommendedRoast")).isEqualTo("中浅");
        assertThat(created.get("flavor")).isEqualTo("茉莉花、柑橘");

        ResponseEntity<Map> listResp = rest.exchange(
                "/api/v1/archive/green-beans?keyword=" + encode("单品豆-" + suffix) + "&includeMethods=true",
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        Map<String, Object> page = (Map<String, Object>) listResp.getBody().get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("region")).isEqualTo("耶加雪菲");
        assertThat(list.get(0).get("flavor")).isEqualTo("茉莉花、柑橘");
    }

    @Test
    @SuppressWarnings("unchecked")
    void beanTypeDefaultsToSingle() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "默认单品-" + unique());
        Map<String, Object> created = createBean(body);
        assertThat(created.get("beanType")).isEqualTo("SINGLE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createBlendBeanAndRejectInvalidValues() {
        String suffix = unique();
        Map<String, Object> body = new HashMap<>();
        body.put("beanType", "BLEND");
        body.put("name", "拼配豆-" + suffix);
        body.put("recipe", "巴西日晒60% + 耶加水洗40%");
        body.put("agtronColor", 58);
        body.put("recommendedRoast", "中");
        body.put("flavor", "坚果、黑巧克力");
        Map<String, Object> created = createBean(body);

        assertThat(created.get("beanType")).isEqualTo("BLEND");
        assertThat(created.get("recipe")).isEqualTo("巴西日晒60% + 耶加水洗40%");
        assertThat(((Number) created.get("agtronColor")).intValue()).isEqualTo(58);
        assertThat(created.get("flavor")).isEqualTo("坚果、黑巧克力");

        assertBad(beanBodyWith("beanType", "BLEND", "name", "拼配色值超界-" + suffix, "agtronColor", 150),
                "色值");
        assertBad(beanBodyWith("beanType", "BLEND", "name", "拼配色值负-" + suffix, "agtronColor", -1),
                "色值");
        assertBad(beanBodyWith("beanType", "BLEND", "name", "拼配烘焙度非法-" + suffix, "recommendedRoast", "炭"),
                "烘焙");
        assertBad(beanBodyWith("beanType", "BOGUS", "name", "类型非法-" + suffix), "类型");
        assertBad(beanBodyWith("beanType", "SINGLE", "name", "含水率非法-" + suffix, "moisturePct", 105),
                "含水率");
    }

    @Test
    @SuppressWarnings("unchecked")
    void brewMethodsAreStoredReplacedAndOnlyReturnedWhenIncludeMethods() {
        String suffix = unique();
        int m1 = createBrewMethod("冲煮A-" + suffix);
        int m2 = createBrewMethod("冲煮B-" + suffix);

        Map<String, Object> body = singleOriginBody(suffix, new HashMap<>());
        body.put("brewMethodIds", List.of(m1, m2));
        int beanId = ((Number) createBean(body).get("id")).intValue();

        List<Map<String, Object>> row = listByName("单品豆-" + suffix, true);
        assertThat((List<Integer>) row.get(0).get("brewMethodIds")).containsExactlyInAnyOrder(m1, m2);
        assertThat((List<String>) row.get(0).get("brewMethodNames"))
                .containsExactlyInAnyOrder("冲煮A-" + suffix, "冲煮B-" + suffix);

        // 默认不装配（下拉等场景不带 includeMethods）
        List<Map<String, Object>> plain = listByName("单品豆-" + suffix, false);
        assertThat(plain.get(0).get("brewMethodIds")).isNull();
        assertThat(plain.get(0).get("brewMethodNames")).isNull();

        // 更新 = 整存整删，只保留 m2
        Map<String, Object> updateBody = singleOriginBody(suffix, new HashMap<>());
        updateBody.put("brewMethodIds", List.of(m2));
        ResponseEntity<Map> updateResp = rest.exchange("/api/v1/archive/green-beans/" + beanId,
                HttpMethod.PUT, new HttpEntity<>(updateBody, admin()), Map.class);
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> after = listByName("单品豆-" + suffix, true);
        assertThat((List<Integer>) after.get(0).get("brewMethodIds")).containsExactly(m2);

        // 引用不存在的冲煮方式 → 400
        ResponseEntity<Map> bad = rest.exchange("/api/v1/archive/green-beans/" + beanId,
                HttpMethod.PUT, new HttpEntity<>(assignBody(m2, 99999999), admin()), Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void blendBeanCanBePurchasedLikeSingleOrigin() {
        String suffix = unique();
        int supplierId = createSupplier("拼配供货商-" + suffix);
        Map<String, Object> body = new HashMap<>();
        body.put("beanType", "BLEND");
        body.put("name", "可采购拼配-" + suffix);
        int beanId = ((Number) createBean(body).get("id")).intValue();

        ResponseEntity<Map> purchase = rest.exchange("/api/v1/greenbean/purchases", HttpMethod.POST,
                new HttpEntity<>(Map.of("supplierId", supplierId, "greenBeanId", beanId,
                        "purchaseDate", "2026-09-06", "quantityKg", new BigDecimal("8.000"),
                        "unitPrice", new BigDecimal("22.00")), admin()), Map.class);
        assertThat(purchase.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> lot = (Map<String, Object>) purchase.getBody().get("data");
        assertThat(((Number) lot.get("remainingKg")).doubleValue()).isEqualTo(8.0);
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> singleOriginBody(String suffix, Map<String, Object> extra) {
        Map<String, Object> body = new HashMap<>();
        body.put("beanType", "SINGLE");
        body.put("code", "GBE-" + suffix);
        body.put("name", "单品豆-" + suffix);
        body.put("origin", "埃塞俄比亚");
        body.put("region", "耶加雪菲");
        body.put("process", "水洗");
        body.put("variety", "Heirloom");
        body.put("season", "2025/2026");
        body.put("moisturePct", 10.5);
        body.put("altitudeM", 1950);
        body.put("packageSpec", "60kg/袋");
        body.put("recommendedRoast", "中浅");
        body.put("flavor", "茉莉花、柑橘");
        body.put("status", "ACTIVE");
        body.putAll(extra);
        return body;
    }

    private Map<String, Object> beanBodyWith(Object... kv) {
        Map<String, Object> body = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) body.put((String) kv[i], kv[i + 1]);
        return body;
    }

    private Map<String, Object> assignBody(int keepId, int bogusId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "单品豆-assign");
        body.put("beanType", "SINGLE");
        body.put("brewMethodIds", List.of(keepId, bogusId));
        return body;
    }

    private String encode(String s) {
        return s.replace(" ", "%20");
    }

    private String unique() {
        return String.valueOf(System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createBean(Map<String, Object> body) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/green-beans", HttpMethod.POST,
                new HttpEntity<>(body, admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listByName(String name, boolean includeMethods) {
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/archive/green-beans?keyword=" + name + "&includeMethods=" + includeMethods,
                HttpMethod.GET, new HttpEntity<>(admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> page = (Map<String, Object>) resp.getBody().get("data");
        return (List<Map<String, Object>>) page.get("list");
    }

    @SuppressWarnings("unchecked")
    private void assertBad(Map<String, Object> body, String messageHint) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/green-beans", HttpMethod.POST,
                new HttpEntity<>(body, admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("message")).contains(messageHint);
    }

    private int createBrewMethod(String name) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/brew-methods", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "status", "ACTIVE"), admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) ((Map<String, Object>) resp.getBody().get("data")).get("id")).intValue();
    }

    private int createSupplier(String name) {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/archive/suppliers", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", name, "status", "ACTIVE"), admin()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) ((Map<String, Object>) resp.getBody().get("data")).get("id")).intValue();
    }

    @SuppressWarnings("unchecked")
    private HttpHeaders admin() {
        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Admin@123456", "phoneTail", "0000"), Map.class);
        String token = (String) ((Map<String, Object>) login.getBody().get("data")).get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
