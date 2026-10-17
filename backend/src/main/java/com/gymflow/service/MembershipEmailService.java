package com.gymflow.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MembershipEmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MembershipEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MembershipEmailService(JavaMailSender mailSender, @Value("${gym.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void notifyMemberEnrolled(String ownerEmail, String memberEmail, String memberName, String branchName,
            String planName, BigDecimal amountPaid, LocalDate startDate, LocalDate endDate,
            ReceiptAttachment receipt) {
        String memberBody = "Hello " + memberName + ",\n\nYour membership at " + branchName + " is confirmed.\n"
                + "Package: " + planName + "\nAmount paid: " + amountPaid + "\n"
                + "Membership dates: " + startDate + " to " + endDate + ".\n\nWelcome to the gym.";
        String ownerBody = "A new member has been enrolled.\n\nMember: " + memberName + " (" + memberEmail + ")\n"
                + "Branch: " + branchName + "\nPackage: " + planName + "\nAmount paid: " + amountPaid + "\n"
                + "Membership dates: " + startDate + " to " + endDate + ".";
        afterCommit(() -> {
            send(memberEmail, "Your gym membership is confirmed", memberBody, receipt);
            send(ownerEmail, "New member enrolled: " + memberName, ownerBody, receipt);
        });
    }

    public void notifyStaffAdded(String ownerEmail, String staffName, String staffEmail, String branchName) {
        String body = "A staff account has been added.\n\nStaff member: " + staffName + " (" + staffEmail + ")\n"
                + "Assigned branch: " + branchName + ".";
        afterCommit(() -> send(ownerEmail, "New staff account added: " + staffName, body, null));
    }

    public boolean sendRenewalReminder(String recipient, String memberName, String planName, LocalDate endDate,
            int daysBefore) {
        String timing = daysBefore == 1 ? "tomorrow" : "in " + daysBefore + " days";
        String body = "Hello " + memberName + ",\n\nYour " + planName + " membership is due to expire "
                + timing + " (" + endDate + "). Please contact your gym to discuss renewal.\n";
        return send(recipient, "Membership renewal reminder", body, null);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private boolean send(String recipient, String subject, String body, ReceiptAttachment receipt) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, receipt != null, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (receipt != null) {
                helper.addAttachment(receipt.filename(), new ByteArrayResource(receipt.content()),
                        receipt.contentType());
            }
            mailSender.send(message);
            return true;
        } catch (MessagingException | RuntimeException exception) {
            LOGGER.warn("Could not send enrollment notification to {}", recipient, exception);
            return false;
        }
    }

    public record ReceiptAttachment(byte[] content, String contentType, String filename) {
    }
}