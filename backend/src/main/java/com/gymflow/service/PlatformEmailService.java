package com.gymflow.service;

import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.UpgradeRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PlatformEmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String billingEmail;
    private final String adminEmail;
    private final String appUrl;

    public PlatformEmailService(
            JavaMailSender mailSender,
            @Value("${gym.mail.from:noreply@rememberflow.com}") String fromAddress,
            @Value("${gym.platform.billing-email:billing@rememberflow.com}") String billingEmail,
            @Value("${gym.platform.admin-email:admin@rememberflow.com}") String adminEmail,
            @Value("${gym.app.url:http://localhost:4200}") String appUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.billingEmail = billingEmail;
        this.adminEmail = adminEmail;
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    public void sendUpgradeRequestConfirmationToOwner(UpgradeRequest request) {
        String orgName = request.getOrganization() != null ? request.getOrganization().getName() : "Your Gym";
        String subject = "[Re-member Flow] Upgrade Request Received: " + request.getBillingPlanName();
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0f172a; color: #f8fafc;">
              <div style="max-width: 600px; margin: 0 auto; background-color: #1e293b; border: 1px solid #334155; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5);">
                <div style="background: linear-gradient(135deg, #0284c7 0%%, #0369a1 100%%); padding: 24px; text-align: center;">
                  <h1 style="margin: 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.5px;">Re-member Flow Platform</h1>
                  <p style="margin: 6px 0 0; color: #bae6fd; font-size: 13px;">Enterprise Subscription &amp; Licensing Desk</p>
                </div>
                <div style="padding: 28px;">
                  <p style="margin: 0 0 16px; font-size: 15px; color: #f1f5f9;">Hello <strong>%s</strong>,</p>
                  <p style="margin: 0 0 20px; font-size: 14px; line-height: 1.6; color: #cbd5e1;">
                    We have successfully received your PRO license upgrade request for <strong>%s</strong>. Our platform billing team has begun review.
                  </p>
                  <div style="background-color: #0f172a; border: 1px solid #334155; border-radius: 8px; padding: 18px; margin-bottom: 22px;">
                    <table style="width: 100%%; font-size: 13px; color: #cbd5e1; border-collapse: collapse;">
                      <tr><td style="padding: 6px 0; color: #94a3b8;">Request Reference:</td><td style="padding: 6px 0; font-weight: 600; color: #f8fafc; text-align: right;">REQ-%d</td></tr>
                      <tr><td style="padding: 6px 0; color: #94a3b8;">Billing Plan:</td><td style="padding: 6px 0; font-weight: 600; color: #38bdf8; text-align: right;">%s</td></tr>
                      <tr><td style="padding: 6px 0; color: #94a3b8;">Duration:</td><td style="padding: 6px 0; font-weight: 600; color: #f8fafc; text-align: right;">%d Days</td></tr>
                      <tr><td style="padding: 6px 0; color: #94a3b8;">Payment Mode:</td><td style="padding: 6px 0; font-weight: 600; color: #f8fafc; text-align: right;">%s</td></tr>
                    </table>
                  </div>
                  <div style="background-color: rgba(14, 165, 233, 0.1); border-left: 4px solid #0284c7; padding: 14px; border-radius: 4px; margin-bottom: 22px;">
                    <p style="margin: 0 0 6px; font-weight: 600; font-size: 13px; color: #38bdf8;">Payment &amp; Activation Instructions:</p>
                    <p style="margin: 0; font-size: 12.5px; line-height: 1.5; color: #94a3b8;">
                      If paying via Bank Wire / NEFT or Corporate UPI, please ensure your transfer note contains <strong>REQ-%d</strong>. Once verified, your cryptographically signed activation key will be delivered directly to this email address.
                    </p>
                  </div>
                  <p style="margin: 0; font-size: 13px; color: #64748b; line-height: 1.5;">
                    Questions? Reach our enterprise concierge at <a href="mailto:billing@rememberflow.com" style="color: #38bdf8; text-decoration: none;">billing@rememberflow.com</a>.
                  </p>
                </div>
                <div style="background-color: #0b1120; padding: 14px; text-align: center; border-top: 1px solid #1e293b; font-size: 11px; color: #64748b;">
                  &copy; Re-member Flow Multi-Location Platform. All rights reserved.
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                request.getRequesterName() != null ? request.getRequesterName() : "Gym Owner",
                orgName,
                request.getId() != null ? request.getId() : 0L,
                request.getBillingPlanName(),
                request.getDurationDays(),
                request.getPaymentMethod(),
                request.getId() != null ? request.getId() : 0L
        );

        afterCommit(() -> sendPlatformEmail(request.getRequesterEmail(), subject, html));
    }

    public void sendUpgradeRequestAlertToAdmin(UpgradeRequest request) {
        String orgName = request.getOrganization() != null ? request.getOrganization().getName() : "Unknown";
        String orgCode = request.getOrganization() != null ? request.getOrganization().getCode() : "N/A";
        String subject = "[Action Required] New Upgrade Request: " + orgName + " (" + request.getBillingPlanName() + ")";
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #090d16; color: #f8fafc;">
              <div style="max-width: 600px; margin: 0 auto; background-color: #1e293b; border: 1px solid #334155; border-radius: 10px; padding: 24px;">
                <h2 style="margin: 0 0 16px; color: #f59e0b; font-size: 18px;">Pending Tenant License Request</h2>
                <p style="margin: 0 0 16px; font-size: 14px; color: #cbd5e1;">A gym organization has requested an upgrade to PRO:</p>
                <div style="background-color: #0f172a; border: 1px solid #334155; border-radius: 6px; padding: 16px; margin-bottom: 20px; font-size: 13px; color: #cbd5e1; line-height: 1.6;">
                  <div><strong>Organization:</strong> %s (<code>%s</code>)</div>
                  <div><strong>Requester:</strong> %s (%s)</div>
                  <div><strong>Phone:</strong> %s</div>
                  <div><strong>Plan:</strong> <span style="color: #38bdf8;">%s</span> (%d Days)</div>
                  <div><strong>Payment Mode:</strong> %s</div>
                  <div><strong>GSTIN:</strong> %s</div>
                  <div><strong>Notes:</strong> %s</div>
                </div>
                <p style="margin: 0; font-size: 13px; color: #94a3b8;">
                  Log in to the Platform Operations Console to verify payment and issue a cryptographic license key or approve direct upgrade.
                </p>
              </div>
            </body>
            </html>
            """.formatted(
                orgName,
                orgCode,
                request.getRequesterName() != null ? request.getRequesterName() : "Owner",
                request.getRequesterEmail(),
                request.getRequesterPhone() != null ? request.getRequesterPhone() : "N/A",
                request.getBillingPlanName(),
                request.getDurationDays(),
                request.getPaymentMethod(),
                request.getGstNumber() != null ? request.getGstNumber() : "None",
                request.getNotes() != null ? request.getNotes() : "None"
        );

        afterCommit(() -> sendPlatformEmail(this.adminEmail, subject, html));
    }

    public void sendActivationKeyToOwner(UpgradeRequest request, String activationCode, Integer durationDays) {
        String orgName = request.getOrganization() != null ? request.getOrganization().getName() : "Your Gym";
        String deepLink = appUrl + "/#redeem?code=" + activationCode;
        String subject = "[Re-member Flow] Your PRO Activation Key is Ready: " + activationCode;
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; color: #f8fafc;">
              <div style="max-width: 600px; margin: 0 auto; background-color: #1e293b; border: 1px solid #334155; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5);">
                <div style="background: linear-gradient(135deg, #10b981 0%%, #059669 100%%); padding: 24px; text-align: center;">
                  <h1 style="margin: 0; color: #ffffff; font-size: 22px; font-weight: 700;">Re-member Flow PRO License Activated</h1>
                  <p style="margin: 6px 0 0; color: #d1fae5; font-size: 13px;">Official Commercial Activation Voucher</p>
                </div>
                <div style="padding: 28px;">
                  <p style="margin: 0 0 16px; font-size: 15px; color: #f1f5f9;">Hello <strong>%s</strong>,</p>
                  <p style="margin: 0 0 20px; font-size: 14px; line-height: 1.6; color: #cbd5e1;">
                    Your payment for <strong>%s</strong> has been verified. Below is your official, single-use activation voucher key to unlock <strong>Re-member Flow PRO</strong> for <strong>%d days</strong>.
                  </p>
                  
                  <div style="background-color: #0f172a; border: 2px dashed #10b981; border-radius: 10px; padding: 22px; text-align: center; margin-bottom: 24px;">
                    <div style="font-size: 12px; text-transform: uppercase; tracking: 0.1em; color: #34d399; font-weight: 700; margin-bottom: 6px;">Your Cryptographic Activation Key</div>
                    <div style="font-size: 24px; font-family: 'Consolas', 'Monaco', monospace; font-weight: 700; color: #ffffff; letter-spacing: 2px;">%s</div>
                  </div>

                  <div style="text-align: center; margin-bottom: 24px;">
                    <a href="%s" style="background-color: #0284c7; color: #ffffff; padding: 12px 28px; border-radius: 6px; font-weight: 600; text-decoration: none; font-size: 14px; display: inline-block;">
                      Activate Re-member Flow PRO Now &rarr;
                    </a>
                  </div>

                  <p style="margin: 0 0 10px; font-size: 13px; color: #94a3b8; line-height: 1.6;">
                    Alternatively, log into your Re-member Flow dashboard, open <strong>Upgrade to PRO &rarr; I Have an Activation Code</strong>, and paste your voucher key.
                  </p>
                </div>
                <div style="background-color: #0b1120; padding: 14px; text-align: center; border-top: 1px solid #1e293b; font-size: 11px; color: #64748b;">
                  &copy; Re-member Flow Multi-Location Platform. Licensed to %s.
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                request.getRequesterName() != null ? request.getRequesterName() : "Gym Owner",
                orgName,
                durationDays != null ? durationDays : 365,
                activationCode,
                deepLink,
                orgName
        );

        afterCommit(() -> sendPlatformEmail(request.getRequesterEmail(), subject, html));
    }

    public void sendDirectUpgradeNoticeToOwner(GymOrganization org, String ownerEmail, int durationDays) {
        String orgName = org != null ? org.getName() : "Your Gym";
        String subject = "[Re-member Flow] Your Gym is Now Upgraded to PRO!";
        String html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"></head>
            <body style="margin: 0; padding: 24px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; color: #f8fafc;">
              <div style="max-width: 600px; margin: 0 auto; background-color: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 24px;">
                <h2 style="margin: 0 0 14px; color: #10b981; font-size: 20px;">Re-member Flow PRO Activated</h2>
                <p style="margin: 0 0 16px; font-size: 14px; line-height: 1.6; color: #cbd5e1;">
                  Great news! Your gym organization <strong>%s</strong> has been directly upgraded to <strong>Re-member Flow PREMIUM (PRO)</strong> for <strong>%d days</strong> by the platform administration team.
                </p>
                <div style="background-color: #0f172a; border-left: 4px solid #10b981; padding: 14px; border-radius: 4px; margin-bottom: 20px;">
                  <p style="margin: 0; font-size: 13px; color: #94a3b8;">
                    Personal Trainer management, workout sessions, and extended history logs are now unlocked across all your branches immediately.
                  </p>
                </div>
                <p style="margin: 0; font-size: 12px; color: #64748b;">
                  Thank you for partnering with Re-member Flow.
                </p>
              </div>
            </body>
            </html>
            """.formatted(orgName, durationDays);

        afterCommit(() -> sendPlatformEmail(ownerEmail, subject, html));
    }

    public boolean sendTenantMemberEmail(String tenantName, String ownerEmail, String recipient, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String personal = tenantName != null && !tenantName.isBlank() ? tenantName + " via Re-member Flow" : "Re-member Flow Notifications";
            helper.setFrom(new InternetAddress(fromAddress, personal, StandardCharsets.UTF_8.name()));
            if (ownerEmail != null && !ownerEmail.isBlank()) {
                helper.setReplyTo(new InternetAddress(ownerEmail.trim(), personal, StandardCharsets.UTF_8.name()));
            }
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            LOGGER.info("Tenant-branded email sent to {} on behalf of '{}' (Reply-To: {})", recipient, tenantName, ownerEmail);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            LOGGER.warn("Could not dispatch tenant-branded email to {} for {}: {}", recipient, tenantName, exception.getMessage());
            return false;
        }
    }

    private boolean sendPlatformEmail(String recipient, String subject, String htmlContent) {
        if (recipient == null || recipient.isBlank()) {
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, "Re-member Flow Platform", StandardCharsets.UTF_8.name()));
            if (billingEmail != null && !billingEmail.isBlank()) {
                helper.setReplyTo(new InternetAddress(billingEmail, "Re-member Flow Billing", StandardCharsets.UTF_8.name()));
            }
            helper.setTo(recipient.trim().toLowerCase());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            LOGGER.info("Platform email sent to {} with subject '{}'", recipient, subject);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            LOGGER.warn("Could not dispatch platform email to {} ('{}'): {}", recipient, subject, exception.getMessage());
            return false;
        }
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
}
