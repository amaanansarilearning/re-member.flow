package com.gymflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymflow.domain.*;
import com.gymflow.repository.*;
import com.gymflow.web.ApiDtos.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SecurityHardeningIntegrationTest {
        private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE = new ParameterizedTypeReference<>() {
        };
        private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_RESPONSE = new ParameterizedTypeReference<>() {
        };

        @LocalServerPort
        private int port;

        @Autowired
        private TestRestTemplate rest;

        @Autowired
        private OrganizationRepository organizations;

        @Autowired
        private BranchRepository branches;

        @Autowired
        private UserRepository users;

        @Autowired
        private PlanRepository plans;

        @Autowired
        private MembershipRepository memberships;

        @Autowired
        private PersonalTrainerRepository trainers;

        @Autowired
        private PtSessionRepository sessions;

        @Autowired
        private PasswordEncoder encoder;

        private GymOrganization org1;
        private GymBranch branch1;
        private UserAccount owner1;
        private UserAccount member1;
        private UserAccount trainerUser1;
        private PersonalTrainer pt1;
        private Membership membership1;
        private MembershipPlan plan1;

        private GymOrganization org2;
        private GymBranch branch2;
        private UserAccount owner2;
        private UserAccount member2;
        private Membership membership2;

        @BeforeEach
        void setUp() {
                // Setup Org 1
                org1 = organizations.save(new GymOrganization("Alpha Gym", "ALPHA", SubscriptionTier.PREMIUM));
                branch1 = branches.save(new GymBranch(org1, "Alpha Central", "CEN", BranchType.COMPANY_OWNED, "1st Ave",
                                "111-222"));
                owner1 = users.save(new UserAccount("Owner Alpha", "owner1@alpha.test", "111",
                                encoder.encode("password123"), Role.OWNER, org1, null));
                member1 = users.save(new UserAccount("Member Alpha", "member1@alpha.test", "222",
                                encoder.encode("password123"), Role.MEMBER, org1, branch1));
                trainerUser1 = users.save(new UserAccount("Trainer Alpha", "pt1@alpha.test", "333",
                                encoder.encode("password123"), Role.PERSONAL_TRAINER, org1, branch1));
                pt1 = trainers.save(new PersonalTrainer(trainerUser1, org1, branch1, "Strength", "Bio",
                                new BigDecimal("50.00"), new BigDecimal("70.00")));
                plan1 = plans.save(new MembershipPlan("Gold Plan", "Full access", new BigDecimal("100.00"), 1));
                plan1.assignLocation(org1, null);
                plans.save(plan1);
                membership1 = memberships.save(
                                new Membership(member1, plan1, branch1, LocalDate.now(), LocalDate.now().plusMonths(1),
                                                plan1.getPrice(), plan1.getPrice(), BigDecimal.ZERO, null, pt1));

                // Setup Org 2 (separate tenant)
                org2 = organizations.save(new GymOrganization("Beta Gym", "BETA", SubscriptionTier.STANDARD));
                branch2 = branches.save(new GymBranch(org2, "Beta North", "NOR", BranchType.COMPANY_OWNED, "2nd Ave",
                                "333-444"));
                owner2 = users.save(new UserAccount("Owner Beta", "owner2@beta.test", "444",
                                encoder.encode("password123"), Role.OWNER, org2, null));
                member2 = users.save(new UserAccount("Member Beta", "member2@beta.test", "555",
                                encoder.encode("password123"), Role.MEMBER, org2, branch2));
                MembershipPlan plan2 = plans
                                .save(new MembershipPlan("Silver Plan", "Gym access", new BigDecimal("80.00"), 1));
                plan2.assignLocation(org2, null);
                plans.save(plan2);
                membership2 = memberships.save(
                                new Membership(member2, plan2, branch2, LocalDate.now(), LocalDate.now().plusMonths(1),
                                                plan2.getPrice(), plan2.getPrice(), BigDecimal.ZERO, null, null));
        }

        private String loginAndGetCookie(String email, String password) {
                ResponseEntity<Map<String, Object>> response = rest.exchange(
                                "/api/auth/login",
                                HttpMethod.POST,
                                new HttpEntity<>(new LoginRequest(email, password)),
                                MAP_RESPONSE);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
                assertThat(cookies).isNotNull();
                return cookies.stream().filter(c -> c.startsWith("gym_jwt=")).findFirst().orElseThrow();
        }

        private String fetchCsrfToken(String cookie) {
                HttpHeaders headers = new HttpHeaders();
                if (cookie != null)
                        headers.add(HttpHeaders.COOKIE, cookie);
                ResponseEntity<Map<String, Object>> response = rest.exchange(
                                "/api/csrf", HttpMethod.GET, new HttpEntity<>(headers), MAP_RESPONSE);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                return (String) response.getBody().get("token");
        }

        @Test
        void testPublicGymRegistrationLocksToStandardTier() {
                GymRegistrationRequest req = new GymRegistrationRequest(
                                "Sneaky Gym Owner", "sneaky@gym.test", "999-000", "password123",
                                "Sneaky Gym", "SNEAKY", "Main", "101 Street",
                                SubscriptionTier.PREMIUM); // Attempting to request PREMIUM for free

                ResponseEntity<LoginResponse> response = rest.postForEntity(
                                "/api/auth/register-gym", req, LoginResponse.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                UserDto created = response.getBody().user();
                assertThat(created.subscriptionTier()).isEqualTo(SubscriptionTier.STANDARD);

                GymOrganization createdOrg = organizations.findByCodeIgnoreCase("SNEAKY").orElseThrow();
                assertThat(createdOrg.getSubscriptionTier()).isEqualTo(SubscriptionTier.STANDARD);
        }

        @Test
        void testDirectSelfUpgradeIsForbidden() {
                String ownerCookie = loginAndGetCookie("owner2@beta.test", "password123");
                String csrfToken = fetchCsrfToken(ownerCookie);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.COOKIE, ownerCookie);
                headers.add("X-XSRF-TOKEN", csrfToken);

                ResponseEntity<Map<String, Object>> response = rest.exchange(
                                "/api/subscription/upgrade",
                                HttpMethod.POST,
                                new HttpEntity<>(new SubscriptionUpgradeRequest(SubscriptionTier.PREMIUM), headers),
                                MAP_RESPONSE);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void testLogoutRevokesTokenServerSide() {
                String ownerCookie = loginAndGetCookie("owner1@alpha.test", "password123");
                String csrfToken = fetchCsrfToken(ownerCookie);

                // Before logout, access works
                HttpHeaders authHeaders = new HttpHeaders();
                authHeaders.add(HttpHeaders.COOKIE, ownerCookie);
                ResponseEntity<Map<String, Object>> checkBefore = rest.exchange(
                                "/api/subscription/current", HttpMethod.GET, new HttpEntity<>(authHeaders),
                                MAP_RESPONSE);
                assertThat(checkBefore.getStatusCode()).isEqualTo(HttpStatus.OK);

                // Perform logout with CSRF token
                HttpHeaders logoutHeaders = new HttpHeaders();
                logoutHeaders.add(HttpHeaders.COOKIE, ownerCookie);
                logoutHeaders.add("X-XSRF-TOKEN", csrfToken);
                ResponseEntity<Void> logoutResponse = rest.exchange(
                                "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(logoutHeaders), Void.class);
                assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                // Try using the original cookie again -> MUST be rejected because tokenVersion
                // was incremented
                ResponseEntity<Map<String, Object>> checkAfter = rest.exchange(
                                "/api/subscription/current", HttpMethod.GET, new HttpEntity<>(authHeaders),
                                MAP_RESPONSE);
                assertThat(checkAfter.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void testCsrfProtectionEnforcedOnPasswordChange() {
                String ownerCookie = loginAndGetCookie("owner1@alpha.test", "password123");

                // Attempting to change password WITHOUT CSRF token
                HttpHeaders noCsrfHeaders = new HttpHeaders();
                noCsrfHeaders.add(HttpHeaders.COOKIE, ownerCookie);

                ResponseEntity<Map<String, Object>> response = rest.exchange(
                                "/api/auth/change-password",
                                HttpMethod.POST,
                                new HttpEntity<>(new PasswordChangeRequest("password123", "newPassword456!"),
                                                noCsrfHeaders),
                                MAP_RESPONSE);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void testMemberPinIsRedactedFromMembershipDtos() {
                String ptCookie = loginAndGetCookie("pt1@alpha.test", "password123");

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.COOKIE, ptCookie);

                ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                                "/api/pt/me/trainees",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                LIST_RESPONSE);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                List<Map<String, Object>> trainees = response.getBody();
                assertThat(trainees).isNotEmpty();
                // memberPin field must be null/redacted
                assertThat(trainees.get(0).get("memberPin")).isNull();
        }

        @Test
        void testMemberCanUpdateTheirPinAndOthersCannot() {
                String memberCookie = loginAndGetCookie("member1@alpha.test", "password123");
                String csrfToken = fetchCsrfToken(memberCookie);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.COOKIE, memberCookie);
                headers.add("X-XSRF-TOKEN", csrfToken);

                // Member updates their own PIN
                ResponseEntity<Void> updateRes = rest.exchange(
                                "/api/memberships/my-pin",
                                HttpMethod.PUT,
                                new HttpEntity<>(new UpdatePinRequest("7890"), headers),
                                Void.class);
                assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

                UserAccount updatedMember = users.findByEmailIgnoreCase("member1@alpha.test").orElseThrow();
                assertThat(updatedMember.getMemberPin()).isEqualTo("7890");

                // An OWNER or PT calling /api/memberships/my-pin is forbidden
                String ptCookie = loginAndGetCookie("pt1@alpha.test", "password123");
                String ptCsrf = fetchCsrfToken(ptCookie);
                HttpHeaders ptHeaders = new HttpHeaders();
                ptHeaders.add(HttpHeaders.COOKIE, ptCookie);
                ptHeaders.add("X-XSRF-TOKEN", ptCsrf);

                ResponseEntity<Void> ptAttempt = rest.exchange(
                                "/api/memberships/my-pin",
                                HttpMethod.PUT,
                                new HttpEntity<>(new UpdatePinRequest("1111"), ptHeaders),
                                Void.class);
                assertThat(ptAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void testBolaCrossOrganizationBookingForbidden() {
                // User from Org 1 attempts to book session on membership from Org 2
                String owner1Cookie = loginAndGetCookie("owner1@alpha.test", "password123");
                String csrf = fetchCsrfToken(owner1Cookie);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.COOKIE, owner1Cookie);
                headers.add("X-XSRF-TOKEN", csrf);

                PtSessionBookRequest crossOrgBooking = new PtSessionBookRequest(
                                membership2.getId(), // Belongs to Beta Gym
                                LocalDate.now().plusDays(2),
                                LocalTime.of(10, 0),
                                LocalTime.of(11, 0),
                                "Illicit cross-org booking");

                ResponseEntity<Map<String, Object>> response = rest.exchange(
                                "/api/pt/sessions",
                                HttpMethod.POST,
                                new HttpEntity<>(crossOrgBooking, headers),
                                MAP_RESPONSE);

                assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
        }

        @Test
        void testBolaMemberCannotBookForAnotherMember() {
                // Create second member in Org 1
                UserAccount otherMember = users.save(new UserAccount("Other Member", "other@alpha.test", "888",
                                encoder.encode("password123"), Role.MEMBER, org1, branch1));
                Membership otherMembership = memberships
                                .save(new Membership(otherMember, plan1, branch1, LocalDate.now(),
                                                LocalDate.now().plusMonths(1), plan1.getPrice(), plan1.getPrice(),
                                                BigDecimal.ZERO, null, pt1));

                // Member 1 attempts to book session using other member's membership ID
                String member1Cookie = loginAndGetCookie("member1@alpha.test", "password123");
                String csrf = fetchCsrfToken(member1Cookie);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.COOKIE, member1Cookie);
                headers.add("X-XSRF-TOKEN", csrf);

                PtSessionBookRequest attackBooking = new PtSessionBookRequest(
                                otherMembership.getId(),
                                LocalDate.now().plusDays(3),
                                LocalTime.of(14, 0),
                                LocalTime.of(15, 0),
                                "Attempting to hijack session");

                ResponseEntity<Map<String, Object>> response = rest.exchange(
                                "/api/pt/sessions",
                                HttpMethod.POST,
                                new HttpEntity<>(attackBooking, headers),
                                MAP_RESPONSE);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void testPtSessionCompletionRequiresValidPin() {
                // Book a session
                membership1.assignPersonalTrainer(pt1, 10, pt1.getCommissionPercent());
                memberships.save(membership1);

                PtSession session = sessions.save(new PtSession(pt1, membership1,
                                LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(10, 0),
                                "Legitimate session", pt1.getCommissionPercent(), pt1.getSessionFeePerHour()));

                member1.updateMemberPin("4321");
                users.save(member1);

                String ptCookie = loginAndGetCookie("pt1@alpha.test", "password123");
                String csrf = fetchCsrfToken(ptCookie);

                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.COOKIE, ptCookie);
                headers.add("X-XSRF-TOKEN", csrf);

                // Attempt completion with wrong PIN -> Bad Request
                PtSessionCompleteRequest badPinReq = new PtSessionCompleteRequest("Completed workout", "0000");
                ResponseEntity<Map<String, Object>> badResponse = rest.exchange(
                                "/api/pt/sessions/" + session.getId() + "/complete",
                                HttpMethod.POST,
                                new HttpEntity<>(badPinReq, headers),
                                MAP_RESPONSE);
                assertThat(badResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                // Attempt completion with correct PIN -> OK
                PtSessionCompleteRequest goodPinReq = new PtSessionCompleteRequest("Completed workout", "4321");
                ResponseEntity<PtSessionDto> goodResponse = rest.exchange(
                                "/api/pt/sessions/" + session.getId() + "/complete",
                                HttpMethod.POST,
                                new HttpEntity<>(goodPinReq, headers),
                                PtSessionDto.class);
                assertThat(goodResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(goodResponse.getBody().status()).isEqualTo("COMPLETED");
                assertThat(goodResponse.getBody().verificationMethod()).isEqualTo("MEMBER_PIN");
        }
}