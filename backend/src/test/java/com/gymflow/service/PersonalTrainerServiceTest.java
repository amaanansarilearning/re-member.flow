package com.gymflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymflow.domain.*;
import com.gymflow.repository.*;
import com.gymflow.web.ApiDtos.PtPayoutCreateRequest;
import com.gymflow.web.ApiDtos.PtPayoutDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PersonalTrainerServiceTest {

    private UserRepository users;
    private PersonalTrainerRepository pts;
    private PtAvailabilityRepository availabilities;
    private PtSessionRepository sessions;
    private PtEarningRepository earnings;
    private PtPayoutRepository payouts;
    private BranchRepository branches;
    private PasswordEncoder passwordEncoder;
    private ActionHistoryService actionHistory;
    private GymService gymService;
    private MembershipRepository memberships;
    private PersonalTrainerService service;

    private GymOrganization org;
    private UserAccount owner;
    private UserAccount ptUser;
    private PersonalTrainer pt;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        pts = mock(PersonalTrainerRepository.class);
        availabilities = mock(PtAvailabilityRepository.class);
        sessions = mock(PtSessionRepository.class);
        earnings = mock(PtEarningRepository.class);
        payouts = mock(PtPayoutRepository.class);
        branches = mock(BranchRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        actionHistory = mock(ActionHistoryService.class);
        gymService = mock(GymService.class);
        memberships = mock(MembershipRepository.class);

        service = new PersonalTrainerService(users, pts, availabilities, sessions, earnings,
                payouts, branches, passwordEncoder, actionHistory, gymService, memberships);

        org = new GymOrganization("Test Gym", "TEST");
        ReflectionTestUtils.setField(org, "id", 1L);

        owner = new UserAccount("Owner Alex", "owner@gym.test", "+919876543200", "hash", Role.OWNER, org, null);
        ReflectionTestUtils.setField(owner, "id", 2L);

        ptUser = new UserAccount("Coach Chris", "chris@gym.test", "+919876543210", "hash", Role.PERSONAL_TRAINER, org, null);
        ReflectionTestUtils.setField(ptUser, "id", 10L);

        pt = new PersonalTrainer(ptUser, org, null, "Strength & Conditioning", "Certified CSCS",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(70));
        ReflectionTestUtils.setField(pt, "id", 100L);
    }

    @Test
    void createPayout_bundlesUnpaidEarningsAndCalculatesTotal() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        when(gymService.account(owner.getEmail())).thenReturn(owner);
        when(pts.findByIdAndOrganizationId(pt.getId(), org.getId())).thenReturn(Optional.of(pt));

        UserAccount member = new UserAccount("Member", "m@gym.test", null, "h", Role.MEMBER, org, null);
        MembershipPlan plan = new MembershipPlan("Plan", null, BigDecimal.TEN, 1);
        Membership membership = new Membership(member, plan, from, to, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO);

        PtSession session1 = new PtSession(pt, membership, LocalDate.of(2026, 9, 5),
                LocalTime.of(9, 0), LocalTime.of(10, 0), null, BigDecimal.valueOf(70), BigDecimal.valueOf(1000));
        session1.complete("Done");

        PtSession session2 = new PtSession(pt, membership, LocalDate.of(2026, 9, 15),
                LocalTime.of(9, 0), LocalTime.of(10, 0), null, BigDecimal.valueOf(70), BigDecimal.valueOf(1000));
        session2.complete("Done");

        PtEarning earning1 = new PtEarning(session1, pt, BigDecimal.valueOf(1000),
                BigDecimal.valueOf(700), BigDecimal.valueOf(300), BigDecimal.valueOf(70));
        PtEarning earning2 = new PtEarning(session2, pt, BigDecimal.valueOf(1000),
                BigDecimal.valueOf(700), BigDecimal.valueOf(300), BigDecimal.valueOf(70));

        when(earnings.findByPersonalTrainerIdAndPayoutIsNullOrderByCreatedAtDesc(pt.getId()))
                .thenReturn(List.of(earning1, earning2));
        when(payouts.save(any(PtPayout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PtPayoutCreateRequest req = new PtPayoutCreateRequest(from, to, "September payout batch");
        PtPayoutDto result = service.createPayout(owner.getEmail(), pt.getId(), req);

        // 700 + 700 = 1400 total
        assertThat(result.totalEarnings()).isEqualByComparingTo(BigDecimal.valueOf(1400));
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(earning1.getPayout()).isNotNull();
        assertThat(earning2.getPayout()).isNotNull();
    }

    @Test
    void markPaid_updatesStatusAndPaidByName() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        PtPayout payout = new PtPayout(pt, from, to, BigDecimal.valueOf(1400), "September batch");
        ReflectionTestUtils.setField(payout, "id", 55L);

        when(gymService.account(owner.getEmail())).thenReturn(owner);
        when(payouts.findByIdAndPersonalTrainerOrganizationId(payout.getId(), org.getId()))
                .thenReturn(Optional.of(payout));

        PtPayoutDto result = service.markPaid(owner.getEmail(), payout.getId());

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.paidByName()).isEqualTo(owner.getFullName());
        assertThat(payout.getPaidAt()).isNotNull();
    }

    @Test
    void markPaid_throwsIfAlreadyPaid() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        PtPayout payout = new PtPayout(pt, from, to, BigDecimal.valueOf(1400), "September batch");
        payout.markPaid("Someone");
        ReflectionTestUtils.setField(payout, "id", 55L);

        when(gymService.account(owner.getEmail())).thenReturn(owner);
        when(payouts.findByIdAndPersonalTrainerOrganizationId(payout.getId(), org.getId()))
                .thenReturn(Optional.of(payout));

        assertThatThrownBy(() -> service.markPaid(owner.getEmail(), payout.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already marked as paid");
    }
}

