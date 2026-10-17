package com.gymflow.web;

import com.gymflow.repository.UserRepository;
import com.gymflow.security.JwtService;
import com.gymflow.service.GymService;
import com.gymflow.service.PasswordResetService;
import static com.gymflow.web.ApiDtos.*;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final JwtService jwtService;
    private final GymService gymService;
    private final PasswordResetService passwordResetService;

    @Value("${gym.app.url:http://localhost:4200}")
    private String appUrl;

    @Value("${gym.auth.cookie-secure:#{null}}")
    private Boolean cookieSecureConfig;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService,
            GymService gymService, PasswordResetService passwordResetService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.gymService = gymService;
        this.passwordResetService = passwordResetService;
    }

    private boolean isSecureCookie(jakarta.servlet.http.HttpServletRequest request) {
        if (Boolean.TRUE.equals(cookieSecureConfig)) {
            return true;
        }
        if (request != null && (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")))) {
            return true;
        }
        if (appUrl != null && appUrl.toLowerCase().startsWith("https://")) {
            return true;
        }
        return false;
    }

    private void attachJwtCookie(jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest request, String token, int maxAgeSeconds) {
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie.from("gym_jwt", token)
                .httpOnly(true)
                .secure(isSecureCookie(request))
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        
        String token = jwtService.generate(user);
        attachJwtCookie(response, httpRequest, token, (int) (jwtService.getExpirationMs() / 1000));
        
        return new LoginResponse(null, gymService.userDto(user.getEmail()));
    }

    @PostMapping("/register-gym")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse registerGym(@Valid @RequestBody GymRegistrationRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        UserDto created = gymService.registerGym(request);
        
        String token = jwtService.generate(gymService.account(created.email()));
        attachJwtCookie(response, httpRequest, token, (int) (jwtService.getExpirationMs() / 1000));
        
        return new LoginResponse(null, created);
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return passwordResetService.request(request.email());
    }

    @PostMapping("/password-reset/confirm")
    public org.springframework.http.ResponseEntity<String> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        boolean changed = passwordResetService.confirm(request.email(), request.code(), request.newPassword());
        if (!changed)
            return org.springframework.http.ResponseEntity.badRequest().body("Invalid or expired reset code");
        return org.springframework.http.ResponseEntity.ok("Password updated. You can now sign in.");
    }

    @PostMapping("/change-password")
    public LoginResponse changePassword(
            Principal principal, @Valid @RequestBody PasswordChangeRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        UserDto user = gymService.changePassword(principal.getName(), request);
        
        String token = jwtService.generate(gymService.account(user.email()));
        attachJwtCookie(response, httpRequest, token, (int) (jwtService.getExpirationMs() / 1000));
        
        return new LoginResponse(null, user);
    }

    @PutMapping("/profile")
    public LoginResponse updateProfile(Principal principal, @Valid @RequestBody ProfileRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        UserDto updated = gymService.updateProfile(principal.getName(), request);
        
        String token = jwtService.generate(gymService.account(updated.email()));
        attachJwtCookie(response, httpRequest, token, (int) (jwtService.getExpirationMs() / 1000));
        
        return new LoginResponse(null, updated);
    }

    @PostMapping("/logout")
    public void logout(Principal principal,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (principal != null) {
            gymService.invalidateUserTokens(principal.getName());
        }
        attachJwtCookie(response, httpRequest, "", 0);
    }
}