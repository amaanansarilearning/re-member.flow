package com.gymflow.repository;

import java.time.Instant;

public interface MembershipActivityProjection {
    Long getMembershipId();

    Instant getActivityAt();
}
