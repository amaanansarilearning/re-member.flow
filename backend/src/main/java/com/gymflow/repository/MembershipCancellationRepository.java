package com.gymflow.repository;

import com.gymflow.domain.MembershipCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipCancellationRepository extends JpaRepository<MembershipCancellation, Long> {
}
