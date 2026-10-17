package com.gymflow.repository;

import com.gymflow.domain.PtAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PtAvailabilityRepository extends JpaRepository<PtAvailability, Long> {
    List<PtAvailability> findByPersonalTrainerIdOrderByDayOfWeekAscStartTimeAsc(Long ptId);
    void deleteByPersonalTrainerId(Long ptId);
}
