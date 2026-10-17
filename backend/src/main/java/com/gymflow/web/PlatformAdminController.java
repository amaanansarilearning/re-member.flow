package com.gymflow.web;

import com.gymflow.domain.UpgradeRequestStatus;
import com.gymflow.service.SubscriptionService;
import static com.gymflow.web.ApiDtos.*;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/platform")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformAdminController {
    private final SubscriptionService subscriptionService;

    public PlatformAdminController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/metrics")
    public PlatformMetricsDto getMetrics() {
        return subscriptionService.getPlatformMetrics();
    }

    @GetMapping("/organizations")
    public List<PlatformOrganizationDto> listOrganizations(
            @RequestParam(required = false) String search) {
        return subscriptionService.listAllOrganizations(search);
    }

    @PutMapping("/organizations/{id}/subscription")
    public PlatformOrganizationDto updateOrganizationSubscription(
            @PathVariable Long id,
            @RequestBody UpdateSubscriptionAdminRequest request) {
        return subscriptionService.updateOrganizationSubscriptionAdmin(
                id,
                request.tier(),
                request.status(),
                request.expiresAt()
        );
    }

    @PostMapping("/organizations/{id}/extend")
    public PlatformOrganizationDto extendOrganizationSubscription(
            @PathVariable Long id,
            @Valid @RequestBody ExtendSubscriptionRequest request) {
        return subscriptionService.extendOrganizationSubscription(id, request.days());
    }

    @PostMapping("/organizations/{id}/suspend")
    public PlatformOrganizationDto suspendOrganization(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean suspend) {
        return subscriptionService.suspendOrganization(id, suspend);
    }

    @GetMapping("/activation-codes")
    public List<ActivationCodeDto> listActivationCodes() {
        return subscriptionService.listActivationCodes();
    }

    @PostMapping("/activation-codes")
    public ActivationCodeDto generateActivationCode(
            Principal principal,
            @RequestBody GenerateCodeRequest request) {
        return subscriptionService.generateActivationCode(
                principal != null ? principal.getName() : "system",
                request.tier(),
                request.durationDays(),
                request.notes()
        );
    }

    @DeleteMapping("/activation-codes/{id}")
    public ActivationCodeDto revokeActivationCode(@PathVariable Long id) {
        return subscriptionService.revokeActivationCode(id);
    }

    @GetMapping("/upgrade-requests")
    public List<UpgradeRequestDto> listUpgradeRequests(@RequestParam(required = false) UpgradeRequestStatus status) {
        return subscriptionService.listAllUpgradeRequests(status);
    }

    @PostMapping("/upgrade-requests/{id}/approve-key")
    public UpgradeRequestDto approveUpgradeRequestWithKey(@PathVariable Long id, Principal principal) {
        return subscriptionService.approveUpgradeRequestWithKey(id, principal != null ? principal.getName() : "system");
    }

    @PostMapping("/upgrade-requests/{id}/approve-direct")
    public UpgradeRequestDto approveUpgradeRequestDirect(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveUpgradeDirectDto request,
            Principal principal) {
        Integer durationDays = request != null ? request.durationDays() : null;
        return subscriptionService.approveUpgradeRequestDirect(id, durationDays, principal != null ? principal.getName() : "system");
    }

    @PostMapping("/upgrade-requests/{id}/reject")
    public UpgradeRequestDto rejectUpgradeRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectUpgradeDto request,
            Principal principal) {
        return subscriptionService.rejectUpgradeRequest(id, request.reason(), principal != null ? principal.getName() : "system");
    }
}

