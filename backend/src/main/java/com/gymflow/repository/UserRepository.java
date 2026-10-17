package com.gymflow.repository;

import com.gymflow.domain.Role;
import com.gymflow.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    List<UserAccount> findByOrganizationIdAndRoleOrderByFullNameAsc(Long organizationId, Role role);

    List<UserAccount> findByOrganizationIdAndBranchIdAndRoleOrderByFullNameAsc(Long organizationId, Long branchId,
            Role role);

    long countByRoleAndOrganizationId(Role role, Long organizationId);

    long countByRoleAndOrganizationIdAndBranchId(Role role, Long organizationId, Long branchId);

    @Query(value = "select u from UserAccount u where u.organization.id = :organizationId "
            + "and u.role = com.gymflow.domain.Role.MEMBER "
            + "and (:memberId is null or u.id = :memberId) "
            + "and (:search = '' or lower(u.fullName) like lower(concat('%', :search, '%')) "
            + "or lower(u.email) like lower(concat('%', :search, '%'))) "
            + "and exists (select m from Membership m where m.member = u "
            + "and (:branchId is null or m.branch.id = :branchId) "
            + "and (:filter = 'all' "
            + "or (:filter = 'active' and m.status = com.gymflow.domain.MembershipStatus.ACTIVE "
            + "and m.startDate <= :today and m.endDate >= :today) "
            + "or (:filter = 'expiring' and m.status = com.gymflow.domain.MembershipStatus.ACTIVE "
            + "and m.startDate <= :today and m.endDate between :today and :expiringOn "
            + "and m.renewalStartDate is null) "
            + "or (:filter = 'cancelled' and m.status = com.gymflow.domain.MembershipStatus.CANCELLED "
            + "and not exists (select newerTerm from Membership newerTerm where newerTerm.member = u "
            + "and (:branchId is null or newerTerm.branch.id = :branchId) "
            + "and (newerTerm.startDate > m.startDate "
            + "or (newerTerm.startDate = m.startDate and newerTerm.createdAt > m.createdAt) "
            + "or (newerTerm.startDate = m.startDate and newerTerm.createdAt = m.createdAt "
            + "and newerTerm.id > m.id))) "
            + "and not exists (select activeTerm from Membership activeTerm where activeTerm.member = u "
            + "and (:branchId is null or activeTerm.branch.id = :branchId) "
            + "and activeTerm.status = com.gymflow.domain.MembershipStatus.ACTIVE "
            + "and (activeTerm.startDate > :today or activeTerm.endDate >= :today)))))",
            countQuery = "select count(u) from UserAccount u where u.organization.id = :organizationId "
                    + "and u.role = com.gymflow.domain.Role.MEMBER "
                    + "and (:memberId is null or u.id = :memberId) "
                    + "and (:search = '' or lower(u.fullName) like lower(concat('%', :search, '%')) "
                    + "or lower(u.email) like lower(concat('%', :search, '%'))) "
                    + "and exists (select m from Membership m where m.member = u "
                    + "and (:branchId is null or m.branch.id = :branchId) "
                    + "and (:filter = 'all' "
                    + "or (:filter = 'active' and m.status = com.gymflow.domain.MembershipStatus.ACTIVE "
                    + "and m.startDate <= :today and m.endDate >= :today) "
                    + "or (:filter = 'expiring' and m.status = com.gymflow.domain.MembershipStatus.ACTIVE "
                    + "and m.startDate <= :today and m.endDate between :today and :expiringOn "
                    + "and m.renewalStartDate is null) "
                    + "or (:filter = 'cancelled' and m.status = com.gymflow.domain.MembershipStatus.CANCELLED "
                    + "and not exists (select newerTerm from Membership newerTerm where newerTerm.member = u "
                    + "and (:branchId is null or newerTerm.branch.id = :branchId) "
                    + "and (newerTerm.startDate > m.startDate "
                    + "or (newerTerm.startDate = m.startDate and newerTerm.createdAt > m.createdAt) "
                    + "or (newerTerm.startDate = m.startDate and newerTerm.createdAt = m.createdAt "
                    + "and newerTerm.id > m.id))) "
                    + "and not exists (select activeTerm from Membership activeTerm where activeTerm.member = u "
                    + "and (:branchId is null or activeTerm.branch.id = :branchId) "
                    + "and activeTerm.status = com.gymflow.domain.MembershipStatus.ACTIVE "
                    + "and (activeTerm.startDate > :today or activeTerm.endDate >= :today)))))")
    Page<UserAccount> findMembershipMembers(@Param("organizationId") Long organizationId,
            @Param("memberId") Long memberId, @Param("branchId") Long branchId,
            @Param("search") String search, @Param("filter") String filter,
            @Param("today") LocalDate today, @Param("expiringOn") LocalDate expiringOn,
            Pageable pageable);
}