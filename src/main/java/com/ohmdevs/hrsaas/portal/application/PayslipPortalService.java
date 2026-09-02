package com.ohmdevs.hrsaas.portal.application;

import com.ohmdevs.hrsaas.org.api.OrgDirectoryQueryService;
import com.ohmdevs.hrsaas.payroll.api.PayslipDetailView;
import com.ohmdevs.hrsaas.payroll.api.PayslipLineView;
import com.ohmdevs.hrsaas.payroll.api.PayslipPage;
import com.ohmdevs.hrsaas.payroll.api.PayslipSelfServiceQueryService;
import com.ohmdevs.hrsaas.payroll.api.PayslipSummaryView;
import com.ohmdevs.hrsaas.people.api.EmployeeDirectorySummary;
import com.ohmdevs.hrsaas.platform.access.AccessGuard;
import com.ohmdevs.hrsaas.platform.access.ActorContext;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.shared.web.CursorCodec;
import com.ohmdevs.hrsaas.shared.web.CursorPage;
import com.ohmdevs.hrsaas.shared.web.PageLimits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;

/** Orquesta {@code payroll.api.PayslipSelfServiceQueryService} para {@code GET /me/payslips[/{id}]}. */
@Service
public class PayslipPortalService {

    /** Cualquier otro estado de la corrida se muestra como "en proceso" — ver Javadoc de {@link #toStatusLabel}. */
    private static final Set<String> PAID_RUN_STATUSES = Set.of("PAGADA", "CERRADA");

    private final PortalEmployeeResolver employeeResolver;
    private final PayslipSelfServiceQueryService payslipSelfServiceQueryService;
    private final OrgDirectoryQueryService orgDirectoryQueryService;
    private final AccessGuard accessGuard;

    public PayslipPortalService(PortalEmployeeResolver employeeResolver,
                                 PayslipSelfServiceQueryService payslipSelfServiceQueryService,
                                 OrgDirectoryQueryService orgDirectoryQueryService,
                                 AccessGuard accessGuard) {
        this.employeeResolver = employeeResolver;
        this.payslipSelfServiceQueryService = payslipSelfServiceQueryService;
        this.orgDirectoryQueryService = orgDirectoryQueryService;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public CursorPage<PayslipSummaryResponse> list(String cursor, int limit) {
        accessGuard.require("payslip:read", PermissionScope.SELF);
        Long employeeId = employeeResolver.resolve(ActorContext.require().userId()).employeeId();

        Long afterId = CursorCodec.decode(cursor);
        PayslipPage page = payslipSelfServiceQueryService.payslipsFor(employeeId, afterId, PageLimits.clamp(limit));

        var items = page.items().stream().map(PayslipPortalService::toSummary).toList();
        String nextCursor = page.nextAfterId() == null ? null : CursorCodec.encode(page.nextAfterId());
        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public PayslipDetailResponse detail(String payslipId) {
        accessGuard.require("payslip:read", PermissionScope.SELF);
        EmployeeDirectorySummary employee = employeeResolver.resolve(ActorContext.require().userId());

        Long id = parseId(payslipId);
        PayslipDetailView view = payslipSelfServiceQueryService.payslipDetailFor(employee.employeeId(), id)
                .orElseThrow(() -> new NoSuchElementException("Volante " + payslipId + " no encontrado"));

        String companyName = orgDirectoryQueryService.findCompany(view.companyId())
                .map(company -> company.tradeName() != null ? company.tradeName() : company.legalName())
                .orElse("");
        return toDetail(view, companyName, employee.maskedDocumentNumber());
    }

    private static Long parseId(String rawId) {
        try {
            return Long.valueOf(rawId);
        } catch (NumberFormatException e) {
            // Mismo id "no encontrado" que un id inexistente, no un 400: para quien
            // consulta un id ajeno o inventado, la forma del error no debe delatar
            // si el problema es "no es tuyo" o "ni siquiera es un id válido".
            throw new NoSuchElementException("Volante " + rawId + " no encontrado");
        }
    }

    private static PayslipSummaryResponse toSummary(PayslipSummaryView view) {
        return new PayslipSummaryResponse(
                view.payslipId().toString(), view.periodName(), view.payDate(),
                toStatusLabel(view.runStatus()), view.grossPay(), view.netPay());
    }

    private static PayslipDetailResponse toDetail(PayslipDetailView view, String companyName, String maskedNationalId) {
        return new PayslipDetailResponse(
                view.payslipId().toString(), view.periodName(), view.payDate(), toStatusLabel(view.runStatus()),
                view.grossPay(), view.netPay(),
                view.earnings().stream().map(PayslipPortalService::toLine).toList(),
                view.deductions().stream().map(PayslipPortalService::toLine).toList(),
                view.totalDeductions(),
                companyName, maskedNationalId);
    }

    private static PayslipLineResponse toLine(PayslipLineView line) {
        return new PayslipLineResponse(line.label(), line.amount());
    }

    /**
     * Traduce el estado crudo de {@code PayrollRun} al vocabulario binario que
     * espera hoy el frontend ({@code 'PAGADO' | 'PROCESANDO'}). Simplificación
     * deliberada: {@code ANULADA} también cae en "PROCESANDO" a falta de un
     * tercer estado en el contrato acordado — revisar si alguna vez un volante
     * de una corrida anulada llega a mostrarse aquí (hoy no debería, porque
     * {@code ANULADA} solo es alcanzable antes de aprobar, y un volante recién
     * creado en {@code BORRADOR} tampoco debería ser visible para el empleado
     * todavía — eso lo decide quién llama a {@code payroll.api}, no esta clase).
     */
    private static String toStatusLabel(String runStatus) {
        return PAID_RUN_STATUSES.contains(runStatus) ? "PAGADO" : "PROCESANDO";
    }
}
