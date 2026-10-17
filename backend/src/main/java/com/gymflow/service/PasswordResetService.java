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
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PasswordResetCodeRepository resetCodes;
    private final UserRepository users;
    private final GymService gymService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean exposeCode;

    public PasswordResetService(PasswordResetCodeRepository resetCodes, UserRepository users, GymService gymService,
            PasswordEncoder passwordEncoder, JavaMailSender mailSender,
            @Value("${gym.mail.from}") String fromAddress,
            @Value("${gym.password-reset.expose-code:false}") boolean exposeCode) {
        this.resetCodes = resetCodes;
        this.users = users;
        this.gymService = gymService;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.exposeCode = exposeCode;
    }

    @Transactional
    public PasswordResetResponse request(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        String message = "If an account exists for that email, a reset code has been sent.";
        Instant now = Instant.now();
        if (!users.existsByEmailIgnoreCase(normalizedEmail)
                || resetCodes.existsByEmailIgnoreCaseAndCreatedAtAfter(normalizedEmail, now.minusSeconds(60))) {
            return new PasswordResetResponse(message, null);
        }
        resetCodes.findAllByEmailIgnoreCaseAndConsumedAtIsNull(normalizedEmail)
                .forEach(PasswordResetCode::consume);
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        resetCodes
                .save(new PasswordResetCode(normalizedEmail, passwordEncoder.encode(code), now, now.plusSeconds(600)));
        if (exposeCode)
            return new PasswordResetResponse(message, code);
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(normalizedEmail);
        mail.setSubject("Your FORM / Club password reset code");
        mail.setText("Your password reset code is " + code + ". It expires in 10 minutes.");
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
}