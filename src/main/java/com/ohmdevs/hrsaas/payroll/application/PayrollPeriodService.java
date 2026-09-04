package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Alta de {@link PayrollPeriod} — el paso previo a poder crear una corrida:
 * {@code PayrollRunLifecycleService.createRun} exige que el período ya
 * exista. Sin ciclo de vida propio (no hay {@code enum} de estado ni
 * {@code canTransitionTo}): un período no cambia, solo se referencia desde
 * las corridas que corren sobre él; corregir uno mal creado es una fila
 * nueva, igual que las claves de negocio de {@code org} (ver Javadoc de
 * {@code OrgStructureService}).
 *
 * <p>No valida solapamiento con otro período de la misma empresa a
 * propósito: dos períodos superpuestos son una situación rara pero no
 * necesariamente inválida (una corrida especial de bono junto a la nómina
 * regular del mes, por ejemplo) — inventar esa regla de negocio sin
 * confirmarla sería el mismo error que AD-04 prohíbe para cifras fiscales,
 * aplicado a reglas de proceso.
 */
@Service
public class PayrollPeriodService {

    private final PayrollPeriodRepository payrollPeriodRepository;

    public PayrollPeriodService(PayrollPeriodRepository payrollPeriodRepository) {
        this.payrollPeriodRepository = payrollPeriodRepository;
    }

    @Transactional
    public PayrollPeriod createPeriod(Long companyId, String name, PayrollFrequency frequency,
                                       LocalDate startDate, LocalDate endDate, LocalDate payDate) {
        TenantContext.require();
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        if (payDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha de pago no puede ser anterior a la fecha de inicio del periodo");
        }

        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setName(name);
        period.setFrequency(frequency);
        period.setStartDate(startDate);
        period.setEndDate(endDate);
        period.setPayDate(payDate);
        return payrollPeriodRepository.save(period);
    }
}
