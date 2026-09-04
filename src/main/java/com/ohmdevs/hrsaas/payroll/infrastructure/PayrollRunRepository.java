package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    /**
     * {@code JOIN FETCH payrollPeriod} a propósito: {@code PayrollAdminController}
     * arma la respuesta (nombre de período, fechas) fuera de la transacción de
     * {@code PayrollAdminQueryService} — mismo motivo, y misma solución, que
     * {@code PositionRepository.findByCompanyId}. Sin este fetch-join,
     * {@code payrollPeriod} llega como proxy sin inicializar y
     * {@code spring.jpa.open-in-view=false} no deja sesión de respaldo para
     * resolverlo.
     */
    @Query("SELECT r FROM PayrollRun r JOIN FETCH r.payrollPeriod WHERE r.companyId = :companyId ORDER BY r.id DESC")
    List<PayrollRun> findByCompanyIdFetchPeriod(@Param("companyId") Long companyId);

    /** Mismo motivo que {@link #findByCompanyIdFetchPeriod(Long)}, para la lectura de una única corrida. */
    @Query("SELECT r FROM PayrollRun r JOIN FETCH r.payrollPeriod WHERE r.id = :id")
    Optional<PayrollRun> findByIdFetchPeriod(@Param("id") Long id);
}
