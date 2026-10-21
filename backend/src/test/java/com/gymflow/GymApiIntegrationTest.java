package com.gymflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymflow.domain.GymBranch;
import com.gymflow.domain.Membership;
import com.gymflow.domain.MembershipStatus;
import com.gymflow.domain.MembershipPlan;
import com.gymflow.domain.MembershipReceipt;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.MembershipCancellationRepository;
import com.gymflow.repository.MembershipReceiptRepository;
import com.gymflow.repository.PlanRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.service.RenewalReminderService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GymApiIntegrationTest {
        private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
        };
        private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_RESPONSE = new ParameterizedTypeReference<>() {
        };

        @LocalServerPort
        private int port;

        @Autowired
        private TestRestTemplate rest;

        @Autowired
        private UserRepository users;

        @Autowired
        private PlanRepository planRepository;

        @Autowired
        private BranchRepository branches;

        @Autowired
        private MembershipRepository memberships;

        @Autowired
        private MembershipCancellationRepository membershipCancellations;

        @Autowired
        private MembershipReceiptRepository receipts;

        @Autowired
        private RenewalReminderService renewalReminderService;

        @Test
        void ownerWriteWithoutCsrfTokenGetsAnActionableForbiddenResponse() {
                String cookies = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                String jwtCookie = java.util.Arrays.stream(cookies.split(";"))
                                .map(String::trim)
                                .filter(cookie -> cookie.startsWith("gym_jwt="))
                                .findFirst()
                                .orElseThrow();
                Map<String, Object> registration = Map.of(
                                "fullName", "CSRF Test Member",
                                "email", "csrf-" + System.nanoTime() + "@example.test",
                                "password", "MemberPass123",
                                "planId", 1,
                                "discountPercent", 0);

                ResponseEntity<Map<String, Object>> response = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, request(jwtCookie, registration), MAP_RESPONSE);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(response.getBody().get("message").toString())
                                .contains("security token is missing or expired");
        }

        @Test
        void ownerCanCreateMembershipAfterFetchingCsrfToken() {
                String ownerCookies = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                String jwtCookie = java.util.Arrays.stream(ownerCookies.split(";"))
                                .map(String::trim)
                                .filter(cookie -> cookie.startsWith("gym_jwt="))
                                .findFirst()
                                .orElseThrow();
                ResponseEntity<Map<String, String>> csrf = rest.exchange(url("/api/csrf"), HttpMethod.GET,
                                request(ownerCookies, null),
                                new ParameterizedTypeReference<Map<String, String>>() {
                                });
                assertThat(csrf.getStatusCode()).isEqualTo(HttpStatus.OK);
                String csrfToken = csrf.getBody().get("token");
                assertThat(csrfToken).isNotBlank();
                String csrfCookie = java.util.Optional.ofNullable(csrf.getHeaders().get(HttpHeaders.SET_COOKIE))
                                .orElse(List.of()).stream()
                                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                                .map(cookie -> cookie.split(";", 2)[0])
                                .filter(cookie -> cookie.length() > "XSRF-TOKEN=".length())
                                .findFirst()
                                .orElseGet(() -> java.util.Arrays.stream(ownerCookies.split(";"))
                                                .map(String::trim)
                                                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                                                .filter(cookie -> cookie.length() > "XSRF-TOKEN=".length())
                                                .findFirst()
                                                .orElseThrow());
                assertThat(csrfCookie.substring("XSRF-TOKEN=".length())).isEqualTo(csrfToken);

                ResponseEntity<Map<String, String>> refreshedCsrf = rest.exchange(url("/api/csrf"), HttpMethod.GET,
                                request(jwtCookie, null),
                                new ParameterizedTypeReference<Map<String, String>>() {
                                });
                String refreshedToken = refreshedCsrf.getBody().get("token");
                String refreshedCookie = refreshedCsrf.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                                .map(cookie -> cookie.split(";", 2)[0])
                                .filter(cookie -> cookie.length() > "XSRF-TOKEN=".length())
                                .findFirst()
                                .orElseThrow();
                assertThat(refreshedToken).isNotEqualTo(csrfToken);
                assertThat(refreshedCookie.substring("XSRF-TOKEN=".length())).isEqualTo(refreshedToken);

                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(jwtCookie + "; " + refreshedCookie, null), LIST_RESPONSE);
                long planId = ((Number) plans.getBody().get(0).get("id")).longValue();
                Map<String, Object> registration = Map.of(
                                "fullName", "CSRF Verified Member",
                                "email", "csrf-ok-" + System.nanoTime() + "@example.test",
                                "password", "MemberPass123",
                                "planId", planId,
                                "discountPercent", 0);
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.COOKIE, jwtCookie + "; " + refreshedCookie);
                headers.set("X-XSRF-TOKEN", refreshedToken);
                headers.setContentType(MediaType.APPLICATION_JSON);

                ResponseEntity<Map<String, Object>> created = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, new HttpEntity<>(registration, headers), MAP_RESPONSE);

                assertThat(created.getStatusCode()).as("Response: " + created.getBody())
                                .isEqualTo(HttpStatus.CREATED);
        }

        @Test
        void invalidMembershipDetailsReturnFieldSpecificValidationErrors() {
                String ownerCookies = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerCookies, null), LIST_RESPONSE);
                long planId = ((Number) plans.getBody().get(0).get("id")).longValue();
                Map<String, Object> registration = Map.of(
                                "fullName", "Validation Test Member",
                                "email", "invalid-phone-" + System.nanoTime() + "@example.test",
                                "phoneNumber", "not a phone",
                                "password", "MemberPass123",
                                "planId", planId,
                                "discountPercent", 0);

                ResponseEntity<Map<String, Object>> response = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, request(ownerCookies, registration), MAP_RESPONSE);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody().get("message"))
                                .isEqualTo("Some membership details are invalid. Review the fields and try again.");
                List<?> errors = (List<?>) response.getBody().get("errors");
                assertThat(errors).anySatisfy(error -> {
                        Map<?, ?> fieldError = (Map<?, ?>) error;
                        assertThat(fieldError.get("field")).isEqualTo("phoneNumber");
                        assertThat(fieldError.get("defaultMessage")).isInstanceOf(String.class);
                        assertThat((String) fieldError.get("defaultMessage")).isNotBlank();
                });
        }

        @Test
        void ownerAnalyticsSeparatesNewTermsRenewalsAndEstimatesTaxFromRecordedRevenue() {
                String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                long planId = ((Number) plans.getBody().get(0).get("id")).longValue();
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
                Map<String, Object> registration = Map.of(
                                "fullName", "Analytics Member",
                                "email", "analytics-" + System.nanoTime() + "@example.test",
                                "password", "MemberPass123",
                                "planId", planId,
                                "discountPercent", 15,
                                "startDate", today.toString());
                ResponseEntity<Map<String, Object>> enrolled = rest.exchange(url("/api/memberships"), HttpMethod.POST,
                                request(ownerToken, registration), MAP_RESPONSE);
                assertThat(enrolled.getStatusCode()).isEqualTo(HttpStatus.CREATED);

                Map<String, Object> renewalRequest = Map.of(
                                "planId", planId,
                                "discountPercent", 15,
                                "startDate", LocalDate.parse((String) enrolled.getBody().get("endDate"))
                                                .plusDays(1).toString());
                ResponseEntity<Map<String, Object>> renewal = rest.exchange(
                                url("/api/memberships/" + enrolled.getBody().get("id") + "/renew"), HttpMethod.POST,
                                request(ownerToken, renewalRequest), MAP_RESPONSE);
                assertThat(renewal.getStatusCode()).isEqualTo(HttpStatus.CREATED);

                ResponseEntity<Map<String, Object>> analytics = rest.exchange(
                                url("/api/analytics?from=" + today + "&to=" + today), HttpMethod.GET,
                                request(ownerToken, null), MAP_RESPONSE);
                assertThat(analytics.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(((Number) analytics.getBody().get("totalMembershipsSold")).longValue()).isEqualTo(2);
                assertThat(((Number) analytics.getBody().get("newMembers")).longValue()).isEqualTo(1);
                assertThat(((Number) analytics.getBody().get("membershipRenewals")).longValue()).isEqualTo(1);
                assertThat(((Number) analytics.getBody().get("renewingMembers")).longValue()).isEqualTo(1);
                assertThat(((Number) analytics.getBody().get("totalRevenue")).doubleValue())
                                .isEqualTo(((Number) enrolled.getBody().get("amountPaid")).doubleValue()
                                                + ((Number) renewal.getBody().get("amountPaid")).doubleValue());
                double expectedDiscounts = (((Number) enrolled.getBody().get("listPrice")).doubleValue()
                                + ((Number) renewal.getBody().get("listPrice")).doubleValue()) * 0.15;
                assertThat(((Number) analytics.getBody().get("discountsGiven")).doubleValue())
                                .isEqualTo(Math.round(expectedDiscounts * 100.0) / 100.0);
                assertThat(((Number) analytics.getBody().get("recordedPriceVariance")).doubleValue()).isZero();
                Map<?, ?> tax = (Map<?, ?>) analytics.getBody().get("tax");
                assertThat(tax.get("estimated")).isEqualTo(true);
                assertThat(((Number) tax.get("estimatedCgst")).doubleValue()).isGreaterThan(0);
                assertThat(((Number) tax.get("estimatedSgst")).doubleValue()).isGreaterThan(0);
                assertThat(((Number) tax.get("estimatedIgst")).doubleValue()).isZero();
                assertThat(((Number) tax.get("estimatedOutputGst")).doubleValue()).isGreaterThan(0);
                List<?> trend = (List<?>) analytics.getBody().get("trend");
                assertThat(trend).hasSize(1);
                assertThat(((Number) ((Map<?, ?>) trend.get(0)).get("memberships")).longValue()).isEqualTo(2);
                List<?> packages = (List<?>) analytics.getBody().get("packagePerformance");
                assertThat(packages).hasSize(1);
                assertThat(((Number) ((Map<?, ?>) packages.get(0)).get("memberships")).longValue()).isEqualTo(2);
                assertThat(((Number) ((Map<?, ?>) packages.get(0)).get("renewals")).longValue()).isEqualTo(1);
                assertThat(((Number) ((Map<?, ?>) packages.get(0)).get("averageDiscountPercent")).doubleValue())
                                .isEqualTo(15.0);
                List<?> renewers = (List<?>) analytics.getBody().get("renewingMemberDetails");
                assertThat(renewers).hasSize(1);
                assertThat(((Number) ((Map<?, ?>) renewers.get(0)).get("renewals")).longValue()).isEqualTo(1);
                assertThat(((Number) ((Map<?, ?>) renewers.get(0)).get("terms")).longValue()).isEqualTo(2);
                assertThat(analytics.getBody().get("revenueIsRecordedMembershipAmounts")).isEqualTo(true);
        }

        @Test
        void cancellationRequiresRemarksAndExplanationForOtherReason() {
                String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                long planId = ((Number) plans.getBody().get(0).get("id")).longValue();
                Map<String, Object> registration = Map.of(
                                "fullName", "Cancellation Validation",
                                "email", "cancel-validation-" + System.nanoTime() + "@example.test",
                                "password", "MemberPass123",
                                "planId", planId,
                                "discountPercent", 0,
                                "startDate", LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
                ResponseEntity<Map<String, Object>> enrolled = rest.exchange(url("/api/memberships"), HttpMethod.POST,
                                request(ownerToken, registration), MAP_RESPONSE);
                long membershipId = ((Number) enrolled.getBody().get("id")).longValue();
                Map<String, Object> missingExplanation = Map.of(
                                "reason", "OTHER",
                                "remarks", "Member gave a detailed explanation",
                                "cancellationDate", LocalDate.now(ZoneId.of("Asia/Kolkata")).toString(),
                                "otherExplanation", "");

                ResponseEntity<Map<String, Object>> rejected = rest.exchange(
                                url("/api/memberships/" + membershipId + "/cancel"), HttpMethod.POST,
                                request(ownerToken, missingExplanation), MAP_RESPONSE);
                assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(membershipCancellations.count()).isZero();

                Map<String, Object> accepted = Map.of(
                                "reason", "OTHER",
                                "remarks", "Member gave a detailed explanation",
                                "cancellationDate", LocalDate.now(ZoneId.of("Asia/Kolkata")).toString(),
                                "otherExplanation", "Member is changing to a different sports club");
                ResponseEntity<Void> cancelled = rest.exchange(
                                url("/api/memberships/" + membershipId + "/cancel"), HttpMethod.POST,
                                request(ownerToken, accepted), Void.class);
                assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                assertThat(membershipCancellations.findAll().get(0).getOtherExplanation())
                                .isEqualTo("Member is changing to a different sports club");
        }

        @Test
        void ownerCanRegisterDiscountedMemberButMemberCannotReadOwnerDashboard() {
                Map<String, Object> owner = login("owner@gym.local", "ChangeMe123!");
                String ownerToken = (String) owner.get("token");

                ResponseEntity<Map<String, Object>> dashboard = rest.exchange(url("/api/dashboard"), HttpMethod.GET,
                                request(ownerToken, null), MAP_RESPONSE);
                assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);

                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                Map<String, Object> plan = plans.getBody().get(0);
                ResponseEntity<List<Map<String, Object>>> branchRows = rest.exchange(url("/api/branches"),
                                HttpMethod.GET, request(ownerToken, null), LIST_RESPONSE);
                Long mainBranchId = ((Number) branchRows.getBody().get(0).get("id")).longValue();
                String email = "integration-" + System.nanoTime() + "@example.test";
                Map<String, Object> registration = Map.of(
                                "fullName", "Integration Member",
                                "email", email,
                                "phoneNumber", "+1 415 555 0134",
                                "password", "MemberPass123",
                                "planId", ((Number) plan.get("id")).longValue(),
                                "discountPercent", 15,
                                "startDate", LocalDate.now().toString(),
                                "receiptImageData",
                                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lZkAAAAASUVORK5CYII=");
                ResponseEntity<Map<String, Object>> created = rest.exchange(url("/api/memberships"), HttpMethod.POST,
                                request(ownerToken, registration), MAP_RESPONSE);

                assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(created.getBody().get("phoneNumber")).isEqualTo("+1 415 555 0134");
                MembershipReceipt savedReceipt = receipts
                                .findByMembershipId(((Number) created.getBody().get("id")).longValue())
                                .orElseThrow();
                assertThat(savedReceipt.getImage()).isNotEmpty();
                assertThat(savedReceipt.getContentType()).isEqualTo("image/png");
                assertThat(((Number) created.getBody().get("amountPaid")).doubleValue())
                                .isEqualTo(((Number) created.getBody().get("listPrice")).doubleValue() * 0.85);
                double originalListPrice = ((Number) created.getBody().get("listPrice")).doubleValue();

                Map<String, Object> updatedPlan = Map.of(
                                "name", plan.get("name"),
                                "description", plan.get("description"),
                                "price", originalListPrice + 100,
                                "durationMonths", plan.get("durationMonths"),
                                "active", true);
                rest.exchange(url("/api/plans/" + plan.get("id")), HttpMethod.PUT,
                                request(ownerToken, updatedPlan), MAP_RESPONSE);
                ResponseEntity<List<Map<String, Object>>> ownerRows = rest.exchange(url("/api/memberships"),
                                HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                Map<String, Object> recordedMembership = ownerRows.getBody().get(0);
                assertThat(((Number) recordedMembership.get("listPrice")).doubleValue()).isEqualTo(originalListPrice);

                Map<String, Object> futureRegistration = Map.of(
                                "fullName", "Future Member",
                                "email", "future-" + System.nanoTime() + "@example.test",
                                "password", "MemberPass123",
                                "planId", ((Number) plan.get("id")).longValue(),
                                "discountPercent", 0,
                                "startDate", LocalDate.now().plusDays(10).toString());
                rest.exchange(url("/api/memberships"), HttpMethod.POST,
                                request(ownerToken, futureRegistration), MAP_RESPONSE);
                ResponseEntity<Map<String, Object>> updatedDashboard = rest.exchange(url("/api/dashboard"),
                                HttpMethod.GET,
                                request(ownerToken, null), MAP_RESPONSE);
                assertThat(((Number) updatedDashboard.getBody().get("activeMembers")).longValue()).isEqualTo(1);
                assertThat(((Number) updatedDashboard.getBody().get("totalMembers")).longValue()).isEqualTo(2);
                Object branchDashboardsValue = updatedDashboard.getBody().get("branches");
                assertThat(branchDashboardsValue).isInstanceOf(List.class);
                List<?> branchDashboards = (List<?>) branchDashboardsValue;
                assertThat(branchDashboards).hasSize(1);
                assertThat(branchDashboards.get(0)).isInstanceOf(Map.class);
                Map<?, ?> mainBranchDashboard = (Map<?, ?>) branchDashboards.get(0);
                assertThat(((Number) mainBranchDashboard.get("branchId")).longValue()).isEqualTo(mainBranchId);
                assertThat(((Number) mainBranchDashboard.get("activeMembers")).longValue()).isEqualTo(1);
                assertThat(((Number) mainBranchDashboard.get("totalMembers")).longValue()).isEqualTo(2);

                String memberToken = (String) login(email, "MemberPass123").get("token");
                ResponseEntity<List<Map<String, Object>>> memberRows = rest.exchange(url("/api/memberships"),
                                HttpMethod.GET,
                                request(memberToken, null), LIST_RESPONSE);
                assertThat(memberRows.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(memberRows.getBody()).hasSize(1);

                ResponseEntity<Map<String, Object>> denied = rest.exchange(url("/api/dashboard"), HttpMethod.GET,
                                request(memberToken, null), MAP_RESPONSE);
                assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
                ResponseEntity<Map<String, Object>> analyticsDenied = rest.exchange(
                                url("/api/analytics?from=" + today + "&to=" + today), HttpMethod.GET,
                                request(memberToken, null), MAP_RESPONSE);
                assertThat(analyticsDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                ResponseEntity<String> invalidToken = rest.exchange(url("/api/memberships"), HttpMethod.GET,
                                request("invalid-token", null), String.class);
                assertThat(invalidToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

                LocalDate originalEndDate = LocalDate.parse((String) created.getBody().get("endDate"));
                LocalDate renewalStartDate = originalEndDate.plusDays(1);
                Map<String, Object> renewalRequest = Map.of(
                                "planId", ((Number) plan.get("id")).longValue(),
                                "discountPercent", 5,
                                "startDate", renewalStartDate.toString());
                Map<String, Object> overlappingRenewal = Map.of(
                                "planId", ((Number) plan.get("id")).longValue(),
                                "discountPercent", 0,
                                "startDate", originalEndDate.toString());
                ResponseEntity<Map<String, Object>> rejectedOverlap = rest.exchange(
                                url("/api/memberships/" + created.getBody().get("id") + "/renew"), HttpMethod.POST,
                                request(ownerToken, overlappingRenewal), MAP_RESPONSE);
                assertThat(rejectedOverlap.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                ResponseEntity<Map<String, Object>> renewed = rest.exchange(
                                url("/api/memberships/" + created.getBody().get("id") + "/renew"),
                                HttpMethod.POST, request(ownerToken, renewalRequest), MAP_RESPONSE);
                assertThat(renewed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(((Number) renewed.getBody().get("discountPercent")).doubleValue()).isEqualTo(5);
                assertThat(renewed.getBody().get("startDate")).isEqualTo(renewalStartDate.toString());
                ResponseEntity<String> afterRenewal = rest.exchange(url("/api/memberships"), HttpMethod.GET,
                                request(ownerToken, null), String.class);
                assertThat(afterRenewal.getStatusCode())
                                .withFailMessage("Membership list failed: %s", afterRenewal.getBody())
                                .isEqualTo(HttpStatus.OK);
                assertThat(afterRenewal.getBody()).contains("\"renewalStartDate\":\"" + renewalStartDate + "\"");
                ResponseEntity<List<Map<String, Object>>> expiring = rest.exchange(
                                url("/api/memberships/expiring?withinDays=90"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                assertThat(expiring.getBody()).noneMatch(row -> ((Number) row.get("id"))
                                .longValue() == ((Number) created.getBody().get("id")).longValue());
                ResponseEntity<Map<String, Object>> rejectedDuplicate = rest.exchange(
                                url("/api/memberships/" + created.getBody().get("id") + "/renew"), HttpMethod.POST,
                                request(ownerToken, renewalRequest), MAP_RESPONSE);
                assertThat(rejectedDuplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

                Map<String, Object> dayPlanRequest = Map.of(
                                "name", "Two-day trial",
                                "description", "Short expiry test package",
                                "price", 10,
                                "durationDays", 2,
                                "active", true);
                ResponseEntity<Map<String, Object>> dayPlanResponse = rest.exchange(url("/api/plans"), HttpMethod.POST,
                                request(ownerToken, dayPlanRequest), MAP_RESPONSE);
                assertThat(dayPlanResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                String dayMemberEmail = "day-member-" + System.nanoTime() + "@example.test";
                Map<String, Object> dayMembershipRequest = Map.of(
                                "fullName", "Day Trial Member",
                                "email", dayMemberEmail,
                                "phoneNumber", "+1 415 555 0199",
                                "password", "MemberPass123",
                                "planId", dayPlanResponse.getBody().get("id"),
                                "discountPercent", 0,
                                "startDate", LocalDate.now().toString());
                ResponseEntity<Map<String, Object>> dayMembership = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST,
                                request(ownerToken, dayMembershipRequest), MAP_RESPONSE);
                assertThat(dayMembership.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(dayMembership.getBody().get("endDate")).isEqualTo(LocalDate.now().plusDays(1).toString());

                Map<String, Object> resetRequest = Map.of("email", email);
                ResponseEntity<Map<String, Object>> resetCode = rest.exchange(url("/api/auth/password-reset/request"),
                                HttpMethod.POST, request(null, resetRequest), MAP_RESPONSE);
                String developmentCode = (String) resetCode.getBody().get("developmentCode");
                assertThat(developmentCode).hasSize(6);
                ResponseEntity<String> resetResult = rest.postForEntity(url("/api/auth/password-reset/confirm"),
                                Map.of("email", email, "code", developmentCode, "newPassword", "ResetMember123"),
                                String.class);
                assertThat(resetResult.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(login(email, "ResetMember123")).isNotNull();
                ResponseEntity<String> revokedMemberSession = rest.exchange(url("/api/memberships"), HttpMethod.GET,
                                request(memberToken, null), String.class);
                assertThat(revokedMemberSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

                ResponseEntity<Map<String, Object>> profile = rest.exchange(url("/api/auth/profile"), HttpMethod.PUT,
                                request(ownerToken, Map.of("fullName", "Gym Owner", "email", "owner@gym.local",
                                                "phoneNumber", "+1 415 555 0100")),
                                MAP_RESPONSE);
                assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(((Map<?, ?>) profile.getBody().get("user")).get("phoneNumber")).isEqualTo("+1 415 555 0100");
                ResponseEntity<Map<String, Object>> passwordChanged = rest.exchange(url("/api/auth/change-password"),
                                HttpMethod.POST,
                                request(ownerToken,
                                                Map.of("currentPassword", "ChangeMe123!", "newPassword",
                                                                "ChangedOwner123")),
                                MAP_RESPONSE);
                assertThat(passwordChanged.getStatusCode()).isEqualTo(HttpStatus.OK);
                ownerToken = (String) login("owner@gym.local", "ChangedOwner123").get("token");
                assertThat(ownerToken).isNotNull();

                Map<String, Object> staffRequest = Map.of(
                                "fullName", "Front Desk",
                                "email", "staff-" + System.nanoTime() + "@example.test",
                                "phoneNumber", "+1 415 555 0188",
                                "branchId", mainBranchId,
                                "password", "StaffPass123");
                ResponseEntity<Map<String, Object>> createdStaff = rest.exchange(url("/api/staff"), HttpMethod.POST,
                                request(ownerToken, staffRequest), MAP_RESPONSE);
                assertThat(createdStaff.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                String staffToken = (String) login((String) staffRequest.get("email"), "StaffPass123").get("token");
                ResponseEntity<Map<String, Object>> staffDashboard = rest.exchange(url("/api/dashboard"),
                                HttpMethod.GET,
                                request(staffToken, null), MAP_RESPONSE);
                assertThat(staffDashboard.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                ResponseEntity<Void> deletedStaff = rest.exchange(url("/api/staff/" + createdStaff.getBody().get("id")),
                                HttpMethod.DELETE, request(ownerToken, null), Void.class);
                assertThat(deletedStaff.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                ResponseEntity<String> revokedStaffSession = rest.exchange(url("/api/memberships"), HttpMethod.GET,
                                request(staffToken, null), String.class);
                assertThat(revokedStaffSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void cancelledMemberMustRenewExistingMembershipAndCannotRegisterAnotherTerm() {
                String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                Long firstPlanId = ((Number) plans.getBody().get(0).get("id")).longValue();
                Long secondPlanId = ((Number) plans.getBody().get(1).get("id")).longValue();
                Long thirdPlanId = ((Number) plans.getBody().get(2).get("id")).longValue();
                String memberEmail = "cancelled-member-" + System.nanoTime() + "@example.test";

                Map<String, Object> firstEnrollment = Map.of(
                                "fullName", "Rejoining Member",
                                "email", memberEmail,
                                "phoneNumber", "+1 415 555 0177",
                                "password", "MemberPass123",
                                "planId", firstPlanId,
                                "discountPercent", 0,
                                "startDate", LocalDate.now().plusDays(10).toString());
                ResponseEntity<Map<String, Object>> first = rest.exchange(url("/api/memberships"), HttpMethod.POST,
                                request(ownerToken, firstEnrollment), MAP_RESPONSE);
                assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                Map<String, Object> originalSession = login(memberEmail, "MemberPass123");
                Map<?, ?> originalAccount = (Map<?, ?>) originalSession.get("user");
                long originalMemberId = ((Number) originalAccount.get("id")).longValue();
                ResponseEntity<Void> cancelFirst = rest.exchange(
                                url("/api/memberships/" + first.getBody().get("id") + "/cancel"), HttpMethod.POST,
                                request(ownerToken, cancellationRequest("RELOCATED")), Void.class);
                assertThat(cancelFirst.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                var cancellationAudit = membershipCancellations.findAll().get(0);
                assertThat(cancellationAudit.getMembershipId())
                                .isEqualTo(((Number) first.getBody().get("id")).longValue());
                assertThat(cancellationAudit.getMemberId()).isEqualTo(originalMemberId);
                assertThat(cancellationAudit.getReason()).isEqualTo("RELOCATED");
                assertThat(cancellationAudit.getRemarks()).contains("after review");
                assertThat(cancellationAudit.getRequestedById()).isNotNull();
                assertThat(cancellationAudit.getPerformedById()).isEqualTo(cancellationAudit.getRequestedById());
                assertThat(cancellationAudit.getApprovedById()).isNull();
                assertThat(cancellationAudit.getStatusBefore()).isEqualTo("ACTIVE");
                assertThat(cancellationAudit.getStatusAfter()).isEqualTo("CANCELLED");
                assertThat(cancellationAudit.getApprovalStatus()).isEqualTo("NOT_REQUIRED");
                ResponseEntity<List<Map<String, Object>>> afterFirstCancellation = rest.exchange(
                                url("/api/memberships"), HttpMethod.GET, request(ownerToken, null), LIST_RESPONSE);
                Map<String, Object> persistedCancelled = afterFirstCancellation.getBody().stream()
                                .filter(row -> ((Number) row.get("id"))
                                                .longValue() == ((Number) first.getBody().get("id")).longValue())
                                .findFirst().orElseThrow();
                assertThat(persistedCancelled.get("status")).isEqualTo("CANCELLED");
                ResponseEntity<Map<String, Object>> cancelledPage = rest.exchange(
                                url("/api/memberships/page?page=0&size=10&search=" + memberEmail
                                                + "&filter=cancelled"),
                                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
                assertThat(cancelledPage.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(cancelledPage.getBody().get("totalMembers")).isEqualTo(1);

                Map<String, Object> duplicateEnrollment = Map.of(
                                "fullName", "Rejoining Member",
                                "email", memberEmail,
                                "phoneNumber", "+1 415 555 0177",
                                "password", "MemberPass456",
                                "planId", secondPlanId,
                                "discountPercent", 0,
                                "startDate", LocalDate.now().toString());
                ResponseEntity<Map<String, Object>> blockedEnrollment = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, request(ownerToken, duplicateEnrollment), MAP_RESPONSE);
                assertThat(blockedEnrollment.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

                UserAccount memberAccount = users.findById(originalMemberId).orElseThrow();
                MembershipPlan historicalPlan = planRepository.findById(secondPlanId).orElseThrow();
                GymBranch memberBranch = branches.findById(((Number) first.getBody().get("branchId")).longValue())
                                .orElseThrow();
                Membership otherCancelledMembership = new Membership(memberAccount, historicalPlan, memberBranch,
                                LocalDate.now().minusDays(20), LocalDate.now().minusDays(1),
                                historicalPlan.getPrice(), historicalPlan.getPrice(), BigDecimal.ZERO);
                otherCancelledMembership.cancel();
                memberships.save(otherCancelledMembership);

                Map<String, Object> cancelledRenewal = Map.of(
                                "planId", thirdPlanId,
                                "discountPercent", 10,
                                "startDate", LocalDate.now().toString());
                ResponseEntity<Map<String, Object>> renewed = rest.exchange(
                                url("/api/memberships/" + first.getBody().get("id") + "/renew"), HttpMethod.POST,
                                request(ownerToken, cancelledRenewal), MAP_RESPONSE);
                assertThat(renewed.getStatusCode())
                                .withFailMessage("Cancelled membership renewal failed: %s", renewed.getBody())
                                .isEqualTo(HttpStatus.CREATED);
                assertThat(renewed.getBody().get("startDate")).isEqualTo(LocalDate.now().toString());
                ResponseEntity<Map<String, Object>> noLongerCancelled = rest.exchange(
                                url("/api/memberships/page?page=0&size=10&search=" + memberEmail
                                                + "&filter=cancelled"),
                                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
                assertThat(noLongerCancelled.getBody().get("totalMembers")).isEqualTo(0);

                ResponseEntity<Map<String, Object>> duplicateRenewal = rest.exchange(
                                url("/api/memberships/" + first.getBody().get("id") + "/renew"), HttpMethod.POST,
                                request(ownerToken, cancelledRenewal), MAP_RESPONSE);
                assertThat(duplicateRenewal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

                ResponseEntity<Map<String, Object>> otherCancelledRenewal = rest.exchange(
                                url("/api/memberships/" + otherCancelledMembership.getId() + "/renew"),
                                HttpMethod.POST, request(ownerToken, cancelledRenewal), MAP_RESPONSE);
                assertThat(otherCancelledRenewal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

                ResponseEntity<Map<String, Object>> enrollmentWhileRenewed = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, request(ownerToken, duplicateEnrollment), MAP_RESPONSE);
                assertThat(enrollmentWhileRenewed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void franchiseBranchesScopeStaffAndMemberships() {
                String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> currentBranches = rest.exchange(url("/api/branches"),
                                HttpMethod.GET, request(ownerToken, null), LIST_RESPONSE);
                Long mainBranchId = ((Number) currentBranches.getBody().get(0).get("id")).longValue();

                Map<String, Object> franchiseRequest = Map.of(
                                "name", "West Franchise",
                                "code", "WEST-FR",
                                "type", "FRANCHISE",
                                "address", "West district",
                                "phoneNumber", "+1 415 555 0102",
                                "active", true);
                ResponseEntity<Map<String, Object>> franchise = rest.exchange(url("/api/branches"), HttpMethod.POST,
                                request(ownerToken, franchiseRequest), MAP_RESPONSE);
                assertThat(franchise.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                Long franchiseBranchId = ((Number) franchise.getBody().get("id")).longValue();

                Map<String, Object> staffRequest = Map.of(
                                "fullName", "West Front Desk",
                                "email", "west-staff-" + System.nanoTime() + "@example.test",
                                "phoneNumber", "+1 415 555 0103",
                                "password", "BranchStaff123",
                                "branchId", franchiseBranchId);
                ResponseEntity<Map<String, Object>> staff = rest.exchange(url("/api/staff"), HttpMethod.POST,
                                request(ownerToken, staffRequest), MAP_RESPONSE);
                assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                String staffToken = (String) login((String) staffRequest.get("email"), "BranchStaff123")
                                .get("token");
                ResponseEntity<List<Map<String, Object>>> staffBranches = rest.exchange(url("/api/branches"),
                                HttpMethod.GET, request(staffToken, null), LIST_RESPONSE);
                assertThat(staffBranches.getBody()).hasSize(1);
                assertThat(((Number) staffBranches.getBody().get(0).get("id")).longValue())
                                .isEqualTo(franchiseBranchId);

                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                Long planId = ((Number) plans.getBody().get(0).get("id")).longValue();
                String memberEmail = "franchise-member-" + System.nanoTime() + "@example.test";
                Map<String, Object> memberRequest = Map.of(
                                "fullName", "West Member",
                                "email", memberEmail,
                                "phoneNumber", "+1 415 555 0104",
                                "password", "BranchMember123",
                                "planId", planId,
                                "branchId", franchiseBranchId,
                                "discountPercent", 0,
                                "startDate", LocalDate.now().toString());
                ResponseEntity<Map<String, Object>> member = rest.exchange(url("/api/memberships"), HttpMethod.POST,
                                request(ownerToken, memberRequest), MAP_RESPONSE);
                assertThat(member.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(((Number) member.getBody().get("branchId")).longValue()).isEqualTo(franchiseBranchId);

                ResponseEntity<Map<String, Object>> ownerDashboard = rest.exchange(url("/api/dashboard"),
                                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
                List<?> dashboardBranches = (List<?>) ownerDashboard.getBody().get("branches");
                Map<?, ?> franchiseDashboard = dashboardBranches.stream()
                                .map(row -> (Map<?, ?>) row)
                                .filter(row -> ((Number) row.get("branchId")).longValue() == franchiseBranchId)
                                .findFirst().orElseThrow();
                assertThat(franchiseDashboard.get("branchType")).isEqualTo("FRANCHISE");
                assertThat(((Number) franchiseDashboard.get("activeMembers")).longValue()).isEqualTo(1);
                assertThat(((Number) franchiseDashboard.get("totalMembers")).longValue()).isEqualTo(1);

                ResponseEntity<List<Map<String, Object>>> franchiseStaffMembers = rest.exchange(
                                url("/api/memberships"), HttpMethod.GET, request(staffToken, null), LIST_RESPONSE);
                assertThat(franchiseStaffMembers.getBody()).hasSize(1);

                memberRequest = Map.of(
                                "fullName", "Wrong Branch Member",
                                "email", "wrong-branch-" + System.nanoTime() + "@example.test",
                                "phoneNumber", "+1 415 555 0105",
                                "password", "BranchMember123",
                                "planId", planId,
                                "branchId", mainBranchId,
                                "discountPercent", 0,
                                "startDate", LocalDate.now().toString());
                ResponseEntity<Map<String, Object>> crossBranchRegistration = rest.exchange(
                                url("/api/memberships"), HttpMethod.POST, request(staffToken, memberRequest),
                                MAP_RESPONSE);
                assertThat(crossBranchRegistration.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void packageArchiveSnapshotsMembershipAdjustmentsReportsAndHistory() {
                String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> availablePlans = rest.exchange(url("/api/plans"),
                                HttpMethod.GET, request(ownerToken, null), LIST_RESPONSE);
                Map<String, Object> plan = availablePlans.getBody().get(0);
                Long planId = ((Number) plan.get("id")).longValue();
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
                String memberEmail = "feature-set-" + System.nanoTime() + "@example.test";
                Map<String, Object> registration = Map.of(
                                "fullName", "Feature Set Member", "email", memberEmail,
                                "password", "MemberPass123", "planId", planId,
                                "discountPercent", 10, "startDate", today.toString());
                ResponseEntity<Map<String, Object>> enrollment = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, request(ownerToken, registration), MAP_RESPONSE);
                assertThat(enrollment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                Long membershipId = ((Number) enrollment.getBody().get("id")).longValue();
                String originalPlanName = (String) enrollment.getBody().get("planName");

                Map<String, Object> planUpdate = new HashMap<>();
                planUpdate.put("name", "Archived history snapshot");
                planUpdate.put("description", "Changed after purchase");
                planUpdate.put("price", plan.get("price"));
                planUpdate.put("durationMonths", plan.get("durationMonths"));
                planUpdate.put("durationDays", plan.get("durationDays"));
                planUpdate.put("branchId", plan.get("branchId"));
                planUpdate.put("active", false);
                ResponseEntity<Map<String, Object>> archived = rest.exchange(url("/api/plans/" + planId),
                                HttpMethod.PUT, request(ownerToken, planUpdate), MAP_RESPONSE);
                assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(archived.getBody().get("usageCount")).isEqualTo(1);
                ResponseEntity<List<Map<String, Object>>> plansWithArchive = rest.exchange(url("/api/plans"),
                                HttpMethod.GET, request(ownerToken, null), LIST_RESPONSE);
                assertThat(plansWithArchive.getBody()).anyMatch(row ->
                                ((Number) row.get("id")).longValue() == planId
                                                && Boolean.FALSE.equals(row.get("active")));
                Map<String, Object> blockedEnrollment = Map.of(
                                "fullName", "Archived Package Member",
                                "email", "archived-" + System.nanoTime() + "@example.test",
                                "password", "MemberPass123", "planId", planId,
                                "discountPercent", 0, "startDate", today.toString());
                ResponseEntity<Map<String, Object>> archivedPurchase = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, request(ownerToken, blockedEnrollment), MAP_RESPONSE);
                assertThat(archivedPurchase.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                planUpdate.put("active", true);
                ResponseEntity<Map<String, Object>> restored = rest.exchange(url("/api/plans/" + planId),
                                HttpMethod.PUT, request(ownerToken, planUpdate), MAP_RESPONSE);
                assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(restored.getBody().get("active")).isEqualTo(true);
                ResponseEntity<List<Map<String, Object>>> membershipRows = rest.exchange(url("/api/memberships"),
                                HttpMethod.GET, request(ownerToken, null), LIST_RESPONSE);
                Map<String, Object> savedMembership = membershipRows.getBody().stream()
                                .filter(row -> ((Number) row.get("id")).longValue() == membershipId)
                                .findFirst().orElseThrow();
                assertThat(savedMembership.get("planName")).isEqualTo(originalPlanName);

                ResponseEntity<Map<String, Object>> branch = rest.exchange(url("/api/branches"), HttpMethod.POST,
                                request(ownerToken, Map.of("name", "Transfer destination",
                                                "code", "TRANSFER-" + System.nanoTime(),
                                                "type", "COMPANY_OWNED", "active", true)),
                                MAP_RESPONSE);
                Long newBranchId = ((Number) branch.getBody().get("id")).longValue();

                ResponseEntity<Map<String, Object>> frozen = rest.exchange(
                                url("/api/memberships/" + membershipId + "/freeze"), HttpMethod.POST,
                                request(ownerToken, Map.of("startDate", today.toString(),
                                                "endDate", today.plusDays(1).toString(), "reason", "Test freeze")),
                                MAP_RESPONSE);
                assertThat(frozen.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(frozen.getBody().get("endDate")).isEqualTo(
                                LocalDate.parse((String) enrollment.getBody().get("endDate")).plusDays(2).toString());
                ResponseEntity<Map<String, Object>> access = rest.exchange(
                                url("/api/memberships/" + membershipId + "/access"), HttpMethod.GET,
                                request(ownerToken, null), MAP_RESPONSE);
                assertThat(access.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(access.getBody().get("allowed")).isEqualTo(false);
                assertThat(access.getBody().get("status")).isEqualTo("FROZEN");

                ResponseEntity<Map<String, Object>> transferred = rest.exchange(
                                url("/api/memberships/" + membershipId + "/transfer"), HttpMethod.POST,
                                request(ownerToken, Map.of("branchId", newBranchId, "reason", "Member requested move")),
                                MAP_RESPONSE);
                assertThat(transferred.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(((Number) transferred.getBody().get("branchId")).longValue()).isEqualTo(newBranchId);

                Map<String, Object> correction = Map.of(
                                "planId", planId, "startDate", today.toString(),
                                "endDate", transferred.getBody().get("endDate"),
                                "listPrice", enrollment.getBody().get("listPrice"),
                                "discountPercent", 10, "amountPaid", enrollment.getBody().get("amountPaid"),
                                "reason", "Corrected after verification");
                ResponseEntity<Map<String, Object>> corrected = rest.exchange(
                                url("/api/memberships/" + membershipId + "/correction"), HttpMethod.PUT,
                                request(ownerToken, correction), MAP_RESPONSE);
                assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);

                ResponseEntity<List<Map<String, Object>>> history = rest.exchange(url("/api/history"),
                                HttpMethod.GET, request(ownerToken, null), LIST_RESPONSE);
                assertThat(history.getBody()).extracting(row -> row.get("action"))
                                .contains("PACKAGE_ARCHIVED", "PACKAGE_RESTORED", "MEMBERSHIP_FROZEN",
                                                "MEMBERSHIP_TRANSFERRED", "MEMBERSHIP_CORRECTED");
                ResponseEntity<Map<String, Object>> report = rest.exchange(
                                url("/api/reports?from=" + today.minusDays(1) + "&to=" + today.plusDays(2)),
                                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
                assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(((Number) report.getBody().get("memberships")).longValue()).isGreaterThanOrEqualTo(1);
                ResponseEntity<String> csv = rest.exchange(
                                url("/api/reports/export.csv?from=" + today.minusDays(1)
                                                + "&to=" + today.plusDays(2) + "&type=memberships"),
                                HttpMethod.GET, request(ownerToken, null), String.class);
                assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(csv.getBody()).contains("membership_id,member_name");

                Membership reminderTarget = memberships.findById(membershipId).orElseThrow();
                reminderTarget.correct(planRepository.findById(planId).orElseThrow(), today,
                                today.plusDays(7), reminderTarget.getListPrice(),
                                reminderTarget.getAmountPaid(), reminderTarget.getDiscountPercent());
                memberships.save(reminderTarget);
                renewalReminderService.processDueReminders();
                ResponseEntity<List<Map<String, Object>>> reminders = rest.exchange(
                                url("/api/renewal-reminders"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                assertThat(reminders.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(reminders.getBody()).anyMatch(row ->
                                ((Number) row.get("membershipId")).longValue() == membershipId
                                                && ((Number) row.get("daysBefore")).intValue() == 7
                                                && "FAILED".equals(row.get("status"))
                                                && ((Number) row.get("attempts")).intValue() == 1);

                ResponseEntity<Map<String, Object>> manualReminder = rest.exchange(
                                url("/api/memberships/" + membershipId + "/send-reminder"), HttpMethod.POST,
                                request(ownerToken, null), MAP_RESPONSE);
                assertThat(manualReminder.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(((Number) manualReminder.getBody().get("membershipId")).longValue()).isEqualTo(membershipId);
        }

        @Test
        void cancellingAndEndingFreezesRestoreOnlyUnusedMembershipDays() {
                String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                Long planId = ((Number) plans.getBody().get(0).get("id")).longValue();
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
                ResponseEntity<Map<String, Object>> enrolled = rest.exchange(url("/api/memberships"),
                                HttpMethod.POST, request(ownerToken, Map.of(
                                                "fullName", "Freeze Adjustment Member",
                                                "email", "freeze-adjustment-" + System.nanoTime() + "@example.test",
                                                "password", "MemberPass123", "planId", planId,
                                                "discountPercent", 0, "startDate", today.toString())),
                                MAP_RESPONSE);
                Long membershipId = ((Number) enrolled.getBody().get("id")).longValue();
                String originalEndDate = (String) enrolled.getBody().get("endDate");

                ResponseEntity<Map<String, Object>> scheduled = rest.exchange(
                                url("/api/memberships/" + membershipId + "/freeze"), HttpMethod.POST,
                                request(ownerToken, Map.of("startDate", today.plusDays(1).toString(),
                                                "endDate", today.plusDays(3).toString(), "reason", "Planned trip")),
                                MAP_RESPONSE);
                assertThat(scheduled.getBody().get("endDate"))
                                .isEqualTo(LocalDate.parse(originalEndDate).plusDays(3).toString());
                Long scheduledFreezeId = ((Number) ((Map<?, ?>) ((List<?>) scheduled.getBody().get("freezes"))
                                .get(0)).get("id")).longValue();
                ResponseEntity<Map<String, Object>> cancelled = rest.exchange(
                                url("/api/memberships/" + membershipId + "/freezes/" + scheduledFreezeId + "/cancel"),
                                HttpMethod.POST, request(ownerToken, Map.of("reason", "Plans changed")), MAP_RESPONSE);
                assertThat(cancelled.getBody().get("endDate")).isEqualTo(originalEndDate);
                assertThat(((Map<?, ?>) ((List<?>) cancelled.getBody().get("freezes")).get(0))
                                .get("cancelledAt")).isNotNull();

                ResponseEntity<Map<String, Object>> activeFreeze = rest.exchange(
                                url("/api/memberships/" + membershipId + "/freeze"), HttpMethod.POST,
                                request(ownerToken, Map.of("startDate", today.toString(),
                                                "endDate", today.plusDays(2).toString(), "reason", "Temporary pause")),
                                MAP_RESPONSE);
                Map<?, ?> activeFreezeRow = ((List<?>) activeFreeze.getBody().get("freezes")).stream()
                                .filter(row -> row instanceof Map<?, ?> freeze
                                                && today.toString().equals(freeze.get("startDate")))
                                .map(row -> (Map<?, ?>) row).findFirst().orElseThrow();
                Long activeFreezeId = ((Number) activeFreezeRow.get("id")).longValue();
                ResponseEntity<Map<String, Object>> endedEarly = rest.exchange(
                                url("/api/memberships/" + membershipId + "/freezes/" + activeFreezeId
                                                + "/end-early"),
                                HttpMethod.POST, request(ownerToken, Map.of("reason", "Returned early")),
                                MAP_RESPONSE);
                assertThat(endedEarly.getBody().get("endDate")).isEqualTo(originalEndDate);
                Map<?, ?> savedFreeze = ((List<?>) endedEarly.getBody().get("freezes")).stream()
                                .filter(row -> row instanceof Map<?, ?> freeze
                                                && ((Number) freeze.get("id")).longValue() == activeFreezeId)
                                .map(row -> (Map<?, ?>) row)
                                .findFirst().orElseThrow();
                assertThat(savedFreeze.get("endedEarlyAt")).isEqualTo(today.toString());
                assertThat(endedEarly.getBody().get("frozen")).isEqualTo(false);

                ResponseEntity<Map<String, Object>> access = rest.exchange(
                                url("/api/memberships/" + membershipId + "/access"), HttpMethod.GET,
                                request(ownerToken, null), MAP_RESPONSE);
                assertThat(access.getBody().get("allowed")).isEqualTo(true);
        }

        @Test
        void pagedMemberGroupsKeepTermHistoryAndCancelOnlyTheScheduledRenewal() {
                String ownerToken = (String) login("owner@gym.local", "ChangeMe123!").get("token");
                ResponseEntity<List<Map<String, Object>>> plans = rest.exchange(url("/api/plans"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                Long planId = ((Number) plans.getBody().get(0).get("id")).longValue();
                String email = "grouped-terms-" + System.nanoTime() + "@example.test";
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
                Map<String, Object> registration = Map.of(
                                "fullName", "Grouped Terms Member", "email", email,
                                "password", "MemberPass123", "planId", planId,
                                "discountPercent", 0, "startDate", today.toString());
                ResponseEntity<Map<String, Object>> enrolled = rest.exchange(url("/api/memberships"), HttpMethod.POST,
                                request(ownerToken, registration), MAP_RESPONSE);
                Long originalId = ((Number) enrolled.getBody().get("id")).longValue();
                ResponseEntity<List<Map<String, Object>>> recentMemberships = rest.exchange(
                                url("/api/memberships/recent"), HttpMethod.GET,
                                request(ownerToken, null), LIST_RESPONSE);
                assertThat(recentMemberships.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(recentMemberships.getBody()).anyMatch(term ->
                                ((Number) term.get("id")).longValue() == originalId);

                ResponseEntity<Map<String, Object>> frozen = rest.exchange(
                                url("/api/memberships/" + originalId + "/freeze"), HttpMethod.POST,
                                request(ownerToken, Map.of("startDate", today.toString(),
                                                "endDate", today.toString(), "reason", "Grouped history test")),
                                MAP_RESPONSE);
                assertThat(frozen.getStatusCode()).isEqualTo(HttpStatus.OK);

                ResponseEntity<Map<String, Object>> initialPage = rest.exchange(
                                url("/api/memberships/page?page=0&size=1&search=" + email + "&filter=all"),
                                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
                assertThat(initialPage.getStatusCode()).isEqualTo(HttpStatus.OK);
                List<?> initialGroups = (List<?>) initialPage.getBody().get("members");
                Map<?, ?> initialGroup = (Map<?, ?>) initialGroups.get(0);
                assertThat(((List<?>) initialGroup.get("terms"))).hasSize(1);
                assertThat(initialPage.getBody().get("totalMembers")).isEqualTo(1);
                ResponseEntity<Map<String, Object>> activePage = rest.exchange(
                                url("/api/memberships/page?page=0&size=1&search=" + email + "&filter=active"),
                                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
                assertThat(activePage.getBody().get("totalMembers")).isEqualTo(1);

                Map<String, Object> renewalRequest = Map.of(
                                "planId", planId, "discountPercent", 0,
                                "startDate", LocalDate.parse((String) frozen.getBody().get("endDate"))
                                                .plusDays(1).toString());
                ResponseEntity<Map<String, Object>> renewal = rest.exchange(
                                url("/api/memberships/" + originalId + "/renew"), HttpMethod.POST,
                                request(ownerToken, renewalRequest), MAP_RESPONSE);
                assertThat(renewal.getStatusCode()).isEqualTo(HttpStatus.CREATED);

                ResponseEntity<Void> cancelledRenewal = rest.exchange(
                                url("/api/memberships/" + originalId + "/cancel-renewal"), HttpMethod.POST,
                                request(ownerToken, cancellationRequest("NO_LONGER_INTERESTED")), Void.class);
                assertThat(cancelledRenewal.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                ResponseEntity<Map<String, Object>> afterCancellation = rest.exchange(
                                url("/api/memberships/page?page=0&size=1&search=" + email + "&filter=all"),
                                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
                List<?> savedGroups = (List<?>) afterCancellation.getBody().get("members");
                Map<?, ?> savedGroup = (Map<?, ?>) savedGroups.get(0);
                List<?> savedTerms = (List<?>) savedGroup.get("terms");
                assertThat(savedTerms).hasSize(2);
                Map<?, ?> originalTerm = savedTerms.stream().map(term -> (Map<?, ?>) term)
                                .filter(term -> ((Number) term.get("id")).longValue() == originalId)
                                .findFirst().orElseThrow();
                Map<?, ?> cancelledTerm = savedTerms.stream().map(term -> (Map<?, ?>) term)
                                .filter(term -> ((Number) term.get("id")).longValue()
                                                == ((Number) renewal.getBody().get("id")).longValue())
                                .findFirst().orElseThrow();
                assertThat(originalTerm.get("renewalStartDate")).isNull();
                assertThat(originalTerm.get("status")).isEqualTo("ACTIVE");
                assertThat(cancelledTerm.get("status")).isEqualTo("CANCELLED");

                ResponseEntity<Map<String, Object>> rescheduledRenewal = rest.exchange(
                                url("/api/memberships/" + originalId + "/renew"),
                                HttpMethod.POST, request(ownerToken, renewalRequest), MAP_RESPONSE);
                assertThat(rescheduledRenewal.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(rescheduledRenewal.getBody().get("id"))
                                .isNotEqualTo(renewal.getBody().get("id"));

                ResponseEntity<Void> cancelledRescheduledRenewal = rest.exchange(
                                url("/api/memberships/" + originalId + "/cancel-renewal"), HttpMethod.POST,
                                request(ownerToken, cancellationRequest("NO_LONGER_INTERESTED")), Void.class);
                assertThat(cancelledRescheduledRenewal.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

                Map<String, Object> conflictingRenewalRequest = Map.of(
                                "planId", planId, "discountPercent", 0,
                                "startDate", today.toString());
                ResponseEntity<Map<String, Object>> overlappingRenewal = rest.exchange(
                                url("/api/memberships/" + rescheduledRenewal.getBody().get("id") + "/renew"),
                                HttpMethod.POST, request(ownerToken, conflictingRenewalRequest), MAP_RESPONSE);
                assertThat(overlappingRenewal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(overlappingRenewal.getBody().get("message").toString())
                                .contains("already has an active membership");

                ResponseEntity<Map<String, Object>> access = rest.exchange(
                                url("/api/memberships/" + originalId + "/access"), HttpMethod.GET,
                                request(ownerToken, null), MAP_RESPONSE);
                assertThat(access.getBody().get("allowed")).isEqualTo(false);
                assertThat(access.getBody().get("status")).isEqualTo("FROZEN");
        }

        private String extractCookies(ResponseEntity<?> response) {
                List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
                System.out.println("Set-Cookie headers: " + setCookies);
                if (setCookies == null) return null;
                List<String> cookies = new java.util.ArrayList<>();
                for (String c : setCookies) {
                    if (c.startsWith("gym_jwt=")) {
                        cookies.add(c.split(";")[0]);
                    } else if (c.startsWith("XSRF-TOKEN=")) {
                        cookies.add(c.split(";")[0]);
                    }
                }
                String result = String.join("; ", cookies);
                System.out.println("Extracted cookies: " + result);
                return result;
        }

        private Map<String, Object> login(String email, String password) {
                ResponseEntity<Map<String, Object>> response = rest.exchange(url("/api/auth/login"), HttpMethod.POST,
                                request(null, Map.of("email", email, "password", password)), MAP_RESPONSE);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                Map<String, Object> body = response.getBody();
                Map<String, Object> result = body != null ? new HashMap<>(body) : new HashMap<>();
                String cookies = extractCookies(response);
                ResponseEntity<Map<String, String>> csrf = rest.exchange(url("/api/csrf"), HttpMethod.GET,
                                request(cookies, null),
                                new ParameterizedTypeReference<Map<String, String>>() {
                                });
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
        void standardGymCannotAccessPremiumFeaturesUntilUpgraded() {
                Map<String, Object> regReq = new HashMap<>();
                regReq.put("fullName", "Standard Gym Owner");
                regReq.put("email", "standard-owner@gym.local");
                regReq.put("password", "SecurePass123!");
                regReq.put("phoneNumber", "+15551234567");
                regReq.put("organizationName", "Standard Fitness Club");
                regReq.put("organizationCode", "STDFIT");
                regReq.put("branchName", "Downtown");
                regReq.put("subscriptionTier", "STANDARD");

                ResponseEntity<Map<String, Object>> regRes = rest.exchange(
                                url("/api/auth/register-gym"), HttpMethod.POST, request(null, regReq), MAP_RESPONSE);
                assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

                String cookies = login("standard-owner@gym.local", "SecurePass123!").get("token").toString();

                ResponseEntity<String> ptRes = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(cookies, null), String.class);
                assertThat(ptRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(ptRes.getBody()).contains("Personal Trainer Management is a Premium feature");

                ResponseEntity<String> historyRes = rest.exchange(
                                url("/api/history"), HttpMethod.GET, request(cookies, null), String.class);
                assertThat(historyRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(historyRes.getBody()).contains("Audit History & Logs is a Premium feature");

                ResponseEntity<Map<String, Object>> directUpgrade = rest.exchange(
                                url("/api/subscription/upgrade"), HttpMethod.POST,
                                request(cookies, Map.of("tier", "PREMIUM")), MAP_RESPONSE);
                assertThat(directUpgrade.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

                String adminCookies = login("admin@gymflow.io", "AdminPassword123!").get("token").toString();
                ResponseEntity<Map<String, Object>> codeRes = rest.exchange(
                                url("/api/platform/activation-codes"), HttpMethod.POST,
                                request(adminCookies, Map.of("tier", "PREMIUM", "durationDays", 30)), MAP_RESPONSE);
                String activationCode = codeRes.getBody().get("code").toString();

                ResponseEntity<Map<String, Object>> upgradeRes = rest.exchange(
                                url("/api/subscription/redeem"), HttpMethod.POST,
                                request(cookies, Map.of("code", activationCode)), MAP_RESPONSE);
                assertThat(upgradeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(upgradeRes.getBody().get("subscriptionTier")).isEqualTo("PREMIUM");
                assertThat(upgradeRes.getBody().get("ptEnabled")).isEqualTo(true);
                assertThat(upgradeRes.getBody().get("historyEnabled")).isEqualTo(true);

                ResponseEntity<List<Map<String, Object>>> historyAfterUpgrade = rest.exchange(
                                url("/api/history"), HttpMethod.GET, request(cookies, null), LIST_RESPONSE);
                assertThat(historyAfterUpgrade.getStatusCode()).isEqualTo(HttpStatus.OK);

                ResponseEntity<List<Map<String, Object>>> ptAfterUpgrade = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(cookies, null), LIST_RESPONSE);
                assertThat(ptAfterUpgrade.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void superAdminAndActivationCodeAndCancellationLifecycle() {
                // 1. Super-Admin login
                String adminCookies = login("admin@gymflow.io", "AdminPassword123!").get("token").toString();

                // 2. Super-Admin fetches platform metrics
                ResponseEntity<Map<String, Object>> metricsRes = rest.exchange(
                                url("/api/platform/metrics"), HttpMethod.GET, request(adminCookies, null), MAP_RESPONSE);
                assertThat(metricsRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(metricsRes.getBody()).containsKey("totalGyms");

                // 3. Super-Admin generates an activation code for 365 days
                Map<String, Object> genReq = Map.of(
                                "tier", "PREMIUM",
                                "durationDays", 365,
                                "notes", "Annual contract for Apex Gym");
                ResponseEntity<Map<String, Object>> codeRes = rest.exchange(
                                url("/api/platform/activation-codes"), HttpMethod.POST, request(adminCookies, genReq), MAP_RESPONSE);
                assertThat(codeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                String activationCode = codeRes.getBody().get("code").toString();
                assertThat(activationCode).startsWith("GF-PRO-");

                // 4. Register a Standard Gym
                Map<String, Object> gymRegistration = Map.of(
                                "fullName", "Apex Owner",
                                "email", "apex-owner@gym.local",
                                "password", "ApexPass123!",
                                "organizationName", "Apex Fitness",
                                "organizationCode", "APEX",
                                "branchName", "Apex Central",
                                "subscriptionTier", "STANDARD");
                ResponseEntity<Map> regRes = rest.postForEntity(
                                url("/api/auth/register-gym"), gymRegistration, Map.class);
                assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

                // 5. Standard gym owner logs in
                String gymCookies = login("apex-owner@gym.local", "ApexPass123!").get("token").toString();

                // 6. Verify gym owner cannot access platform admin endpoints (Forbidden)
                ResponseEntity<String> forbiddenAdminRes = rest.exchange(
                                url("/api/platform/metrics"), HttpMethod.GET, request(gymCookies, null), String.class);
                assertThat(forbiddenAdminRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

                // 7. Verify standard gym is blocked from PT
                ResponseEntity<String> ptBlocked = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(gymCookies, null), String.class);
                assertThat(ptBlocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

                // 8. Redeem activation code
                Map<String, Object> redeemReq = Map.of("code", activationCode);
                ResponseEntity<Map<String, Object>> redeemRes = rest.exchange(
                                url("/api/subscription/redeem"), HttpMethod.POST, request(gymCookies, redeemReq), MAP_RESPONSE);
                assertThat(redeemRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(redeemRes.getBody().get("subscriptionTier")).isEqualTo("PREMIUM");
                assertThat(redeemRes.getBody().get("subscriptionStatus")).isEqualTo("ACTIVE");
                assertThat(redeemRes.getBody().get("expiresAt")).isNotNull();

                // 9. Attempting to redeem the same code again fails with 400
                ResponseEntity<String> duplicateRedeem = rest.exchange(
                                url("/api/subscription/redeem"), HttpMethod.POST, request(gymCookies, redeemReq), String.class);
                assertThat(duplicateRedeem.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                // 10. PT is now unlocked
                ResponseEntity<List<Map<String, Object>>> ptUnlocked = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(gymCookies, null), LIST_RESPONSE);
                assertThat(ptUnlocked.getStatusCode()).isEqualTo(HttpStatus.OK);

                // 11. Test Graceful Cancellation: cancel with immediate = false
                Map<String, Object> gracefulCancelReq = Map.of("immediate", false, "reason", "End of annual budget");
                ResponseEntity<Map<String, Object>> cancelRes = rest.exchange(
                                url("/api/subscription/cancel"), HttpMethod.POST, request(gymCookies, gracefulCancelReq), MAP_RESPONSE);
                assertThat(cancelRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(cancelRes.getBody().get("subscriptionStatus")).isEqualTo("CANCELLED");
                assertThat(cancelRes.getBody().get("expiresAt")).isNotNull();

                // Access is preserved during the grace period
                ResponseEntity<List<Map<String, Object>>> ptGracePeriod = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(gymCookies, null), LIST_RESPONSE);
                assertThat(ptGracePeriod.getStatusCode()).isEqualTo(HttpStatus.OK);

                // 12. Reactivate before expiration
                ResponseEntity<Map<String, Object>> reactivateRes = rest.exchange(
                                url("/api/subscription/reactivate"), HttpMethod.POST, request(gymCookies, null), MAP_RESPONSE);
                assertThat(reactivateRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(reactivateRes.getBody().get("subscriptionStatus")).isEqualTo("ACTIVE");

                // 13. Immediate Cancellation: cancel with immediate = true
                Map<String, Object> immediateCancelReq = Map.of("immediate", true, "reason", "Immediate downgrade");
                ResponseEntity<Map<String, Object>> immediateCancelRes = rest.exchange(
                                url("/api/subscription/cancel"), HttpMethod.POST, request(gymCookies, immediateCancelReq), MAP_RESPONSE);
                assertThat(immediateCancelRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(immediateCancelRes.getBody().get("subscriptionTier")).isEqualTo("STANDARD");

                // PT is now blocked again
                ResponseEntity<String> ptBlockedAgain = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(gymCookies, null), String.class);
                assertThat(ptBlockedAgain.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void upgradeRequestAndApprovalLifecycleTest() {
                // 1. Register a standard gym
                Map<String, Object> titanGym = Map.of(
                                "fullName", "Titan Owner",
                                "email", "titan-owner@gym.local",
                                "password", "TitanPass123!",
                                "organizationName", "Titan Athletics",
                                "organizationCode", "TITAN",
                                "branchName", "Titan North",
                                "subscriptionTier", "STANDARD");
                ResponseEntity<Map> regRes = rest.postForEntity(
                                url("/api/auth/register-gym"), titanGym, Map.class);
                assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

                String titanToken = login("titan-owner@gym.local", "TitanPass123!").get("token").toString();

                // 2. Verify PT is blocked
                ResponseEntity<String> ptBlocked = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(titanToken, null), String.class);
                assertThat(ptBlocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

                // 2b. Verify non-owner (STAFF) cannot submit upgrade request or view requests
                ResponseEntity<List<Map<String, Object>>> titanBranches = rest.exchange(
                                url("/api/branches"), HttpMethod.GET, request(titanToken, null), LIST_RESPONSE);
                Long titanBranchId = ((Number) titanBranches.getBody().get(0).get("id")).longValue();

                Map<String, Object> staffRequest = Map.of(
                                "fullName", "Titan Staff",
                                "email", "titan-staff-" + System.nanoTime() + "@gym.local",
                                "phoneNumber", "+1 415 555 0199",
                                "branchId", titanBranchId,
                                "password", "StaffPass123");
                rest.exchange(url("/api/staff"), HttpMethod.POST, request(titanToken, staffRequest), MAP_RESPONSE);
                String staffToken = (String) login((String) staffRequest.get("email"), "StaffPass123").get("token");

                ResponseEntity<String> staffUpgradeAttempt = rest.exchange(
                                url("/api/subscription/request-upgrade"), HttpMethod.POST,
                                request(staffToken, Map.of(
                                                "billingPlan", "Annual PRO (365 Days)",
                                                "durationDays", 365,
                                                "paymentMethod", "BANK_WIRE")),
                                String.class);
                assertThat(staffUpgradeAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

                ResponseEntity<String> staffMyRequestsAttempt = rest.exchange(
                                url("/api/subscription/my-requests"), HttpMethod.GET,
                                request(staffToken, null), String.class);
                assertThat(staffMyRequestsAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

                // 3. Gym owner submits an upgrade request
                Map<String, Object> upgradeReqBody = Map.of(
                                "billingPlan", "Annual PRO (365 Days)",
                                "durationDays", 365,
                                "paymentMethod", "BANK_WIRE",
                                "gstNumber", "29ABCDE1234F1Z5",
                                "billingAddress", "123 Commercial St, Bangalore",
                                "notes", "Wire transfer ref WT-998811");
                ResponseEntity<Map<String, Object>> submitRes = rest.exchange(
                                url("/api/subscription/request-upgrade"), HttpMethod.POST,
                                request(titanToken, upgradeReqBody), MAP_RESPONSE);
                assertThat(submitRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(submitRes.getBody().get("status")).isEqualTo("PENDING");
                assertThat(submitRes.getBody().get("billingPlanName")).isEqualTo("Annual PRO (365 Days)");
                Long requestId = ((Number) submitRes.getBody().get("id")).longValue();

                // 4. Owner lists their own requests
                ResponseEntity<List<Map<String, Object>>> myRequests = rest.exchange(
                                url("/api/subscription/my-requests"), HttpMethod.GET,
                                request(titanToken, null), LIST_RESPONSE);
                assertThat(myRequests.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(myRequests.getBody()).isNotEmpty();
                assertThat(myRequests.getBody().get(0).get("id")).isEqualTo(requestId.intValue());

                // 5. Super-Admin lists pending upgrade requests
                String adminToken = login("admin@gymflow.io", "AdminPassword123!").get("token").toString();
                ResponseEntity<List<Map<String, Object>>> pendingRequests = rest.exchange(
                                url("/api/platform/upgrade-requests?status=PENDING"), HttpMethod.GET,
                                request(adminToken, null), LIST_RESPONSE);
                assertThat(pendingRequests.getStatusCode()).isEqualTo(HttpStatus.OK);
                boolean found = pendingRequests.getBody().stream().anyMatch(r -> ((Number) r.get("id")).longValue() == requestId);
                assertThat(found).isTrue();

                // 6. Super-Admin approves with key
                ResponseEntity<Map<String, Object>> approveRes = rest.exchange(
                                url("/api/platform/upgrade-requests/" + requestId + "/approve-key"), HttpMethod.POST,
                                request(adminToken, null), MAP_RESPONSE);
                assertThat(approveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(approveRes.getBody().get("status")).isEqualTo("APPROVED_KEY_ISSUED");
                String issuedCode = approveRes.getBody().get("issuedActivationCode").toString();
                assertThat(issuedCode).startsWith("GF-PRO-");

                // 7. Gym owner redeems the issued code
                ResponseEntity<Map<String, Object>> redeemRes = rest.exchange(
                                url("/api/subscription/redeem"), HttpMethod.POST,
                                request(titanToken, Map.of("code", issuedCode)), MAP_RESPONSE);
                assertThat(redeemRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(redeemRes.getBody().get("subscriptionTier")).isEqualTo("PREMIUM");
                assertThat(redeemRes.getBody().get("ptEnabled")).isEqualTo(true);

                // 8. PT access is now unlocked!
                ResponseEntity<List<Map<String, Object>>> ptUnlocked = rest.exchange(
                                url("/api/pt"), HttpMethod.GET, request(titanToken, null), LIST_RESPONSE);
                assertThat(ptUnlocked.getStatusCode()).isEqualTo(HttpStatus.OK);

                // 9. Test Direct Upgrade workflow on another gym
                Map<String, Object> vanguardGym = Map.of(
                                "fullName", "Vanguard Owner",
                                "email", "vanguard-owner@gym.local",
                                "password", "VanguardPass123!",
                                "organizationName", "Vanguard Fitness",
                                "organizationCode", "VANGUARD",
                                "branchName", "Vanguard South",
                                "subscriptionTier", "STANDARD");
                rest.postForEntity(url("/api/auth/register-gym"), vanguardGym, Map.class);
                String vanguardToken = login("vanguard-owner@gym.local", "VanguardPass123!").get("token").toString();

                ResponseEntity<Map<String, Object>> vanguardReq = rest.exchange(
                                url("/api/subscription/request-upgrade"), HttpMethod.POST,
                                request(vanguardToken, Map.of(
                                                "billingPlan", "Half-Year PRO (180 Days)",
                                                "durationDays", 180,
                                                "paymentMethod", "UPI_QR",
                                                "notes", "UPI Txn 123456789")), MAP_RESPONSE);
                Long vanguardReqId = ((Number) vanguardReq.getBody().get("id")).longValue();

                // Super-Admin direct approves
                ResponseEntity<Map<String, Object>> directApproveRes = rest.exchange(
                                url("/api/platform/upgrade-requests/" + vanguardReqId + "/approve-direct"), HttpMethod.POST,
                                request(adminToken, Map.of("durationDays", 180)), MAP_RESPONSE);
                assertThat(directApproveRes.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(directApproveRes.getBody().get("status")).isEqualTo("APPROVED_DIRECT_UPGRADE");

                // Vanguard gym is now PREMIUM immediately
                ResponseEntity<Map<String, Object>> currentSub = rest.exchange(
                                url("/api/subscription/current"), HttpMethod.GET,
                                request(vanguardToken, null), MAP_RESPONSE);
                assertThat(currentSub.getBody().get("subscriptionTier")).isEqualTo("PREMIUM");
        }

        private Map<String, Object> cancellationRequest(String reason) {
                return Map.of("reason", reason, "remarks", "Member requested cancellation after review",
                                "cancellationDate", LocalDate.now(ZoneId.of("Asia/Kolkata")).toString(),
                                "otherExplanation", "");
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

        private String url(String path) {
                return "http://localhost:" + port + path;
        }

    @Test
    void memberUpgradeQuoteAndExecutionLifecycleTest() {
        String gymOwnerEmail = "upgrade-owner-" + System.nanoTime() + "@gym.local";
        Map<String, Object> gym = Map.of(
                "fullName", "Upgrade Owner",
                "email", gymOwnerEmail,
                "password", "OwnerPass123!",
                "organizationName", "Upgrade Gym " + System.nanoTime(),
                "organizationCode", "UPG" + (System.currentTimeMillis() % 10000),
                "branchName", "Main Branch",
                "subscriptionTier", "ENTERPRISE");
        rest.postForEntity(url("/api/auth/register-gym"), gym, Map.class);
        String ownerToken = login(gymOwnerEmail, "OwnerPass123!").get("token").toString();

        // Redeem Enterprise code to enable PT feature
        Map<String, Object> adminLogin = login("admin@gymflow.io", "AdminPassword123!");
        String adminToken = adminLogin.get("token").toString();
        Map<String, Object> genReq = Map.of(
                "tier", "ENTERPRISE",
                "durationDays", 365,
                "notes", "Enterprise test code"
        );
        ResponseEntity<Map<String, Object>> codeRes = rest.exchange(
                url("/api/platform/activation-codes"), HttpMethod.POST, request(adminToken, genReq), MAP_RESPONSE);
        String activationCode = codeRes.getBody().get("code").toString();
        rest.exchange(url("/api/subscription/redeem"), HttpMethod.POST, request(ownerToken, Map.of("code", activationCode)), MAP_RESPONSE);

        Map<String, Object> basicPlan = Map.of(
                "name", "Basic 30-Day",
                "description", "No PT",
                "price", 999,
                "durationDays", 30,
                "active", true,
                "ptPackageType", "NONE");
        ResponseEntity<Map<String, Object>> basicPlanRes = rest.exchange(url("/api/plans"), HttpMethod.POST,
                request(ownerToken, basicPlan), MAP_RESPONSE);
        Long basicPlanId = ((Number) basicPlanRes.getBody().get("id")).longValue();

        Map<String, Object> ptPlan = Map.of(
                "name", "Pro PT 30-Day",
                "description", "With PT",
                "price", 1499,
                "durationDays", 30,
                "active", true,
                "ptPackageType", "COMBINED",
                "includedPtSessions", 5,
                "ptFeeIncluded", 500);
        ResponseEntity<Map<String, Object>> ptPlanRes = rest.exchange(url("/api/plans"), HttpMethod.POST,
                request(ownerToken, ptPlan), MAP_RESPONSE);
        Long ptPlanId = ((Number) ptPlanRes.getBody().get("id")).longValue();

        String memberEmail = "upgrade-member-" + System.nanoTime() + "@example.test";
        Map<String, Object> memberReq = Map.of(
                "fullName", "Upgrade Member",
                "email", memberEmail,
                "phoneNumber", "+1 415 555 0188",
                "password", "MemberPass123",
                "planId", basicPlanId,
                "discountPercent", 0,
                "startDate", LocalDate.now().toString());
        ResponseEntity<Map<String, Object>> memberRes = rest.exchange(url("/api/memberships"), HttpMethod.POST,
                request(ownerToken, memberReq), MAP_RESPONSE);
        assertThat(memberRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long membershipId = ((Number) memberRes.getBody().get("id")).longValue();

        ResponseEntity<Map<String, Object>> quoteRes = rest.exchange(
                url("/api/memberships/" + membershipId + "/upgrade-quote?newPlanId=" + ptPlanId),
                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
        assertThat(quoteRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) quoteRes.getBody().get("totalDays")).intValue()).isEqualTo(30);
        assertThat(((Number) quoteRes.getBody().get("remainingDays")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) quoteRes.getBody().get("topUpDue")).doubleValue()).isGreaterThan(0);
        assertThat(quoteRes.getBody().get("hasPt")).isEqualTo(true);

        double topUpDue = ((Number) quoteRes.getBody().get("topUpDue")).doubleValue();

        Map<String, Object> upgradeReq = Map.of(
                "newPlanId", ptPlanId,
                "topUpPaid", topUpDue,
                "paymentMethod", "CASH");
        ResponseEntity<Map<String, Object>> upgradeRes = rest.exchange(
                url("/api/memberships/" + membershipId + "/upgrade"),
                HttpMethod.POST, request(ownerToken, upgradeReq), MAP_RESPONSE);
        assertThat(upgradeRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upgradeRes.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(((Number) upgradeRes.getBody().get("upgradedFromId")).longValue()).isEqualTo(membershipId);
        assertThat(((Number) upgradeRes.getBody().get("planId")).longValue()).isEqualTo(ptPlanId);

        Membership oldTerm = memberships.findById(membershipId).orElseThrow();
        assertThat(oldTerm.getStatus()).isEqualTo(MembershipStatus.UPGRADED);

        ResponseEntity<Map<String, Object>> saasQuote = rest.exchange(
                url("/api/subscription/upgrade-quote?targetTier=ENTERPRISE"),
                HttpMethod.GET, request(ownerToken, null), MAP_RESPONSE);
        assertThat(saasQuote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saasQuote.getBody().get("targetTier")).isEqualTo("ENTERPRISE");
        assertThat(((Number) saasQuote.getBody().get("targetMonthlyPrice")).doubleValue()).isEqualTo(25000.0);
    }

}
