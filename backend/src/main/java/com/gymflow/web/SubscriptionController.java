package com.gymflow.web;

import com.gymflow.service.SubscriptionService;
import static com.gymflow.web.ApiDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'MEMBER', 'PERSONAL_TRAINER', 'SUPER_ADMIN')")
    public SubscriptionStatusDto getCurrentSubscription(Principal principal) {
        return subscriptionService.getSubscription(principal.getName());
    }

    @PostMapping("/upgrade")
    @PreAuthorize("hasRole('OWNER')")
    public SubscriptionStatusDto upgrade(Principal principal,
            @RequestBody(required = false) SubscriptionUpgradeRequest request) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Direct self-upgrades are not permitted. Please submit an upgrade request or redeem an activation code.");
    }

    @PostMapping("/request-upgrade")
    @PreAuthorize("hasRole('OWNER')")
    public UpgradeRequestDto requestUpgrade(Principal principal,
            @Valid @RequestBody SubmitUpgradeRequestDto request) {
        return subscriptionService.submitUpgradeRequest(principal.getName(), request);
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('OWNER')")
    public List<UpgradeRequestDto> getMyUpgradeRequests(Principal principal) {
        return subscriptionService.listMyUpgradeRequests(principal.getName());
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasRole('OWNER')")
    public SubscriptionStatusDto redeem(Principal principal,
            @Valid @RequestBody RedeemCodeRequest request) {
        return subscriptionService.redeemCode(principal.getName(), request.code());
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasRole('OWNER')")
    public SubscriptionStatusDto cancel(Principal principal,
            @RequestBody(required = false) CancelSubscriptionRequest request) {
        boolean immediate = request != null && request.immediate();
        return subscriptionService.cancelSubscription(principal.getName(), immediate);
    }

    @PostMapping("/reactivate")
    @PreAuthorize("hasRole('OWNER')")
    public SubscriptionStatusDto reactivate(Principal principal) {
        return subscriptionService.reactivateSubscription(principal.getName());
    }
}
