package com.gymflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.SubscriptionTier;
import com.gymflow.repository.OrganizationRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.web.ApiDtos.GymRegistrationOtpRequest;
import com.gymflow.web.ApiDtos.GymRegistrationOtpResponse;
import com.gymflow.web.ApiDtos.GymRegistrationRequest;
import com.gymflow.web.ApiDtos.LoginResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class GymRegistrationOtpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository users;

    @Autowired
    private OrganizationRepository organizations;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void testRequestOtpSuccessfullyReturnsDevCode() {
        GymRegistrationOtpRequest req = new GymRegistrationOtpRequest("newowner@fitnessclub.com", "FITCLUB");
        ResponseEntity<GymRegistrationOtpResponse> response = rest.postForEntity(
                url("/api/auth/register-gym/send-otp"), req, GymRegistrationOtpResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Verification code sent");
        assertThat(response.getBody().developmentCode()).isNotNull().hasSize(6);
    }

    @Test
    void testRequestOtpFailsWhenEmailAlreadyExists() {
        // "owner@gym.local" is created by demo data
        GymRegistrationOtpRequest req = new GymRegistrationOtpRequest("owner@gym.local", "NEWGYM");
        ResponseEntity<Map> response = rest.postForEntity(
                url("/api/auth/register-gym/send-otp"), req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString()).contains("An account with this email already exists");
    }

    @Test
    void testRequestOtpFailsWhenOrgCodeAlreadyExists() {
        // "FORM" is created by demo data
        GymRegistrationOtpRequest req = new GymRegistrationOtpRequest("freshowner@fitness.com", "FORM");
        ResponseEntity<Map> response = rest.postForEntity(
                url("/api/auth/register-gym/send-otp"), req, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString()).contains("already exists");
    }

    @Test
    void testRequestOtpRateLimitedWhenRequestedTooFast() {
        GymRegistrationOtpRequest req = new GymRegistrationOtpRequest("cooldown@fitness.com", "COOLDOWN");
        ResponseEntity<GymRegistrationOtpResponse> first = rest.postForEntity(
                url("/api/auth/register-gym/send-otp"), req, GymRegistrationOtpResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Immediate second request with same email -> 429
        ResponseEntity<Map> second = rest.postForEntity(
                url("/api/auth/register-gym/send-otp"), req, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getBody().get("message").toString()).contains("recently sent");
    }

    @Test
    void testRegisterGymFailsWithInvalidOtp() {
        GymRegistrationOtpRequest otpReq = new GymRegistrationOtpRequest("invalidotp@fitness.com", "INVOTP");
        rest.postForEntity(url("/api/auth/register-gym/send-otp"), otpReq, GymRegistrationOtpResponse.class);

        GymRegistrationRequest regReq = new GymRegistrationRequest(
                "Gym Owner", "invalidotp@fitness.com", "+1234567890", "SecurePass123!",
                "Invalid OTP Gym", "INVOTP", "Main", "100 Fitness St",
                SubscriptionTier.STANDARD, "IN", "000000");

        ResponseEntity<Map> regRes = rest.postForEntity(
                url("/api/auth/register-gym"), regReq, Map.class);

        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(regRes.getBody().get("message").toString()).contains("Invalid verification code");
    }

    @Test
    void testRegisterGymFailsWithExpiredOrNonExistentOtp() {
        GymRegistrationRequest regReq = new GymRegistrationRequest(
                "Gym Owner", "nootp@fitness.com", "+1234567890", "SecurePass123!",
                "No OTP Gym", "NOOTP", "Main", "100 Fitness St",
                SubscriptionTier.STANDARD, "IN", "123456");

        ResponseEntity<Map> regRes = rest.postForEntity(
                url("/api/auth/register-gym"), regReq, Map.class);

        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(regRes.getBody().get("message").toString()).contains("Invalid or expired verification code");
    }

    @Test
    void testRegisterGymSucceedsWithValidOtpAndConsumesIt() {
        String email = "success@fitness.com";
        String orgCode = "SUCCESSOK";

        GymRegistrationOtpRequest otpReq = new GymRegistrationOtpRequest(email, orgCode);
        ResponseEntity<GymRegistrationOtpResponse> otpRes = rest.postForEntity(
                url("/api/auth/register-gym/send-otp"), otpReq, GymRegistrationOtpResponse.class);

        assertThat(otpRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String validCode = otpRes.getBody().developmentCode();
        assertThat(validCode).isNotNull();

        GymRegistrationRequest regReq = new GymRegistrationRequest(
                "Successful Owner", email, "+1234567890", "OwnerPass123!",
                "Success Gym", orgCode, "Main Branch", "456 Muscle Way",
                SubscriptionTier.STANDARD, "IN", validCode);

        ResponseEntity<LoginResponse> regRes = rest.postForEntity(
                url("/api/auth/register-gym"), regReq, LoginResponse.class);

        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regRes.getBody()).isNotNull();
        assertThat(regRes.getBody().user().email()).isEqualTo(email);
        assertThat(users.existsByEmailIgnoreCase(email)).isTrue();
        assertThat(organizations.findByCodeIgnoreCase(orgCode)).isPresent();

        // Attempting to register again with same OTP must fail
        GymRegistrationRequest reuseReq = new GymRegistrationRequest(
                "Another Owner", email, "+1234567890", "OwnerPass123!",
                "Another Gym", "ANOTHER", "Main", "100 St",
                SubscriptionTier.STANDARD, "IN", validCode);

        ResponseEntity<Map> reuseRes = rest.postForEntity(
                url("/api/auth/register-gym"), reuseReq, Map.class);
        // Either email conflict or OTP consumed
        assertThat(reuseRes.getStatusCode().is4xxClientError()).isTrue();
    }
}

