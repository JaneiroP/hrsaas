package com.ohmdevs.hrsaas.payroll.web;

import com.ohmdevs.hrsaas.payroll.api.PayslipLineView;
import com.ohmdevs.hrsaas.payroll.application.PayrollAdminQueryService;
import com.ohmdevs.hrsaas.payroll.application.PayrollPeriodService;
import com.ohmdevs.hrsaas.payroll.application.PayrollRunLifecycleService;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConceptType;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.platform.access.AccessGuard;
import com.ohmdevs.hrsaas.platform.access.ActorContext;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * API administrativa de nómina: períodos, corridas y sus volantes. No
 * duplica ninguna regla del motor — cada acción de ciclo de vida delega en
 * {@link PayrollRunLifecycleService} (único punto que valida
 * {@code PayrollRunStatus.canTransitionTo}, AD-06) y cada lectura en
 * {@link PayrollAdminQueryService}.
 *
 * <p><strong>Maker-checker (regla no negociable):</strong> {@code payroll:run}
 * cubre crear período/corrida, calcular, enviar a revisión, devolver a
 * borrador, marcar pagada, cerrar y anular; {@code payroll:approve} y
 * {@code payroll:reverse} son permisos aparte, deliberadamente no concedidos
 * a {@code HR_MANAGER} (ver Javadoc de {@code SystemRole#HR_MANAGER}) — quien
 * arma la nómina no debe ser quien la aprueba o la revierte.
 *
 * <p><strong>Mismo límite real que {@code ContractAdminController}/{@code
 * EmployeeAdminController}:</strong> se exige {@code COMPANY} o más, pero no
 * existe todavía una tabla de "empresas asignadas al actor" para acotar el
 * filtro solo a esas — el {@code companyId}/{@code runId} de la URL es una
 * consulta de negocio legítima, no una fuga per se, pero tampoco hay una capa
 * que impida a un actor de la empresa A pedir el {@code runId} de la empresa
 * B <em>dentro del mismo tenant</em>. Aislamiento entre tenants sí está
 * cubierto (RLS + {@code @TenantId}).
 */
@RestController
@RequestMapping("/api/v1/payroll")
public class PayrollAdminController {

    private final PayrollAdminQueryService queries;
    private final PayrollPeriodService periodService;
    private final PayrollRunLifecycleService lifecycle;
    private final AccessGuard accessGuard;

    public PayrollAdminController(PayrollAdminQueryService queries, PayrollPeriodService periodService,
                                   PayrollRunLifecycleService lifecycle, AccessGuard accessGuard) {
        this.queries = queries;
        this.periodService = periodService;
        this.lifecycle = lifecycle;
        this.accessGuard = accessGuard;
    }

    // --- periodos --------------------------------------------------------------

    @GetMapping("/companies/{companyId}/periods")
    public List<PayrollPeriodResponse> listPeriods(@PathVariable Long companyId) {
        accessGuard.require("payroll:read", PermissionScope.COMPANY);
        return queries.listPeriods(companyId).stream().map(PayrollAdminController::toResponse).toList();
    }

    @PostMapping("/companies/{companyId}/periods")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollPeriodResponse createPeriod(@PathVariable Long companyId,
                                               @Valid @RequestBody PayrollPeriodCreateRequest request) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        PayrollPeriod period = periodService.createPeriod(companyId, request.name(), request.frequency(),
                request.startDate(), request.endDate(), request.payDate());
        return toResponse(period);
    }

    // --- corridas ----------------------------------------------------------------

    @GetMapping("/companies/{companyId}/runs")
    public List<PayrollRunResponse> listRuns(@PathVariable Long companyId) {
        accessGuard.require("payroll:read", PermissionScope.COMPANY);
        return queries.listRuns(companyId).stream().map(PayrollAdminController::toResponse).toList();
    }

    @GetMapping("/runs/{id}")
    public PayrollRunResponse getRun(@PathVariable Long id) {
        accessGuard.require("payroll:read", PermissionScope.COMPANY);
        return toResponse(queries.getRun(id));
    }

    @PostMapping("/companies/{companyId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollRunResponse createRun(@PathVariable Long companyId,
                                         @Valid @RequestBody PayrollRunCreateRequest request) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        PayrollPeriod period = requireOwnedByCompany(queries.getPeriod(request.payrollPeriodId()), companyId);
        PayrollRun run = lifecycle.createRun(companyId, period.getId());
        return toResponse(queries.getRun(run.getId()));
    }

    @PostMapping("/runs/{id}/calculate")
    public PayrollRunResponse calculate(@PathVariable Long id) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        lifecycle.calculate(id);
        return toResponse(queries.getRun(id));
    }

    @PostMapping("/runs/{id}/submit-for-review")
    public PayrollRunResponse submitForReview(@PathVariable Long id) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        lifecycle.submitForReview(id);
        return toResponse(queries.getRun(id));
    }

    @PostMapping("/runs/{id}/return-to-draft")
    public PayrollRunResponse returnToDraft(@PathVariable Long id) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        lifecycle.returnToDraft(id);
        return toResponse(queries.getRun(id));
    }

    /** {@code payroll:approve}, no {@code payroll:run}: el maker-checker de la clase. */
    @PostMapping("/runs/{id}/approve")
    public PayrollRunResponse approve(@PathVariable Long id) {
        accessGuard.require("payroll:approve", PermissionScope.COMPANY);
        Long approvedByUserId = ActorContext.require().userId();
        lifecycle.approve(id, approvedByUserId);
        return toResponse(queries.getRun(id));
    }

    @PostMapping("/runs/{id}/mark-paid")
    public PayrollRunResponse markPaid(@PathVariable Long id) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        lifecycle.markPaid(id);
        return toResponse(queries.getRun(id));
    }

    @PostMapping("/runs/{id}/close")
    public PayrollRunResponse close(@PathVariable Long id) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        lifecycle.close(id);
        return toResponse(queries.getRun(id));
    }

    @PostMapping("/runs/{id}/cancel")
    public PayrollRunResponse cancel(@PathVariable Long id) {
        accessGuard.require("payroll:run", PermissionScope.COMPANY);
        lifecycle.cancel(id);
        return toResponse(queries.getRun(id));
    }

    /** {@code payroll:reverse}, no {@code payroll:run}: mismo maker-checker que {@link #approve(Long)}. Devuelve la corrida NUEVA, no la original (AD-03: la original nunca se toca). */
    @PostMapping("/runs/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollRunResponse reverse(@PathVariable Long id) {
        accessGuard.require("payroll:reverse", PermissionScope.COMPANY);
        PayrollRun reversal = lifecycle.reverse(id);
        return toResponse(queries.getRun(reversal.getId()));
    }

    // --- volantes ------------------------------------------------------------------

    @GetMapping("/runs/{runId}/payslips")
    public List<PayslipAdminSummaryResponse> listPayslips(@PathVariable Long runId) {
        accessGuard.require("payslip:read", PermissionScope.COMPANY);
        return queries.listPayslips(runId).stream().map(PayrollAdminController::toResponse).toList();
    }

    @GetMapping("/runs/{runId}/payslips/{payslipId}")
    public PayslipAdminDetailResponse payslipDetail(@PathVariable Long runId, @PathVariable Long payslipId) {
        accessGuard.require("payslip:read", PermissionScope.COMPANY);
        Payslip payslip = queries.getPayslip(runId, payslipId);
        List<PayslipLine> lines = queries.getPayslipLines(payslipId);
        return toDetailResponse(payslip, lines);
    }

    /** IDs sueltos en el cuerpo: nada impide que un período apunte a otra empresa del mismo tenant sin esta comprobación explícita — mismo guardia que {@code OrgAdminController.requireOwnedByCompany}. */
    private static PayrollPeriod requireOwnedByCompany(PayrollPeriod period, Long companyId) {
        if (!period.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException(
                    "El periodo " + period.getId() + " no pertenece a la empresa " + companyId);
        }
        return period;
    }

    private static PayrollPeriodResponse toResponse(PayrollPeriod period) {
        return new PayrollPeriodResponse(period.getId(), period.getCompanyId(), period.getName(),
                period.getFrequency().name(), period.getStartDate(), period.getEndDate(), period.getPayDate());
    }

    private static PayrollRunResponse toResponse(PayrollRun run) {
        PayrollPeriod period = run.getPayrollPeriod();
        return new PayrollRunResponse(run.getId(), run.getCompanyId(),
                period.getId(), period.getName(), period.getFrequency().name(),
                period.getStartDate(), period.getEndDate(), period.getPayDate(),
                run.getStatus().name(), run.getConfigVersionId(), run.getReversalOfRunId(),
                run.getCreatedAt(), run.getCalculatedAt(), run.getApprovedAt(), run.getApprovedByUserId(),
                run.getPaidAt(), run.getClosedAt());
    }

    private static PayslipAdminSummaryResponse toResponse(Payslip payslip) {
        return new PayslipAdminSummaryResponse(payslip.getId(), payslip.getEmployeeId(), payslip.getContractId(),
                payslip.getCostCenterId(), payslip.getGrossPay(), payslip.getNetPay(), payslip.getCurrency());
    }

    private static PayslipAdminDetailResponse toDetailResponse(Payslip payslip, List<PayslipLine> lines) {
        List<PayslipLineView> earnings = lines.stream()
                .filter(line -> line.getType() == PayrollConceptType.EARNING)
                .map(line -> new PayslipLineView(line.getDescription(), line.getAmount()))
                .toList();
        // Deducciones en magnitud positiva, mismo motivo que payroll.api.PayslipDetailView:
        // el signo negativo de PayslipLine.amount existe para que sumar todas las
        // líneas dé el neto sin lógica condicional, no para mostrarlo dos veces.
        List<PayslipLineView> deductions = lines.stream()
                .filter(line -> line.getType() == PayrollConceptType.DEDUCTION)
                .map(line -> new PayslipLineView(line.getDescription(), line.getAmount().abs()))
                .toList();
        BigDecimal totalDeductions = deductions.stream()
                .map(PayslipLineView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PayslipAdminDetailResponse(payslip.getId(), payslip.getEmployeeId(), payslip.getContractId(),
                payslip.getCostCenterId(), payslip.getGrossPay(), payslip.getNetPay(), payslip.getCurrency(),
                earnings, deductions, totalDeductions);
    }
}
