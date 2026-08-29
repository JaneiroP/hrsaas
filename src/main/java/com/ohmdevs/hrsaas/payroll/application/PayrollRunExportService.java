package com.ohmdevs.hrsaas.payroll.application;

import com.ohmdevs.hrsaas.payroll.domain.Accrual;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.infrastructure.AccrualRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exportación de una corrida (blueprint §12 Fase 4: "Exportaciones: nómina,
 * costo laboral, plantilla, resúmenes por centro de costo"). Formato propio
 * en las tres — a diferencia de los layouts bancarios o los generadores
 * TSS/DGII/INFOTEP, ninguna exige una especificación externa, así que las
 * tres quedan implementadas aquí. <strong>No implementado en esta pasada</strong>:
 * la exportación de "plantilla" (un CSV en blanco descargable, que en
 * realidad es responsabilidad de {@code dataio.FileTemplate} — ver
 * {@code dataio/package-info.java} — no de este servicio) y los layouts
 * bancarios/generadores gubernamentales, que sí necesitan una especificación
 * real todavía no disponible.
 */
@Service
public class PayrollRunExportService {

    private static final String[] SUMMARY_HEADERS = {"employeeId", "contractId", "currency", "grossPay", "netPay", "totalDeductions"};
    private static final String[] LABOR_COST_HEADERS = {"employeeId", "contractId", "currency", "grossPay", "employerAccruals", "totalLaborCost"};
    private static final String[] COST_CENTER_HEADERS =
            {"costCenterId", "employeeCount", "totalGrossPay", "totalNetPay", "totalEmployerAccruals", "totalLaborCost"};

    private final PayslipRepository payslipRepository;
    private final AccrualRepository accrualRepository;

    public PayrollRunExportService(PayslipRepository payslipRepository, AccrualRepository accrualRepository) {
        this.payslipRepository = payslipRepository;
        this.accrualRepository = accrualRepository;
    }

    /** Un CSV con un renglón por volante de la corrida — bruto, neto, y el total descontado. */
    @Transactional(readOnly = true)
    public byte[] exportPayrollRunSummaryCsv(Long payrollRunId) {
        List<Payslip> payslips = payslipsOrThrow(payrollRunId);

        return writeCsv(SUMMARY_HEADERS, printer -> {
            for (Payslip payslip : payslips) {
                BigDecimal totalDeductions = payslip.getGrossPay().subtract(payslip.getNetPay());
                printer.printRecord(
                        payslip.getEmployeeId(), payslip.getContractId(), payslip.getCurrency(),
                        payslip.getGrossPay(), payslip.getNetPay(), totalDeductions);
            }
        });
    }

    /**
     * Costo laboral real por empleado: no es el neto pagado, es lo que le
     * cuesta al empleador — bruto devengado más los aportes
     * patronales/provisiones de la misma corrida ({@link Accrual}, que
     * nunca tocan el neto pero sí son costo real).
     */
    @Transactional(readOnly = true)
    public byte[] exportLaborCostCsv(Long payrollRunId) {
        List<Payslip> payslips = payslipsOrThrow(payrollRunId);
        Map<Long, BigDecimal> accrualsByEmployee = accrualsByEmployee(payrollRunId);

        return writeCsv(LABOR_COST_HEADERS, printer -> {
            for (Payslip payslip : payslips) {
                BigDecimal employerAccruals = accrualsByEmployee.getOrDefault(payslip.getEmployeeId(), BigDecimal.ZERO);
                BigDecimal totalLaborCost = payslip.getGrossPay().add(employerAccruals);
                printer.printRecord(
                        payslip.getEmployeeId(), payslip.getContractId(), payslip.getCurrency(),
                        payslip.getGrossPay(), employerAccruals, totalLaborCost);
            }
        });
    }

    /**
     * Costo laboral agregado por centro de costo — el centro de costo es el
     * que quedó capturado en cada {@link Payslip} al momento del cálculo
     * ({@code costCenterId}), no el vigente hoy en el contrato. Los
     * volantes sin centro de costo asignado se agrupan bajo un id vacío.
     */
    @Transactional(readOnly = true)
    public byte[] exportCostCenterSummaryCsv(Long payrollRunId) {
        List<Payslip> payslips = payslipsOrThrow(payrollRunId);
        Map<Long, BigDecimal> accrualsByEmployee = accrualsByEmployee(payrollRunId);

        Map<Long, List<Payslip>> byCostCenter = payslips.stream()
                .collect(Collectors.groupingBy(p -> p.getCostCenterId() == null ? -1L : p.getCostCenterId()));

        return writeCsv(COST_CENTER_HEADERS, printer -> {
            for (var entry : byCostCenter.entrySet().stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey)).toList()) {
                List<Payslip> group = entry.getValue();
                BigDecimal totalGross = sum(group, Payslip::getGrossPay);
                BigDecimal totalNet = sum(group, Payslip::getNetPay);
                BigDecimal totalAccruals = group.stream()
                        .map(p -> accrualsByEmployee.getOrDefault(p.getEmployeeId(), BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalLaborCost = totalGross.add(totalAccruals);
                Object costCenterId = entry.getKey() == -1L ? "" : entry.getKey();
                printer.printRecord(costCenterId, group.size(), totalGross, totalNet, totalAccruals, totalLaborCost);
            }
        });
    }

    private Map<Long, BigDecimal> accrualsByEmployee(Long payrollRunId) {
        return accrualRepository.findByPayrollRunId(payrollRunId).stream()
                .collect(Collectors.groupingBy(Accrual::getEmployeeId,
                        Collectors.reducing(BigDecimal.ZERO, Accrual::getAmount, BigDecimal::add)));
    }

    private List<Payslip> payslipsOrThrow(Long payrollRunId) {
        List<Payslip> payslips = payslipRepository.findByPayrollRunId(payrollRunId);
        if (payslips.isEmpty()) {
            throw new IllegalStateException("PayrollRun " + payrollRunId + " no tiene volantes para exportar (¿ya se calculó?)");
        }
        return payslips;
    }

    private static BigDecimal sum(List<Payslip> payslips, Function<Payslip, BigDecimal> extractor) {
        return payslips.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private byte[] writeCsv(String[] headers, CsvBody body) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers).build();
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            body.write(printer);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el CSV de exportación", e);
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface CsvBody {
        void write(CSVPrinter printer) throws IOException;
    }
}
