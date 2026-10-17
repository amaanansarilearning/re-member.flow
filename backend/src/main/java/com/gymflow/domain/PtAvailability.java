package com.gymflow.domain;

import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Stores a personal trainer's recurring weekly availability window.
 * Multiple rows can exist per PT to model, e.g., Mon/Wed 06:00-10:00 and
 * Fri 16:00-20:00. Optional effectiveFrom/effectiveTo allow seasonal schedules.
 */
@Entity
@Table(name = "pt_availability", indexes = {
        @Index(name = "idx_pt_avail_trainer", columnList = "personal_trainer_id")
})
public class PtAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_trainer_id", nullable = false)
    private PersonalTrainer personalTrainer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** Null means the availability applies indefinitely from creation. */
    @Column
    private LocalDate effectiveFrom;

    /** Null means the availability has no planned end date. */
    @Column
    private LocalDate effectiveTo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PtAvailability() {
    }

    public PtAvailability(PersonalTrainer personalTrainer, DayOfWeek dayOfWeek,
            LocalTime startTime, LocalTime endTime,
            LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.personalTrainer = personalTrainer;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public void update(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
            LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public Long getId() { return id; }
    public PersonalTrainer getPersonalTrainer() { return personalTrainer; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public Instant getCreatedAt() { return createdAt; }
}

