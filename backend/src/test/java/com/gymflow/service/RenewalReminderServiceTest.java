package com.gymflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymflow.domain.Membership;
import com.gymflow.domain.MembershipPlan;
import com.gymflow.domain.RenewalReminder;
import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.RenewalReminderRepository;
import com.gymflow.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenewalReminderServiceTest {
    @Test
    void retryDelayCapsAtTwentyFourHours() {
        MembershipRepository memberships = mock(MembershipRepository.class);
        RenewalReminderRepository reminders = mock(RenewalReminderRepository.class);
        MembershipEmailService emailService = mock(MembershipEmailService.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        UserAccount member = new UserAccount("Member", "member@example.test", null, "hash", Role.MEMBER, null, null);
        MembershipPlan plan = new MembershipPlan("Monthly", null, BigDecimal.TEN, 1);
        Membership membership = new Membership(member, plan, today, today.plusDays(7),
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);
        RenewalReminder reminder = new RenewalReminder(membership, 7, "member@example.test");
        when(reminders.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                anyList(), any(Instant.class))).thenReturn(List.of(reminder));
        when(emailService.sendRenewalReminder(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);
        RenewalReminderService service = new RenewalReminderService(memberships, reminders,
                mock(UserRepository.class), emailService, "Asia/Kolkata");

        Instant beforeRetries = Instant.now();
        for (int attempt = 0; attempt < 8; attempt++) {
            service.processDueReminders();
        }

        Instant afterRetries = Instant.now();
        assertThat(reminder.getAttempts()).isEqualTo(8);
        assertThat(reminder.getNextAttemptAt()).isBetween(
                beforeRetries.plusSeconds(24 * 60 * 60L), afterRetries.plusSeconds(24 * 60 * 60L));
        verify(emailService, times(8)).sendRenewalReminder(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt());
    }
}
