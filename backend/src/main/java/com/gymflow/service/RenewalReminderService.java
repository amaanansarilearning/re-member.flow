package com.gymflow.service;

import com.gymflow.domain.Membership;
import com.gymflow.domain.MembershipStatus;
import com.gymflow.domain.RenewalReminder;
import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.RenewalReminderRepository;
import com.gymflow.repository.UserRepository;
import com.gymflow.web.ApiDtos.RenewalReminderDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenewalReminderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenewalReminderService.class);
    private static final List<Integer> REMINDER_DAYS = List.of(7, 3, 1);
    private final MembershipRepository memberships;
    private final RenewalReminderRepository reminders;
    private final UserRepository users;
    private final MembershipEmailService emailService;
    private final ZoneId businessZone;

    public RenewalReminderService(MembershipRepository memberships, RenewalReminderRepository reminders,
            UserRepository users, MembershipEmailService emailService,
            @Value("${gym.business-zone:Asia/Kolkata}") String businessZone) {
        this.memberships = memberships;
        this.reminders = reminders;
        this.users = users;
        this.emailService = emailService;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Scheduled(cron = "${gym.reminders.cron:0 */15 * * * *}", zone = "${gym.business-zone:Asia/Kolkata}")
    @Transactional
    public void processDueReminders() {
        LocalDate today = LocalDate.now(businessZone);
        for (int days : REMINDER_DAYS) {
            memberships.findByEndDateAndStatusAndRenewalStartDateIsNull(
                    today.plusDays(days), MembershipStatus.ACTIVE)
                    .forEach(membership -> createReminders(membership, days));
        }
        sendDueRetries();
    }

    @Transactional(readOnly = true)
    public List<RenewalReminderDto> list(Long organizationId) {
        return reminders.findTop500ByMembershipMemberOrganizationIdOrderByNextAttemptAtDesc(organizationId).stream()
                .map(reminder -> new RenewalReminderDto(reminder.getId(), reminder.getMembership().getId(),
                        reminder.getMembership().getMember().getFullName(), reminder.getRecipientEmail(),
                        reminder.getDaysBefore(), reminder.getStatus(), reminder.getAttempts(),
                        reminder.getLastAttemptAt(), reminder.getNextAttemptAt(), reminder.getSentAt(),
                        reminder.getLastError()))
                .toList();
    }

    private void createReminders(Membership membership, int daysBefore) {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(membership.getMember().getEmail());
        users.findByOrganizationIdAndRoleOrderByFullNameAsc(
                membership.getMember().getOrganization().getId(), Role.OWNER)
                .stream().map(UserAccount::getEmail).forEach(recipients::add);
        if (membership.getBranch() != null) {
            users.findByOrganizationIdAndBranchIdAndRoleOrderByFullNameAsc(
                    membership.getMember().getOrganization().getId(), membership.getBranch().getId(), Role.STAFF)
                    .stream().map(UserAccount::getEmail).forEach(recipients::add);
        }
        for (String recipient : recipients) {
            if (!reminders.existsForRecipient(
                    membership.getId(), daysBefore, recipient)) {
                reminders.save(new RenewalReminder(membership, daysBefore, recipient));
            }
        }
    }

    private void sendDueRetries() {
        Instant now = Instant.now().plusSeconds(5);
        List<RenewalReminder> due = reminders.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                List.of("PENDING", "FAILED"), now);
        for (RenewalReminder reminder : due) {
            Membership membership = reminder.getMembership();
            long daysUntilExpiry = ChronoUnit.DAYS.between(
                    LocalDate.now(businessZone), membership.getEndDate());
            if (membership.getStatus() != MembershipStatus.ACTIVE || membership.getRenewalStartDate() != null
                    || daysUntilExpiry != reminder.getDaysBefore()) {
                reminder.suppress();
                continue;
            }
            boolean sent = emailService.sendRenewalReminder(reminder.getRecipientEmail(),
                    membership.getMember().getFullName(), membership.getPlanNameSnapshot(), membership.getEndDate(),
                    reminder.getDaysBefore());
            if (sent) {
                reminder.markSent(Instant.now());
            } else {
                int retryMinutes = Math.min(24 * 60, 15 * (1 << Math.min(reminder.getAttempts(), 7)));
                reminder.markFailed(Instant.now(), Instant.now().plusSeconds(retryMinutes * 60L),
                        "Email delivery failed; it will be retried.");
                LOGGER.warn("Renewal reminder {} failed for {}", reminder.getId(), reminder.getRecipientEmail());
            }
        }
    }
}
