package com.gymflow.repository;

import com.gymflow.domain.PersonalTrainer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PersonalTrainerRepository extends JpaRepository<PersonalTrainer, Long> {
    long countByOrganizationId(Long organizationId);

    List<PersonalTrainer> findByOrganizationIdOrderByUserAccountFullNameAsc(Long organizationId);
    List<PersonalTrainer> findByOrganizationIdAndActiveTrueOrderByUserAccountFullNameAsc(Long organizationId);
    Optional<PersonalTrainer> findByUserAccountId(Long userAccountId);
    Optional<PersonalTrainer> findByIdAndOrganizationId(Long id, Long organizationId);
    boolean existsByUserAccountId(Long userAccountId);

    @Query("select count(m) from Membership m where m.personalTrainer.id = :ptId and m.status = com.gymflow.domain.MembershipStatus.ACTIVE")
    long countActiveMembers(@Param("ptId") Long ptId);

    @Query("select count(m) from Membership m where m.personalTrainer.id = :ptId")
    long countTotalMembers(@Param("ptId") Long ptId);
}
