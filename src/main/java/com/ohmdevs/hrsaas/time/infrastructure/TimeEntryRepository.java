package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    List<TimeEntry> findByEmployeeIdAndOccurredAtBetweenOrderByOccurredAtAsc(
            Long employeeId, Instant from, Instant to);
}
