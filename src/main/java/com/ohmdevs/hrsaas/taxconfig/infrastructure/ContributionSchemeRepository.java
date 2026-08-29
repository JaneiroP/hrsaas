package com.ohmdevs.hrsaas.taxconfig.infrastructure;

import com.ohmdevs.hrsaas.taxconfig.domain.ContributionScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ContributionSchemeRepository extends JpaRepository<ContributionScheme, Long> {

    /** El vigente en una fecha: su rango cubre esa fecha (fin abierto o posterior). */
    @Query("""
            SELECT s FROM ContributionScheme s
            WHERE s.code = :code
              AND s.effectiveFrom <= :date
              AND (s.effectiveTo IS NULL OR s.effectiveTo >= :date)
            """)
    Optional<ContributionScheme> findActiveOn(@Param("code") String code, @Param("date") LocalDate date);
}
