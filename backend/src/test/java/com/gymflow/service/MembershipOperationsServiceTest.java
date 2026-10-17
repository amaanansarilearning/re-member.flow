package com.gymflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymflow.domain.GymOrganization;
import com.gymflow.domain.Membership;
import com.gymflow.domain.MembershipPlan;
import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import com.gymflow.repository.BranchRepository;
import com.gymflow.repository.MembershipFreezeRepository;
import com.gymflow.repository.MembershipRepository;
import com.gymflow.repository.PlanRepository;
import com.gymflow.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MembershipOperationsServiceTest {
    @Test
    void accessCheckUsesReadOnlyMembershipLookup() {
        UserRepository users = mock(UserRepository.class);
        MembershipRepository memberships = mock(MembershipRepository.class);
        MembershipFreezeRepository freezes = mock(MembershipFreezeRepository.class);
        GymOrganization organization = mock(GymOrganization.class);
        when(organization.getId()).thenReturn(1L);
        UserAccount owner = new UserAccount("Owner", "owner@example.test", null, "hash",
                Role.OWNER, organization, null);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Membership membership = new Membership(
                new UserAccount("Member", "member@example.test", null, "hash", Role.MEMBER, organization, null),
                new MembershipPlan("Monthly", null, BigDecimal.TEN, 1),
                today, today, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);
        when(users.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(memberships.findById(1L)).thenReturn(Optional.of(membership));
        when(freezes.existsActiveOnDate(1L, today))
                .thenReturn(false);
        MembershipOperationsService service = new MembershipOperationsService(users, memberships, freezes,
                mock(PlanRepository.class), mock(BranchRepository.class), mock(ActionHistoryService.class),
                mock(GymService.class), "Asia/Kolkata");

        assertThat(service.access(owner.getEmail(), Role.OWNER, 1L).allowed()).isTrue();

        verify(memberships).findById(1L);
        verify(memberships, never()).findByIdForUpdate(1L);
    }
}
