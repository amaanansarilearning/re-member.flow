package com.gymflow.domain;

/**
 * Type of PT package a MembershipPlan represents.
 * NONE     – regular gym-only membership (default, backward-compatible)
 * PT_ONLY  – PT sessions only with no general gym access
 * COMBINED – gym access + PT sessions bundled in one plan
 */
public enum PtPackageType {
    NONE,
    PT_ONLY,
    COMBINED
}

