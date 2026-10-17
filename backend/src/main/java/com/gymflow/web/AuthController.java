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
    private final String ownerSetupKey;

    public AuthController(AuthenticationManager authenticationManager, UserRepository users, JwtService jwtService,
            GymService gymService, PasswordResetService passwordResetService,
            @Value("${gym.owner-setup.key:}") String ownerSetupKey) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
        this.gymService = gymService;
        this.passwordResetService = passwordResetService;
        this.ownerSetupKey = ownerSetupKey;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletResponse response) {
        authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        var user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        
        String token = jwtService.generate(user);
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("gym_jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtService.getExpirationMs() / 1000));
        response.addCookie(cookie);
        
        return new LoginResponse(null, gymService.userDto(user.getEmail()));
    }

    @PostMapping("/setup-owner")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse setupOwner(@Valid @RequestBody SetupOwnerRequest request, jakarta.servlet.http.HttpServletResponse response) {
        UserDto created = gymService.createOwner(request, ownerSetupKey);
        
        String token = jwtService.generate(gymService.account(created.email()));
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("gym_jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtService.getExpirationMs() / 1000));
        response.addCookie(cookie);
        
        return new LoginResponse(null, created);
    }

    @PostMapping("/register-gym")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse registerGym(@Valid @RequestBody GymRegistrationRequest request, jakarta.servlet.http.HttpServletResponse response) {
        UserDto created = gymService.registerGym(request);
        
        String token = jwtService.generate(gymService.account(created.email()));
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("gym_jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtService.getExpirationMs() / 1000));
        response.addCookie(cookie);
        
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
            Principal principal, @Valid @RequestBody PasswordChangeRequest request, jakarta.servlet.http.HttpServletResponse response) {
        UserDto user = gymService.changePassword(principal.getName(), request);
        
        String token = jwtService.generate(gymService.account(user.email()));
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("gym_jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtService.getExpirationMs() / 1000));
        response.addCookie(cookie);
        
        return new LoginResponse(null, user);
    }

    @PutMapping("/profile")
    public LoginResponse updateProfile(Principal principal, @Valid @RequestBody ProfileRequest request, jakarta.servlet.http.HttpServletResponse response) {
        UserDto updated = gymService.updateProfile(principal.getName(), request);
        
        String token = jwtService.generate(gymService.account(updated.email()));
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("gym_jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtService.getExpirationMs() / 1000));
        response.addCookie(cookie);
        
        return new LoginResponse(null, updated);
    }

    @PostMapping("/logout")
    public void logout(jakarta.servlet.http.HttpServletResponse response) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("gym_jwt", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}