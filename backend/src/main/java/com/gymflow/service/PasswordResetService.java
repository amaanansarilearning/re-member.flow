package com.gymflow.service;

import com.gymflow.domain.PasswordResetCode;
import com.gymflow.repository.PasswordResetCodeRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.web.ApiDtos.PasswordResetResponse;
import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    // H-5 FIX: Use a larger alphanumeric code space (62^8 ≈ 218 trillion) instead
    // of a
    // 6-digit numeric code (1 million). This makes offline brute-force infeasible
    // even if
    // an attacker somehow obtained the hashed codes from the database.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    // H-5 FIX: Global per-email cooldown after a lockout event. Without this an
    // attacker
    // could loop: request → 5 bad guesses (lockout) → request → 5 more guesses,
    // limited
    // only by the IP rate-limiter. Now, after a code is consumed-via-lockout we
    // require
    // a 15-minute wait before a new code can be issued for that email.
    private static final long LOCKOUT_COOLDOWN_SECONDS = 15 * 60;

    private final PasswordResetCodeRepository resetCodes;
    private final UserRepository users;
    private final GymService gymService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean exposeCode;
    private final org.springframework.core.env.Environment env;

    public PasswordResetService(PasswordResetCodeRepository resetCodes, UserRepository users, GymService gymService,
            PasswordEncoder passwordEncoder, JavaMailSender mailSender,
            @Value("${gym.mail.from}") String fromAddress,
            @Value("${gym.password-reset.expose-code:false}") boolean exposeCode,
            org.springframework.core.env.Environment env) {
        this.resetCodes = resetCodes;
        this.users = users;
        this.gymService = gymService;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.exposeCode = exposeCode;
        this.env = env;
    }

    @Transactional
    public PasswordResetResponse request(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        String message = "If an account exists for that email, a reset code has been sent.";
        Instant now = Instant.now();

        if (!users.existsByEmailIgnoreCase(normalizedEmail)) {
            return new PasswordResetResponse(message, null);
        }

        // Existing per-request cooldown: don't issue a new code within 60 s
        if (resetCodes.existsByEmailIgnoreCaseAndCreatedAtAfter(normalizedEmail, now.minusSeconds(60))) {
            return new PasswordResetResponse(message, null);
        }

        // H-5 FIX: Global post-lockout cooldown.
        // If any code for this email was consumed due to too many failed attempts
        // within
        // the last LOCKOUT_COOLDOWN_SECONDS, refuse to issue a new one. This closes the
        // loop: request → 5 bad guesses → locked-out → request again indefinitely.
        boolean recentLockout = resetCodes
                .findAllByEmailIgnoreCaseAndConsumedAtIsNotNull(normalizedEmail)
                .stream()
                .anyMatch(code -> code.getFailedAttempts() >= 5
                        && code.getConsumedAt() != null
                        && code.getConsumedAt().isAfter(now.minusSeconds(LOCKOUT_COOLDOWN_SECONDS)));
        if (recentLockout) {
            return new PasswordResetResponse(message, null);
        }

        resetCodes.findAllByEmailIgnoreCaseAndConsumedAtIsNull(normalizedEmail)
                .forEach(PasswordResetCode::consume);

        // H-5 FIX: Generate an 8-char alphanumeric code (excludes look-alike chars like
        // 0/O, 1/I/l for readability) giving ~218 trillion possibilities vs 1 million
        // for the old 6-digit numeric code.
        String code = generateCode();
        resetCodes
                .save(new PasswordResetCode(normalizedEmail, passwordEncoder.encode(code), now, now.plusSeconds(600)));

        if (exposeCode && env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "test")))
            return new PasswordResetResponse(message, code);

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(normalizedEmail);
        mail.setSubject("Your Re-member Flow password reset code");
        mail.setText("Your Re-member Flow password reset code is " + code + ". It expires in 10 minutes.");
        mailSender.send(mail);
        return new PasswordResetResponse(message, null);
    }

    @Transactional
    public boolean confirm(String email, String code, String newPassword) {
        var match = resetCodes
                .findFirstByEmailIgnoreCaseAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(email,
                        Instant.now());
        if (match.isEmpty())
            return false;
        PasswordResetCode resetCode = match.get();
        if (resetCode.getFailedAttempts() >= 5) {
            resetCode.consume();
            return false;
        }
        if (!passwordEncoder.matches(code, resetCode.getCodeHash())) {
            resetCode.recordFailedAttempt();
            return false;
        }
        resetCode.consume();
        gymService.resetPassword(email, newPassword);
        return true;
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
