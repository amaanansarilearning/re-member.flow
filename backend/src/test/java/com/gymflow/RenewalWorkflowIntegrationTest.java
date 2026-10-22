package com.gymflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

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
public class RenewalWorkflowIntegrationTest {

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
                request(cookies, null),
                new ParameterizedTypeReference<Map<String, String>>() {});
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
    void memberCannotDirectlyRenew_MustUseRenewalApprovalWorkflow() {
        String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");

        ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(
                url("/api/plans"), HttpMethod.GET,
                request(ownerToken, null), LIST_RESPONSE);
        assertThat(plans.getStatusCode()).isEqualTo(HttpStatus.OK);
        long planId = ((Number) plans.getBody().get(0).get("id")).longValue();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String memberEmail = "workflow-member-" + System.nanoTime() + "@example.test";
        String memberPassword = "MemberPassword123!";

        // 1. Register a member
        Map<String, Object> regPayload = Map.of(
                "fullName", "Workflow Member",
                "email", memberEmail,
                "password", memberPassword,
                "planId", planId,
                "discountPercent", 0,
                "startDate", today.toString()
        );
        ResponseEntity<Map<String, Object>> enrolled = rest.exchange(
                url("/api/memberships"), HttpMethod.POST,
                request(ownerToken, regPayload), MAP_RESPONSE);
        assertThat(enrolled.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long membershipId = ((Number) enrolled.getBody().get("id")).longValue();
        LocalDate endDate = LocalDate.parse((String) enrolled.getBody().get("endDate"));

        // 2. Member logs in
        String memberToken = (String) login(memberEmail, memberPassword).get("token");

        // 3. Member tries to directly call /memberships/{id}/renew -> MUST BE FORBIDDEN (403)
        Map<String, Object> directRenewalPayload = Map.of(
                "planId", planId,
                "discountPercent", 0,
                "startDate", endDate.plusDays(1).toString(),
                "paymentMethod", "CASH"
        );
        ResponseEntity<Map<String, Object>> directRenewAttempt = rest.exchange(
                url("/api/memberships/" + membershipId + "/renew"), HttpMethod.POST,
                request(memberToken, directRenewalPayload), MAP_RESPONSE);
        assertThat(directRenewAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. Member submits a renewal request through workflow -> CREATED (201) with status PENDING
        Map<String, Object> submissionPayload = Map.of(
                "planId", planId,
                "startDate", endDate.plusDays(1).toString(),
                "paymentMethod", "UPI",
                "transactionId", "UPI-REF-123456",
                "notes", "Paid via Google Pay"
        );
        ResponseEntity<Map<String, Object>> requestCreated = rest.exchange(
                url("/api/memberships/" + membershipId + "/renewal-requests"), HttpMethod.POST,
                request(memberToken, submissionPayload), MAP_RESPONSE);
        assertThat(requestCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(requestCreated.getBody().get("status")).isEqualTo("PENDING");
        assertThat(requestCreated.getBody().get("paymentMethod")).isEqualTo("UPI");
        assertThat(requestCreated.getBody().get("transactionId")).isEqualTo("UPI-REF-123456");
        long requestId = ((Number) requestCreated.getBody().get("id")).longValue();

        // 5. Duplicate request before review is rejected with CONFLICT (409)
        ResponseEntity<Map<String, Object>> duplicateAttempt = rest.exchange(
                url("/api/memberships/" + membershipId + "/renewal-requests"), HttpMethod.POST,
                request(memberToken, submissionPayload), MAP_RESPONSE);
        assertThat(duplicateAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 6. Owner views pending requests
        ResponseEntity<List<Map<String, Object>>> ownerRequests = rest.exchange(
                url("/api/membership-renewal-requests?status=PENDING"), HttpMethod.GET,
                request(ownerToken, null), LIST_RESPONSE);
        assertThat(ownerRequests.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownerRequests.getBody()).anyMatch(r -> ((Number) r.get("id")).longValue() == requestId);

        // 7. Owner approves the renewal request
        Map<String, Object> approvalPayload = Map.of(
                "discountPercent", 5,
                "paymentMethod", "UPI",
                "transactionId", "UPI-REF-123456-VERIFIED"
        );
        ResponseEntity<Map<String, Object>> approved = rest.exchange(
                url("/api/membership-renewal-requests/" + requestId + "/approve"), HttpMethod.POST,
                request(ownerToken, approvalPayload), MAP_RESPONSE);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status")).isEqualTo("APPROVED");
        assertThat(((Number) approved.getBody().get("resultingMembershipId")).longValue()).isPositive();

        // 8. Membership now has renewal scheduled
        ResponseEntity<Map<String, Object>> afterCheck = rest.exchange(
                url("/api/memberships/" + membershipId + "/access"), HttpMethod.GET,
                request(memberToken, null), MAP_RESPONSE);
        assertThat(afterCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

