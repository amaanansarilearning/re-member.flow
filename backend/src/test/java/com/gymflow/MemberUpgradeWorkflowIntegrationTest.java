package com.gymflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MemberUpgradeWorkflowIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_RESPONSE = new ParameterizedTypeReference<>() {};

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<?> request(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set(HttpHeaders.COOKIE, token);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("XSRF-TOKEN=([^;]+)").matcher(token);
            if (m.find()) {
                headers.set("X-XSRF-TOKEN", m.group(1));
            }
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String extractCookies(ResponseEntity<?> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        List<String> cookies = new ArrayList<>();
        for (String c : setCookies) {
            if (c.startsWith("gym_jwt=") || c.startsWith("XSRF-TOKEN=")) {
                cookies.add(c.split(";")[0]);
            }
        }
        return String.join("; ", cookies);
    }

    private Map<String, Object> login(String email, String password) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                request(null, Map.of("email", email, "password", password)), MAP_RESPONSE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        Map<String, Object> result = body != null ? new HashMap<>(body) : new HashMap<>();
        String cookies = extractCookies(response);
        ResponseEntity<Map<String, String>> csrf = rest.exchange(
                url("/api/csrf"), HttpMethod.GET,
                request(cookies, null), new ParameterizedTypeReference<Map<String, String>>() {});
        assertThat(csrf.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csrfCookie = java.util.Optional.ofNullable(csrf.getHeaders().get(HttpHeaders.SET_COOKIE))
                .orElse(List.of()).stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .map(cookie -> cookie.split(";", 2)[0])
                .filter(cookie -> cookie.length() > "XSRF-TOKEN=".length())
                .findFirst()
                .orElseGet(() -> java.util.Arrays.stream(cookies.split(";"))
                        .map(String::trim)
                        .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                        .filter(cookie -> cookie.length() > "XSRF-TOKEN=".length())
                        .findFirst()
                        .orElseThrow());
        String jwtCookie = java.util.Arrays.stream(cookies.split(";"))
                .map(String::trim)
                .filter(cookie -> cookie.startsWith("gym_jwt="))
                .findFirst()
                .orElseThrow();
        result.put("token", jwtCookie + "; " + csrfCookie);
        return result;
    }

    @Test
    void memberCannotDirectlyUpgrade_MustUseUpgradeApprovalWorkflow() {
        String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");

        ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(
                url("/api/plans"), HttpMethod.GET,
                request(ownerToken, null), LIST_RESPONSE);
        assertThat(plans.getStatusCode()).isEqualTo(HttpStatus.OK);
        long plan1Id = ((Number) plans.getBody().get(0).get("id")).longValue();
        long plan2Id = ((Number) plans.getBody().get(1).get("id")).longValue();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String memberEmail = "upgrade-member-" + System.nanoTime() + "@example.test";
        String memberPassword = "MemberPassword123!";

        // 1. Register a member with plan 1
        Map<String, Object> regPayload = Map.of(
                "fullName", "Upgrade Workflow Member",
                "email", memberEmail,
                "password", memberPassword,
                "planId", plan1Id,
                "discountPercent", 0,
                "startDate", today.toString()
        );
        ResponseEntity<Map<String, Object>> enrolled = rest.exchange(
                url("/api/memberships"), HttpMethod.POST,
                request(ownerToken, regPayload), MAP_RESPONSE);
        assertThat(enrolled.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long membershipId = ((Number) enrolled.getBody().get("id")).longValue();

        // 2. Member logs in
        String memberToken = (String) login(memberEmail, memberPassword).get("token");

        // 3. Attempt direct upgrade as member -> MUST FAIL with 403 FORBIDDEN
        ResponseEntity<Map<String, Object>> directUpgrade = rest.exchange(
                url("/api/memberships/" + membershipId + "/upgrade"), HttpMethod.POST,
                request(memberToken, Map.of(
                        "newPlanId", plan2Id,
                        "paymentMethod", "CASH",
                        "topUpPaid", 100
                )), MAP_RESPONSE);
        assertThat(directUpgrade.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. Member submits upgrade request -> MUST SUCCEED with 201 CREATED
        ResponseEntity<Map<String, Object>> submitRequest = rest.exchange(
                url("/api/memberships/" + membershipId + "/upgrade-requests"), HttpMethod.POST,
                request(memberToken, Map.of(
                        "newPlanId", plan2Id,
                        "paymentMethod", "UPI",
                        "transactionId", "UPI-TEST-12345",
                        "notes", "Requesting package upgrade"
                )), MAP_RESPONSE);
        assertThat(submitRequest.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> reqBody = submitRequest.getBody();
        assertThat(reqBody.get("status")).isEqualTo("PENDING");
        long requestId = ((Number) reqBody.get("id")).longValue();

        // 5. Duplicate submission while pending -> MUST FAIL with 409 CONFLICT
        ResponseEntity<Map<String, Object>> duplicateSubmit = rest.exchange(
                url("/api/memberships/" + membershipId + "/upgrade-requests"), HttpMethod.POST,
                request(memberToken, Map.of(
                        "newPlanId", plan2Id,
                        "paymentMethod", "CARD"
                )), MAP_RESPONSE);
        assertThat(duplicateSubmit.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 6. Owner views pending queue
        ResponseEntity<List<Map<String, Object>>> queue = rest.exchange(
                url("/api/membership-upgrade-requests?status=PENDING"), HttpMethod.GET,
                request(ownerToken, null), LIST_RESPONSE);
        assertThat(queue.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(queue.getBody()).anyMatch(r -> ((Number) r.get("id")).longValue() == requestId);

        // 7. Owner approves the upgrade request -> 200 OK
        ResponseEntity<Map<String, Object>> approved = rest.exchange(
                url("/api/membership-upgrade-requests/" + requestId + "/approve"), HttpMethod.POST,
                request(ownerToken, Map.of(
                        "paymentMethod", "UPI",
                        "transactionId", "UPI-TEST-12345-VERIFIED"
                )), MAP_RESPONSE);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status")).isEqualTo("APPROVED");

        // 8. Concurrent second approval attempt -> MUST FAIL with 409 CONFLICT
        ResponseEntity<Map<String, Object>> secondApprove = rest.exchange(
                url("/api/membership-upgrade-requests/" + requestId + "/approve"), HttpMethod.POST,
                request(ownerToken, Map.of(
                        "paymentMethod", "UPI"
                )), MAP_RESPONSE);
        assertThat(secondApprove.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}

