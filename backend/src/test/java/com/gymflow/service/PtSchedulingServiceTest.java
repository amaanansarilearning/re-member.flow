package com.gymflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymflow.domain.*;
import com.gymflow.repository.*;
import com.gymflow.web.ApiDtos.PtSessionBookRequest;
import com.gymflow.web.ApiDtos.PtSessionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class PtSchedulingServiceTest {

    private PersonalTrainerRepository pts;
    private PtSessionRepository sessions;
    private PtEarningRepository earnings;
    private MembershipRepository memberships;
    private UserRepository users;
    private ActionHistoryService actionHistory;
    private GymService gymService;
    private PtSchedulingService service;

    private GymOrganization org;
    private UserAccount ptUser;
    private PersonalTrainer pt;
    private UserAccount memberUser;
    private MembershipPlan plan;
    private Membership membership;

    @BeforeEach
    void setUp() {
        pts = mock(PersonalTrainerRepository.class);
        sessions = mock(PtSessionRepository.class);
        earnings = mock(PtEarningRepository.class);
        memberships = mock(MembershipRepository.class);
        users = mock(UserRepository.class);
        actionHistory = mock(ActionHistoryService.class);
        gymService = mock(GymService.class);

        service = new PtSchedulingService(pts, sessions, earnings, memberships, users, actionHistory, gymService);

        org = new GymOrganization("Test Gym", "TEST");
        ReflectionTestUtils.setField(org, "id", 1L);

        ptUser = new UserAccount("Coach Chris", "chris@gym.test", "+919876543210", "hash", Role.PERSONAL_TRAINER, org, null);
        ReflectionTestUtils.setField(ptUser, "id", 10L);

        pt = new PersonalTrainer(ptUser, org, null, "Strength & Conditioning", "Certified CSCS",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(70));
        ReflectionTestUtils.setField(pt, "id", 100L);

        memberUser = new UserAccount("John Doe", "john@member.test", "+919876543211", "hash", Role.MEMBER, org, null);
        ReflectionTestUtils.setField(memberUser, "id", 20L);

        plan = new MembershipPlan("PT Premium", "With 10 PT Sessions", BigDecimal.valueOf(10000), 1);
        plan.updatePt(PtPackageType.COMBINED, 10, BigDecimal.valueOf(7000));
        ReflectionTestUtils.setField(plan, "id", 5L);

        LocalDate today = LocalDate.now();
        membership = new Membership(memberUser, plan, today, today.plusMonths(1),
                BigDecimal.valueOf(10000), BigDecimal.valueOf(10000), BigDecimal.ZERO);
        ReflectionTestUtils.setField(membership, "id", 50L);
        membership.assignPersonalTrainer(pt, 10, BigDecimal.valueOf(70));
    }

    @Test
    void bookSession_preventsConflictingTimeSlot() {
        LocalDate sessionDate = LocalDate.now().plusDays(2);
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(11, 0);

        when(gymService.account(ptUser.getEmail())).thenReturn(ptUser);
        when(pts.findByUserAccountId(ptUser.getId())).thenReturn(Optional.of(pt));
        when(memberships.findById(membership.getId())).thenReturn(Optional.of(membership));
        when(sessions.hasConflict(eq(pt.getId()), eq(sessionDate), eq(start), eq(end), eq(null)))
                .thenReturn(true);

        PtSessionBookRequest req = new PtSessionBookRequest(membership.getId(), sessionDate, start, end, "Leg day");

        assertThatThrownBy(() -> service.bookSession(ptUser.getEmail(), Role.PERSONAL_TRAINER, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("conflicting session");
    }

    @Test
    void bookSession_preventsBookingWhenSessionsExhausted() {
        LocalDate sessionDate = LocalDate.now().plusDays(2);
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(11, 0);

        // Exhaust all 10 sessions
        for (int i = 0; i < 10; i++) {
            membership.incrementPtSessionsUsed();
        }

        when(gymService.account(ptUser.getEmail())).thenReturn(ptUser);
        when(pts.findByUserAccountId(ptUser.getId())).thenReturn(Optional.of(pt));
        when(memberships.findById(membership.getId())).thenReturn(Optional.of(membership));

        PtSessionBookRequest req = new PtSessionBookRequest(membership.getId(), sessionDate, start, end, "Should fail");

        assertThatThrownBy(() -> service.bookSession(ptUser.getEmail(), Role.PERSONAL_TRAINER, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No remaining PT sessions");
    }

    @Test
    void completeSession_calculatesRevenueSplitAndCreatesImmutableEarning() {
        LocalDate sessionDate = LocalDate.now().minusDays(1);
        LocalTime start = LocalTime.of(14, 0);
        LocalTime end = LocalTime.of(15, 0);

        PtSession session = new PtSession(pt, membership, sessionDate, start, end, "Chest & Tris",
                BigDecimal.valueOf(70), BigDecimal.valueOf(1000));
        ReflectionTestUtils.setField(session, "id", 200L);

        when(gymService.account(ptUser.getEmail())).thenReturn(ptUser);
        when(pts.findByUserAccountId(ptUser.getId())).thenReturn(Optional.of(pt));
        when(sessions.findByIdAndPersonalTrainerId(session.getId(), pt.getId())).thenReturn(Optional.of(session));
        when(earnings.existsByPtSessionId(session.getId())).thenReturn(false);

        PtSessionDto completed = service.completeSession(ptUser.getEmail(), Role.PERSONAL_TRAINER, session.getId(), "Great progress", memberUser.getMemberPin());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.verificationMethod()).isEqualTo("MEMBER_PIN");
        assertThat(membership.getPtSessionsUsed()).isEqualTo(1);

        // Verify immutable earning was saved with 70% PT cut and 30% gym cut
        ArgumentCaptor<PtEarning> captor = ArgumentCaptor.forClass(PtEarning.class);
        verify(earnings).save(captor.capture());

        PtEarning savedEarning = captor.getValue();
        assertThat(savedEarning.getGrossRevenue()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(savedEarning.getPtCommission()).isEqualByComparingTo(BigDecimal.valueOf(700)); // 70%
        assertThat(savedEarning.getGymRetained()).isEqualByComparingTo(BigDecimal.valueOf(300)); // 30%
        assertThat(savedEarning.getCommissionRate()).isEqualByComparingTo(BigDecimal.valueOf(70));
    }

    @Test
    void completeSession_withWrongPin_throwsBadRequest() {
        LocalDate sessionDate = LocalDate.now().minusDays(1);
        PtSession session = new PtSession(pt, membership, sessionDate, LocalTime.of(14, 0), LocalTime.of(15, 0),
                "Notes", BigDecimal.valueOf(70), BigDecimal.valueOf(1000));
        ReflectionTestUtils.setField(session, "id", 201L);

        when(gymService.account(ptUser.getEmail())).thenReturn(ptUser);
        when(pts.findByUserAccountId(ptUser.getId())).thenReturn(Optional.of(pt));
        when(sessions.findByIdAndPersonalTrainerId(session.getId(), pt.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeSession(ptUser.getEmail(), Role.PERSONAL_TRAINER, session.getId(), "Notes", "9999"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid client verification PIN");
    }
}

