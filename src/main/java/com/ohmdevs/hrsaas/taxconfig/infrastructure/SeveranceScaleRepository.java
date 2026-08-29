package com.ohmdevs.hrsaas.taxconfig.infrastructure;

import com.ohmdevs.hrsaas.taxconfig.domain.SeveranceScale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SeveranceScaleRepository extends JpaRepository<SeveranceScale, Long> {

    @Query("""
            SELECT s FROM SeveranceScale s
            WHERE s.effectiveFrom <= :date
              AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)
            ORDER BY s.minMonthsOfService ASC
            """)
    List<SeveranceScale> findActiveOn(@Param("date") LocalDate date);
}
