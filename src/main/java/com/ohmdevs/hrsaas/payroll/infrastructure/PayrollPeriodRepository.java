package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {

    /** Sin asociaciones {@code @ManyToOne} en {@link PayrollPeriod}: ningún riesgo de {@code LazyInitializationException} al usarla fuera de una transacción. */
    List<PayrollPeriod> findByCompanyId(Long companyId);
}
