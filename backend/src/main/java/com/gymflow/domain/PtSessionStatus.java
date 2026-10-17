package com.gymflow.domain;

/**
 * Lifecycle states for a PT session.
 */
public enum PtSessionStatus {
    /** Tentative slot held but not yet confirmed by member */
    TENTATIVE,
    /** Confirmed booking */
    BOOKED,
    /** Session occurred and PT marked attendance */
    COMPLETED,
    /** Cancelled before the session */
    CANCELLED,
    /** Member did not show up */
    MISSED
}

