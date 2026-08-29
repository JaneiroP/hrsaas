package com.ohmdevs.hrsaas.taxconfig.infrastructure;

import com.ohmdevs.hrsaas.taxconfig.domain.StatutoryParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface StatutoryParameterRepository extends JpaRepository<StatutoryParameter, Long> {

    @Query("""
            SELECT p FROM StatutoryParameter p
            WHERE p.code = :code
              AND p.effectiveFrom <= :date
              AND (p.effectiveTo IS NULL OR p.effectiveTo >= :date)
            """)
    Optional<StatutoryParameter> findActiveOn(@Param("code") String code, @Param("date") LocalDate date);
}
