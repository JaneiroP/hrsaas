package com.ohmdevs.hrsaas.portal.application;

import com.ohmdevs.hrsaas.contracts.api.EmployeeEmploymentQueryService;
import com.ohmdevs.hrsaas.time.api.LeaveBalanceView;
import com.ohmdevs.hrsaas.time.api.LeaveRequestNotFoundException;
import com.ohmdevs.hrsaas.time.api.LeaveRequestPage;
import com.ohmdevs.hrsaas.time.api.LeaveRequestView;
import com.ohmdevs.hrsaas.time.api.LeaveSelfServiceQueryService;
import com.ohmdevs.hrsaas.platform.access.AccessGuard;
import com.ohmdevs.hrsaas.platform.access.ActorContext;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import com.ohmdevs.hrsaas.shared.web.CursorCodec;
import com.ohmdevs.hrsaas.shared.web.CursorPage;
import com.ohmdevs.hrsaas.shared.web.PageLimits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Orquesta {@code time.api.LeaveSelfServiceQueryService} para las cuatro rutas de {@code /me/leave-*}. */
@Service
public class LeavePortalService {

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "PENDING", "PENDIENTE",
            "APPROVED", "APROBADA",
            "REJECTED", "RECHAZADA",
            "CANCELLED", "CANCELADA");

    private final PortalEmployeeResolver employeeResolver;
    private final EmployeeEmploymentQueryService employmentQueryService;
    private final LeaveSelfServiceQueryService leaveSelfServiceQueryService;
    private final AccessGuard accessGuard;

    public LeavePortalService(PortalEmployeeResolver employeeResolver,
                               EmployeeEmploymentQueryService employmentQueryService,
                               LeaveSelfServiceQueryService leaveSelfServiceQueryService,
                               AccessGuard accessGuard) {
        this.employeeResolver = employeeResolver;
        this.employmentQueryService = employmentQueryService;
        this.leaveSelfServiceQueryService = leaveSelfServiceQueryService;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public List<LeaveBalanceResponse> balances() {
        accessGuard.require("leave:read", PermissionScope.SELF);
        Long employeeId = employeeResolver.resolve(ActorContext.require().userId()).employeeId();
        return leaveSelfServiceQueryService.balancesFor(employeeId).stream()
                .map(LeavePortalService::toBalanceResponse)
                .toList();
    }

    @Transactional
    public CursorPage<LeaveRequestResponse> list(String cursor, int limit) {
        accessGuard.require("leave:read", PermissionScope.SELF);
        Long employeeId = employeeResolver.resolve(ActorContext.require().userId()).employeeId();

        Long afterId = CursorCodec.decode(cursor);
        LeaveRequestPage page = leaveSelfServiceQueryService.requestsFor(employeeId, afterId, PageLimits.clamp(limit));

        var items = page.items().stream().map(LeavePortalService::toResponse).toList();
        String nextCursor = page.nextAfterId() == null ? null : CursorCodec.encode(page.nextAfterId());
        return new CursorPage<>(items, nextCursor);
    }

    @Transactional
    public LeaveRequestResponse create(NewLeaveRequestCommand command) {
        accessGuard.require("leave:request", PermissionScope.SELF);
        Long userId = ActorContext.require().userId();
        Long employeeId = employeeResolver.resolve(userId).employeeId();
        Long companyId = employmentQueryService.activeEmploymentFor(employeeId)
                .orElseThrow(() -> new IllegalStateException("El empleado " + employeeId + " no tiene un contrato vigente"))
                .companyId();

        LeaveRequestView created = leaveSelfServiceQueryService.submitRequest(
                employeeId, companyId, command.type(), command.startDateIso(), command.endDateIso(), command.reason());
        return toResponse(created);
    }

    @Transactional
    public void cancel(String requestId) {
        accessGuard.require("leave:request", PermissionScope.SELF);
        Long employeeId = employeeResolver.resolve(ActorContext.require().userId()).employeeId();
        leaveSelfServiceQueryService.cancelOwnRequest(employeeId, parseId(requestId));
    }

    private static Long parseId(String rawId) {
        try {
            return Long.valueOf(rawId);
        } catch (NumberFormatException e) {
            throw new LeaveRequestNotFoundException(rawId);
        }
    }

    private static LeaveBalanceResponse toBalanceResponse(LeaveBalanceView view) {
        return new LeaveBalanceResponse(view.leaveTypeCode(), view.label(), view.totalDays(), view.takenDays(),
                view.availableDays(), view.note());
    }

    private static LeaveRequestResponse toResponse(LeaveRequestView view) {
        return new LeaveRequestResponse(
                view.requestId().toString(), view.leaveTypeCode(), view.leaveTypeLabel(),
                view.startDate(), view.endDate(), view.days(), statusLabel(view.status()), view.reason(),
                view.requestedAt().atZone(ZoneOffset.UTC).toLocalDate());
    }

    private static String statusLabel(String rawStatus) {
        return STATUS_LABELS.getOrDefault(rawStatus, rawStatus.toUpperCase(Locale.ROOT));
    }
}
