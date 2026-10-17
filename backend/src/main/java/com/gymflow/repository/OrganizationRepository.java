package com.gymflow.repository;

import com.gymflow.domain.GymOrganization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<GymOrganization, Long> {
    Optional<GymOrganization> findFirstByOrderByIdAsc();

    Optional<GymOrganization> findByCodeIgnoreCase(String code);
}