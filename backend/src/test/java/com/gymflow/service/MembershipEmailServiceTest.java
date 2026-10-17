package com.gymflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class MembershipEmailServiceTest {
    @Mock
    private JavaMailSender mailSender;

    @Test
    void emailsMemberAndOwnerWithTheUploadedReceipt() throws Exception {
        MimeMessage memberMessage = newMessage();
        MimeMessage ownerMessage = newMessage();
        when(mailSender.createMimeMessage()).thenReturn(memberMessage, ownerMessage);
        MembershipEmailService service = new MembershipEmailService(mailSender, "receipts@gym.test");

        service.notifyMemberEnrolled("owner@gym.test", "member@gym.test", "New Member", "Main branch",
                "Monthly", new BigDecimal("49.00"), LocalDate.parse("2026-10-02"),
                LocalDate.parse("2026-11-01"), new MembershipEmailService.ReceiptAttachment(
                        new byte[] { 1, 2, 3 }, "image/png", "membership-receipt.png"));

        verify(mailSender).send(memberMessage);
        verify(mailSender).send(ownerMessage);
        assertThat(memberMessage.getAllRecipients()[0].toString()).isEqualTo("member@gym.test");
        assertThat(ownerMessage.getAllRecipients()[0].toString()).isEqualTo("owner@gym.test");
        assertReceiptAttachment(memberMessage);
        assertReceiptAttachment(ownerMessage);
    }

    @Test
    void emailsTheOwnerWhenStaffIsAdded() throws Exception {
        MimeMessage message = newMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);
        MembershipEmailService service = new MembershipEmailService(mailSender, "receipts@gym.test");

        service.notifyStaffAdded("owner@gym.test", "New Staff", "staff@gym.test", "Main branch");

        verify(mailSender).send(message);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("owner@gym.test");
        assertThat(message.getSubject()).contains("New Staff");
    }

    private MimeMessage newMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private void assertReceiptAttachment(MimeMessage message) throws Exception {
        assertThat(message.getContent()).isInstanceOf(MimeMultipart.class);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("membership-receipt.png");
    }
}