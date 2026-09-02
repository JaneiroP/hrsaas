package com.ohmdevs.hrsaas.payroll.api;

import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConceptType;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipLineRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Segundo contenido público de {@code payroll}, aparte de
 * {@link PayrollRunPayoutQueryService}: el histórico de volantes de <em>un
 * solo empleado</em>, para {@code portal} (blueprint §09).
 *
 * <p>Ninguna consulta aquí acepta un {@code companyId} ni recorre toda una
 * corrida: siempre se filtra por {@code employeeId}, el que resuelva
 * {@code portal} a partir del actor autenticado — nunca uno que llegue en la
 * URL. Es la misma defensa que documenta {@link com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository#findByIdAndEmployeeId}.
 */
@Service
public class PayslipSelfServiceQueryService {

    private final PayslipRepository payslipRepository;
    private final PayslipLineRepository payslipLineRepository;

    public PayslipSelfServiceQueryService(PayslipRepository payslipRepository, PayslipLineRepository payslipLineRepository) {
        this.payslipRepository = payslipRepository;
        this.payslipLineRepository = payslipLineRepository;
    }

    @Transactional
    public PayslipPage payslipsFor(Long employeeId, Long afterId, int limit) {
        Pageable page = PageRequest.of(0, limit + 1);
        List<Payslip> rows = afterId == null
                ? payslipRepository.findByEmployeeIdOrderByIdDesc(employeeId, page)
                : payslipRepository.findByEmployeeIdAndIdLessThanOrderByIdDesc(employeeId, afterId, page);

        boolean hasMore = rows.size() > limit;
        List<Payslip> pageRows = hasMore ? rows.subList(0, limit) : rows;
        Long nextAfterId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new PayslipPage(pageRows.stream().map(PayslipSelfServiceQueryService::toSummary).toList(), nextAfterId);
    }

    @Transactional
    public Optional<PayslipDetailView> payslipDetailFor(Long employeeId, Long payslipId) {
        return payslipRepository.findByIdAndEmployeeId(payslipId, employeeId).map(this::toDetail);
    }

    private static PayslipSummaryView toSummary(Payslip payslip) {
        PayrollRun run = payslip.getPayrollRun();
        return new PayslipSummaryView(
                payslip.getId(), run.getPayrollPeriod().getName(), run.getPayrollPeriod().getPayDate(),
                run.getStatus().name(), payslip.getGrossPay(), payslip.getNetPay(), payslip.getCurrency());
    }

    private PayslipDetailView toDetail(Payslip payslip) {
        PayrollRun run = payslip.getPayrollRun();
        List<PayslipLine> lines = payslipLineRepository.findByPayslipIdOrderByLineOrderAsc(payslip.getId());

        List<PayslipLineView> earnings = lines.stream()
                .filter(line -> line.getType() == PayrollConceptType.EARNING)
                .map(line -> new PayslipLineView(line.getDescription(), line.getAmount()))
                .toList();
        // Las deducciones se guardan con signo negativo (PayslipLine.amount, para que
        // sumar todas las líneas dé el neto sin lógica condicional); aquí se muestran
        // en magnitud positiva porque el consumidor ya las separa en su propia lista.
        List<PayslipLineView> deductions = lines.stream()
                .filter(line -> line.getType() == PayrollConceptType.DEDUCTION)
                .map(line -> new PayslipLineView(line.getDescription(), line.getAmount().abs()))
                .toList();
        BigDecimal totalDeductions = deductions.stream()
                .map(PayslipLineView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PayslipDetailView(
                payslip.getId(), run.getPayrollPeriod().getName(), run.getPayrollPeriod().getPayDate(),
                run.getStatus().name(), payslip.getGrossPay(), payslip.getNetPay(), payslip.getCurrency(),
                run.getCompanyId(), earnings, deductions, totalDeductions);
    }
}
