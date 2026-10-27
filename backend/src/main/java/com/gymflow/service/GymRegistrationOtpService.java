package com.gymflow.service;

import com.gymflow.domain.GymRegistrationOtp;
import com.gymflow.repository.GymRegistrationOtpRepository;
import com.gymflow.repository.OrganizationRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.web.ApiDtos.GymRegistrationOtpRequest;
import com.gymflow.web.ApiDtos.GymRegistrationOtpResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GymRegistrationOtpService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GymRegistrationOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GymRegistrationOtpRepository otpRepository;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean exposeCode;
    private final Environment env;

    public GymRegistrationOtpService(
            GymRegistrationOtpRepository otpRepository,
            UserRepository users,
            OrganizationRepository organizations,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            @Value("${gym.mail.from:noreply@rememberflow.com}") String fromAddress,
            @Value("${gym.registration-otp.expose-code:false}") boolean exposeCode,
            Environment env) {
        this.otpRepository = otpRepository;
        this.users = users;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.exposeCode = exposeCode;
        this.env = env;
    }

    @Transactional
    public GymRegistrationOtpResponse requestOtp(GymRegistrationOtpRequest request) {
        String normalizedEmail = request.email() != null ? request.email().trim().toLowerCase() : "";
        if (normalizedEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address is required.");
        }

        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        if (request.organizationCode() != null && !request.organizationCode().isBlank()) {
            String orgCode = request.organizationCode().trim();
            if (organizations.findByCodeIgnoreCase(orgCode).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A gym with code " + orgCode + " already exists");
            }
        }

        Instant now = Instant.now();
        if (otpRepository.existsByEmailIgnoreCaseAndCreatedAtAfter(normalizedEmail, now.minusSeconds(60))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "A verification code was recently sent. Please wait before requesting another.");
        }

        // Invalidate previous unconsumed OTPs for this email
        otpRepository.findAllByEmailIgnoreCaseAndConsumedAtIsNull(normalizedEmail)
                .forEach(GymRegistrationOtp::consume);

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        otpRepository.save(new GymRegistrationOtp(
                normalizedEmail,
                passwordEncoder.encode(code),
                now,
                now.plusSeconds(600)));

        sendOtpEmail(normalizedEmail, code);

        boolean canExpose = exposeCode && env.acceptsProfiles(Profiles.of("dev", "test"));
        String devCode = canExpose ? code : null;

        return new GymRegistrationOtpResponse(
                "Verification code sent to " + normalizedEmail + ". It expires in 10 minutes.",
                devCode);
    }

    @Transactional
    public void verifyAndConsumeOtp(String email, String rawCode) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        if (normalizedEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email address is required.");
        }

        if (rawCode == null || rawCode.trim().length() != 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid 6-digit verification code is required.");
        }

        var match = otpRepository
                .findFirstByEmailIgnoreCaseAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        normalizedEmail, Instant.now());

        if (match.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid or expired verification code. Please request a new code.");
        }

        GymRegistrationOtp otp = match.get();
        if (otp.getFailedAttempts() >= 5) {
            otp.consume();
            otpRepository.save(otp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many failed attempts. Please request a new verification code.");
        }

        if (!passwordEncoder.matches(rawCode.trim(), otp.getCodeHash())) {
            otp.recordFailedAttempt();
            otpRepository.save(otp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code.");
        }

        otp.consume();
        otpRepository.save(otp);
    }

    private void sendOtpEmail(String recipient, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, "Re-member Flow Platform", StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject("[Re-member Flow] Your Gym Registration Verification Code");

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="utf-8"></head>
                <body style="margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0f172a; color: #f8fafc;">
                  <div style="max-width: 560px; margin: 0 auto; background-color: #1e293b; border: 1px solid #334155; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5);">
                    <div style="background: linear-gradient(135deg, #0284c7 0%%, #0369a1 100%%); padding: 24px; text-align: center;">
                      <h1 style="margin: 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.5px;">Re-member Flow Platform</h1>
                      <p style="margin: 6px 0 0; color: #bae6fd; font-size: 13px;">Gym Workspace Verification</p>
                    </div>
                    <div style="padding: 28px;">
                      <p style="margin: 0 0 16px; font-size: 15px; color: #f1f5f9;">Hello,</p>
                      <p style="margin: 0 0 20px; font-size: 14px; line-height: 1.6; color: #cbd5e1;">
                        You are registering a new gym organization on Re-member Flow. Use the verification code below to validate your email address and complete registration:
                      </p>
                      <div style="background-color: #0f172a; border: 1px solid #0284c7; border-radius: 8px; padding: 20px; margin: 24px 0; text-align: center;">
                        <span style="font-family: 'Courier New', Courier, monospace; font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #38bdf8;">%s</span>
                      </div>
                      <p style="margin: 0 0 14px; font-size: 13px; line-height: 1.5; color: #94a3b8;">
                        This code will expire in <strong>10 minutes</strong>. Never share this code with anyone.
                      </p>
                      <p style="margin: 0; font-size: 12px; line-height: 1.5; color: #64748b;">
                        If you did not initiate this registration request, please disregard this email.
                      </p>
                    </div>
                    <div style="background-color: #0b1120; padding: 14px; text-align: center; border-top: 1px solid #1e293b; font-size: 11px; color: #64748b;">
                      &copy; Re-member Flow Multi-Location Platform. All rights reserved.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(code);

            helper.setText(html, true);
            mailSender.send(message);
            LOGGER.info("Registration OTP email dispatched to {}", recipient);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException ex) {
            LOGGER.warn("Failed to dispatch registration OTP email to {}: {}", recipient, ex.getMessage());
        }
    }
}

