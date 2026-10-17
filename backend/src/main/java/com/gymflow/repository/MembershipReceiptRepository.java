package com.gymflow.repository;

import com.gymflow.domain.MembershipReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipReceiptRepository extends JpaRepository<MembershipReceipt, Long> {
    @Query("select receipt from MembershipReceipt receipt where receipt.membership.id = :membershipId")
    Optional<MembershipReceipt> findByMembershipId(@Param("membershipId") Long membershipId);
}