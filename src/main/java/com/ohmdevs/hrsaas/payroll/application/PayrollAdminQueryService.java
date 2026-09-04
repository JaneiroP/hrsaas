package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollRunRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipLineRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lecturas de {@code payroll} para {@code payroll.web.PayrollAdminController}
 * (alcance {@code COMPANY}) — separado de {@link PayrollRunLifecycleService}
 * a propósito: aquel es comando, este es consulta, mismo criterio que
 * {@code org.application.OrgAdminQueryService} frente a
 * {@code OrgStructureService}.
 *
 * <p>{@link #getRun(Long)}/{@link #listRuns(Long)} usan los finders con
 * {@code JOIN FETCH} de {@link PayrollRunRepository} y no {@code findById}:
 * el controlador arma la respuesta (nombre y fechas del período) después de
 * que esta transacción cierra, y {@code spring.jpa.open-in-view=false} no
 * deja sesión de respaldo para inicializar la asociación perezosa
 * {@code PayrollRun.payrollPeriod} en ese momento — el mismo
 * {@code LazyInitializationException} que motivó el fetch-join de
 * {@code PositionRepository} en {@code org}. Por la misma razón, cada acción
 * de ciclo de vida en el controlador vuelve a leer aquí después de invocar
 * {@link PayrollRunLifecycleService}, en vez de reusar la entidad que ese
 * servicio devolvió (su transacción ya cerró para cuando el controlador la
 * toca).
 */
@Service
public class PayrollAdminQueryService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;
    private final PayslipLineRepository payslipLineRepository;

    public PayrollAdminQueryService(PayrollPeriodRepository payrollPeriodRepository,
                                     PayrollRunRepository payrollRunRepository,
                                     PayslipRepository payslipRepository,
                                     PayslipLineRepository payslipLineRepository) {
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
        this.payslipLineRepository = payslipLineRepository;
    }

    @Transactional
    public List<PayrollPeriod> listPeriods(Long companyId) {
        return payrollPeriodRepository.findByCompanyId(companyId);
    }

    @Transactional
    public PayrollPeriod getPeriod(Long periodId) {
        return payrollPeriodRepository.findById(periodId)
                .orElseThrow(() -> new NoSuchElementException("Periodo de nomina " + periodId + " no encontrado"));
    }

    @Transactional
    public List<PayrollRun> listRuns(Long companyId) {
        return payrollRunRepository.findByCompanyIdFetchPeriod(companyId);
    }

    @Transactional
    public PayrollRun getRun(Long runId) {
        return payrollRunRepository.findByIdFetchPeriod(runId)
                .orElseThrow(() -> new NoSuchElementException("Corrida de nomina " + runId + " no encontrada"));
    }

    /** Valida que la corrida exista en el tenant antes de listar, para no confundir "sin volantes todavia" (BORRADOR sin calcular) con "inexistente". */
    @Transactional
    public List<Payslip> listPayslips(Long runId) {
        if (!payrollRunRepository.existsById(runId)) {
            throw new NoSuchElementException("Corrida de nomina " + runId + " no encontrada");
        }
        return payslipRepository.findByPayrollRunId(runId);
    }

    @Transactional
    public Payslip getPayslip(Long runId, Long payslipId) {
        return payslipRepository.findByIdAndPayrollRunId(payslipId, runId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Volante " + payslipId + " no encontrado en la corrida " + runId));
    }

    @Transactional
    public List<PayslipLine> getPayslipLines(Long payslipId) {
        return payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(payslipId);
    }
}
