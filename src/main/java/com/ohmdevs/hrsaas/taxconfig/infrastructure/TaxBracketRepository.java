package com.ohmdevs.hrsaas.taxconfig.infrastructure;

import com.ohmdevs.hrsaas.taxconfig.domain.TaxBracket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaxBracketRepository extends JpaRepository<TaxBracket, Long> {

    /** La escala completa vigente en una fecha, en orden de tramo. */
    @Query("""
            SELECT b FROM TaxBracket b
            WHERE b.schemeCode = :schemeCode
              AND b.effectiveFrom <= :date
              AND (b.effectiveTo IS NULL OR b.effectiveTo >= :date)
            ORDER BY b.bracketOrder ASC
            """)
    List<TaxBracket> findActiveOn(@Param("schemeCode") String schemeCode, @Param("date") LocalDate date);
}
