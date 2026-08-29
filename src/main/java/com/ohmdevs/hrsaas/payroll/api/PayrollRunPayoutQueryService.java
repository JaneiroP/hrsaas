package com.ohmdevs.hrsaas.payroll.api;

import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRunStatus;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollRunRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Único contenido público de {@code payroll}: los netos a pagar de una
 * corrida, para que {@code banking} arme un lote (blueprint §06: "corrida
 * aprobada → PaymentBatch"). Devuelve todos los volantes, incluidos los de
 * neto cero o negativo (una corrida de reverso, por ejemplo) — filtrar
 * cuáles se pagan de verdad es responsabilidad de {@code banking}
 * (blueprint §06: "los netos en cero se excluyen y se reportan"), no de
 * esta consulta.
 */
@Service
public class PayrollRunPayoutQueryService {

    private static final Set<PayrollRunStatus> PAYABLE = EnumSet.of(
            PayrollRunStatus.APROBADA, PayrollRunStatus.PAGADA, PayrollRunStatus.CERRADA);

    private final PayrollRunRepository payrollRunRepository;
    private final PayslipRepository payslipRepository;

    public PayrollRunPayoutQueryService(PayrollRunRepository payrollRunRepository, PayslipRepository payslipRepository) {
        this.payrollRunRepository = payrollRunRepository;
        this.payslipRepository = payslipRepository;
    }

    @Transactional
    public List<PayoutLine> payoutsForRun(Long payrollRunId) {
        PayrollRun run = payrollRunRepository.findById(payrollRunId)
                .orElseThrow(() -> new IllegalArgumentException("PayrollRun no encontrado: " + payrollRunId));
        if (!PAYABLE.contains(run.getStatus())) {
            throw new IllegalStateException(
                    "PayrollRun %s en estado %s no es pagable todavía (debe estar APROBADA o posterior)"
                            .formatted(payrollRunId, run.getStatus()));
        }
        return payslipRepository.findByPayrollRunId(payrollRunId).stream()
                .map(this::toPayoutLine)
                .toList();
    }

    private PayoutLine toPayoutLine(Payslip payslip) {
        return new PayoutLine(payslip.getEmployeeId(), payslip.getContractId(), payslip.getNetPay(), payslip.getCurrency());
    }
}
