package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exportación de una corrida (blueprint §12 Fase 4: "Exportaciones: nómina,
 * costo laboral, plantilla, resúmenes por centro de costo"). Esta pasada
 * cubre solo el resumen de nómina — un CSV con un renglón por volante — que
 * no depende de ninguna especificación externa (a diferencia de los
 * layouts bancarios o los generadores TSS/DGII/INFOTEP, que sí la
 * necesitan y quedan fuera de esta pasada, ver {@code payroll/package-info.java}).
 *
 * <p><strong>No implementado en esta pasada</strong>: costo laboral (exige
 * sumar también los {@code Accrual} de aportes patronales/provisiones, y
 * threadear {@code costCenterId} desde {@code contracts} hasta {@code payroll},
 * que hoy no viaja en {@code ActiveContractSummary}) y resúmenes por centro
 * de costo (misma dependencia). Formato propio, no exige un layout
 * gubernamental — se define aquí mismo, no en un descriptor externo.
 */
@Service
public class PayrollRunExportService {

    private static final String[] HEADERS = {"employeeId", "contractId", "currency", "grossPay", "netPay", "totalDeductions"};

    private final PayslipRepository payslipRepository;

    public PayrollRunExportService(PayslipRepository payslipRepository) {
        this.payslipRepository = payslipRepository;
    }

    /** Un CSV con un renglón por volante de la corrida — bruto, neto, y el total descontado. */
    @Transactional(readOnly = true)
    public byte[] exportPayrollRunSummaryCsv(Long payrollRunId) {
        List<Payslip> payslips = payslipRepository.findByPayrollRunId(payrollRunId);
        if (payslips.isEmpty()) {
            throw new IllegalStateException("PayrollRun " + payrollRunId + " no tiene volantes para exportar (¿ya se calculó?)");
        }

        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).build();
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Payslip payslip : payslips) {
                BigDecimal totalDeductions = payslip.getGrossPay().subtract(payslip.getNetPay());
                printer.printRecord(
                        payslip.getEmployeeId(),
                        payslip.getContractId(),
                        payslip.getCurrency(),
                        payslip.getGrossPay(),
                        payslip.getNetPay(),
                        totalDeductions);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el CSV de exportación", e);
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }
}
