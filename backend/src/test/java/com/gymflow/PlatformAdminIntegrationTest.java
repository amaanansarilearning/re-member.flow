package com.gymflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymflow.domain.GymOrganization;
import com.gymflow.repository.OrganizationRepository;
import com.gymflow.web.ApiDtos.LoginRequest;
import com.gymflow.web.ApiDtos.LoginResponse;
import com.gymflow.web.ApiDtos.PlatformGymDetailDto;
import com.gymflow.web.ApiDtos.PlatformMetricsDto;
import com.gymflow.web.ApiDtos.PlatformOrganizationDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PlatformAdminIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OrganizationRepository organizations;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders adminHeaders() {
        LoginRequest req = new LoginRequest("admin@rememberflow.com", "AdminPassword123!");
        ResponseEntity<LoginResponse> response = rest.postForEntity(url("/api/auth/login"), req, LoginResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return headers;
    }

    @Test
    void testGetPlatformMetricsReturnsEnrichedDecisionMetrics() {
        HttpEntity<?> entity = new HttpEntity<>(adminHeaders());
        ResponseEntity<PlatformMetricsDto> response = rest.exchange(
                url("/api/platform/metrics"), HttpMethod.GET, entity, PlatformMetricsDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlatformMetricsDto metrics = response.getBody();
        assertThat(metrics).isNotNull();
        assertThat(metrics.totalGyms()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.totalPlatformGmv()).isNotNull();
        assertThat(metrics.platformEstimatedMrr()).isNotNull();
        assertThat(metrics.healthyGymsCount()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.atRiskGymsCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testListOrganizationsReturnsDetailedUsageAndHealthScores() {
        HttpEntity<?> entity = new HttpEntity<>(adminHeaders());
        ResponseEntity<List<PlatformOrganizationDto>> response = rest.exchange(
                url("/api/platform/organizations"), HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<PlatformOrganizationDto>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<PlatformOrganizationDto> orgs = response.getBody();
        assertThat(orgs).isNotEmpty();

        PlatformOrganizationDto formOrg = orgs.stream()
                .filter(o -> "FORM".equalsIgnoreCase(o.code()))
                .findFirst()
                .orElse(null);
        assertThat(formOrg).isNotNull();
        assertThat(formOrg.healthScore()).isBetween(0, 100);
        assertThat(formOrg.healthStatus()).isIn("HEALTHY", "MODERATE", "AT_RISK");
        assertThat(formOrg.decisionInsights()).isNotEmpty();
        assertThat(formOrg.totalRevenue()).isNotNull();
        assertThat(formOrg.branchCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void testGetOrganizationDetailsReturnsFull360Analytics() {
        GymOrganization org = organizations.findByCodeIgnoreCase("FORM").orElseThrow();
        HttpEntity<?> entity = new HttpEntity<>(adminHeaders());
        ResponseEntity<PlatformGymDetailDto> response = rest.exchange(
                url("/api/platform/organizations/" + org.getId() + "/details"),
                HttpMethod.GET, entity, PlatformGymDetailDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlatformGymDetailDto detail = response.getBody();
        assertThat(detail).isNotNull();
        assertThat(detail.organization().code()).isEqualTo("FORM");
        assertThat(detail.branches()).isNotEmpty();
        assertThat(detail.plans()).isNotEmpty();
        assertThat(detail.financials()).isNotNull();
        assertThat(detail.serviceUsage()).isNotNull();
        assertThat(detail.decisionSupport()).isNotNull();
        assertThat(detail.decisionSupport().healthScore()).isBetween(0, 100);
        assertThat(detail.decisionSupport().churnRiskLevel()).isIn("LOW", "MEDIUM", "HIGH");
        assertThat(detail.decisionSupport().strategicRecommendations()).isNotEmpty();
    }

    @Test
    void testNonAdminCannotAccessPlatformMetrics() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/platform/metrics"), String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}

