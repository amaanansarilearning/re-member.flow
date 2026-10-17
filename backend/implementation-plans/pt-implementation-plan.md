# Personal Trainer (PT) Feature – Implementation Plan

## Codebase Summary

### Tech Stack
- **Backend**: Spring Boot 3.4.4, Java 17, Spring Security (JWT + CSRF), Spring Data JPA, MySQL (prod), H2 (test)
- **Frontend**: Angular 18+ (signals-based, single `App` component, single `dashboard.html` template, single `gym-api.service.ts`)
- **Pattern**: Single-page Angular app — all views are conditional sections in `dashboard.html`, state in `app.ts` with signals

### Existing Roles
- `OWNER` – full access, multi-branch org management
- `STAFF` – branch-scoped membership management
- `MEMBER` – read-only own memberships

### Key Entities
| Entity | Table | Notes |
|---|---|---|
| `UserAccount` | `user_accounts` | role, org, branch |
| `GymOrganization` | – | parent org |
| `GymBranch` | – | location/franchise |
| `MembershipPlan` | `membership_plans` | price, duration, active flag |
| `Membership` | `memberships` | snapshot fields, renewedFrom, status |
| `MembershipReceipt` | – | payment receipt blob |
| `MembershipCancellation` | – | cancellation records |
| `MembershipFreeze` | – | freeze periods |
| `ActionHistory` | – | audit log |

### Existing Services
- `GymService` – plans, branches, staff, members, register, renew, cancel, dashboard
- `MembershipOperationsService` – freeze, transfer, correct, access check
- `AnalyticsService` – revenue analytics, tax, trends, package performance
- `ReportService` – CSV export
- `ActionHistoryService` – audit recording

---

## New Feature Architecture

### Stage 1 – Backend Entities & Schema

**New entities:**

```
PersonalTrainer (personal_trainers)
  - id, userAccount (FK → user_accounts, unique), organization, branch
  - specializations (TEXT), bio (TEXT)
  - sessionFeePerHour (DECIMAL), commissionPercent (DECIMAL 5,2)
  - active (BOOLEAN), createdAt
  
PtAvailability (pt_availability)
  - id, personalTrainer, dayOfWeek (MON-SUN), startTime, endTime
  - effectiveFrom, effectiveTo (nullable)

PtSession (pt_sessions)
  - id, personalTrainer, membership (FK → memberships)
  - scheduledDate, startTime, endTime
  - status: TENTATIVE | BOOKED | COMPLETED | CANCELLED | MISSED
  - notes, cancelReason, completedAt, createdAt, updatedAt
  - commissionRateSnapshot (DECIMAL 5,2)
  - sessionFeeSnapshot (DECIMAL 10,2)

PtEarning (pt_earnings)  ← immutable financial snapshot per completed session
  - id, ptSession (FK), personalTrainer
  - grossRevenue, ptCommission, gymRetained
  - commissionRate (snapshot), createdAt, payoutId (nullable FK)

PtPayout (pt_payouts)
  - id, personalTrainer, periodFrom, periodTo
  - totalEarnings, status: PENDING | PAID
  - paidAt, paidBy (actor name), notes, createdAt
```

**Schema changes to existing entities:**

```
MembershipPlan: add columns
  - ptPackageType: NONE | PT_ONLY | COMBINED (default NONE)
  - includedPtSessions (INT nullable)
  - ptFeeIncluded (DECIMAL nullable)  ← portion of plan price attributed to PT

Membership: add columns
  - personalTrainerId (FK → personal_trainers, nullable)
  - ptSessionsIncluded (INT, snapshot at time of registration)
  - ptSessionsUsed (INT default 0)
  - ptCommissionRateSnapshot (DECIMAL 5,2, nullable)
```

**Role enum change:**
```java
public enum Role { OWNER, STAFF, MEMBER, PERSONAL_TRAINER }
```

---

### Stage 2 – Repositories

New:
- `PersonalTrainerRepository`
- `PtAvailabilityRepository`
- `PtSessionRepository`
- `PtEarningRepository`
- `PtPayoutRepository`

---

### Stage 3 – Services

New:
- `PersonalTrainerService` – CRUD PT profiles, assign to members, commission config
- `PtSchedulingService` – availability, booking, rescheduling, conflict detection
- `PtEarningsService` – commission calculation, payout management, reconciliation
- `PtAnalyticsService` – PT-filtered analytics, revenue splits

Modified:
- `GymService.register()` – accept optional `personalTrainerId`, validate PT active + available, snapshot commission rate
- `GymService.renew()` – support PT re-assignment on renewal
- `GymService.plans()` – include PT package metadata
- `AnalyticsService.analytics()` – include PT revenue breakdown

---

### Stage 4 – DTOs (additions to `ApiDtos.java`)

```java
record PtProfileDto(Long id, Long userId, String fullName, String email, String specializations,
    BigDecimal sessionFee, BigDecimal commissionPercent, boolean active, int assignedMemberCount)
record PtRegistrationRequest(...)
record PtUpdateRequest(...)
record PtSessionDto(Long id, Long ptId, String ptName, Long membershipId, ...)
record PtSessionBookRequest(...)
record PtEarningDto(Long id, Long sessionId, BigDecimal ptCommission, BigDecimal gymRetained, ...)
record PtPayoutDto(Long id, BigDecimal totalEarnings, String status, ...)
record PtAnalyticsDto(activePts, membersPerPt, sessionsCompleted, sessionsCancelled, 
    totalPtRevenue, ptEarnings, gymRetained, pendingPayouts, ...)
```

---

### Stage 5 – Controllers

New:
- `PersonalTrainerController` (`/api/pt`) – PT CRUD (OWNER only), PT self-read
- `PtScheduleController` (`/api/pt/{id}/sessions`) – sessions CRUD, attendance
- `PtPayoutController` (`/api/pt/{id}/payouts`) – payout management

Modified:
- `GymController` – add PT-filtered analytics endpoint
- Membership registration/renewal to accept `personalTrainerId`

---

### Stage 6 – Security

- Add `PERSONAL_TRAINER` to `Role` enum
- Update `SecurityConfig` – PT access to own data only
- `@PreAuthorize` on all PT endpoints:
  - Owner: full CRUD
  - PT: GET own profile, sessions, earnings only
  - Staff: read-only PT list for member assignment
- Database-level scoping in service methods (never rely on UI)

---

### Stage 7 – Frontend

**New views in `app.ts` type `View`:**
```typescript
type View = ... | 'pt-management' | 'pt-dashboard' | 'pt-schedule' | 'pt-earnings';
```

**New API methods in `gym-api.service.ts`:**
- `personalTrainers()`, `createPt()`, `updatePt()`, `activatePt()`, `deactivatePt()`
- `ptSessions()`, `bookSession()`, `cancelSession()`, `completeSession()`
- `ptEarnings()`, `ptPayouts()`, `createPayout()`, `markPaid()`
- `ptAnalytics()`

**New sections in `dashboard.html`:**
- Owner: PT Management (list, create, edit, commission config)
- Owner: PT in Analytics section (revenue split chart, table)
- PT: PT Dashboard (own sessions, members, upcoming schedule)
- PT: PT Earnings (commission statements, payout history)
- Registration form: PT selector (when plan allows PT)

---

### Stage 8 – Tests

- PT role isolation (cannot see other PTs' data)
- Commission calculation (snapshot immutability)
- Scheduling conflict prevention
- Package entitlements (PT sessions included)
- Renewal with PT assignment
- Payout reconciliation

---

## Implementation Sequence

1. ✅ Add `PERSONAL_TRAINER` to `Role` enum
2. ✅ Add `PersonalTrainer` entity + schema changes to `MembershipPlan` and `Membership`
3. ✅ Create `PtAvailability`, `PtSession`, `PtEarning`, `PtPayout` entities
4. ✅ Create repositories
5. ✅ Add PT DTOs to `ApiDtos.java`
6. ✅ Create `PersonalTrainerService` + `PtSchedulingService` + `PtEarningsService`
7. ✅ Create `PersonalTrainerController`
8. ✅ Update `SecurityConfig` for PERSONAL_TRAINER role
9. ✅ Extend `GymService.register()` / `renew()` for PT assignment
10. ✅ Extend `AnalyticsService` with PT metrics
11. ✅ Frontend: API service methods
12. ✅ Frontend: PT management screens (Owner)
13. ✅ Frontend: PT dashboard + sessions (PT role)
14. ✅ Frontend: PT earnings (PT role)
15. ✅ Run tests, fix regressions

---

## Risk Notes

- **Role enum change**: `PERSONAL_TRAINER` in JWT and Spring Security roles must be consistent
- **Double-counting**: PT commission stored as a ledger entry separate from membership revenue; analytics must not sum both
- **Snapshot immutability**: `PtEarning` records must never be recomputed from updated rates
- **Concurrent bookings**: `PtSession` creation needs pessimistic lock or unique constraint on (ptId, scheduledDate, startTime)
- **Existing tests**: Existing tests reference `Role.OWNER/STAFF/MEMBER` only, will need updating if `currentRole()` parsing changes
- **DDL auto**: `spring.jpa.hibernate.ddl-auto=update` will auto-add columns; we still need to handle nullable/default constraints carefully
