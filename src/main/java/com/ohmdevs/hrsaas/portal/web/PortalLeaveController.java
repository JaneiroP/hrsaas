package com.ohmdevs.hrsaas.portal.web;

import com.ohmdevs.hrsaas.portal.application.LeaveBalanceResponse;
import com.ohmdevs.hrsaas.portal.application.LeavePortalService;
import com.ohmdevs.hrsaas.portal.application.LeaveRequestResponse;
import com.ohmdevs.hrsaas.portal.application.NewLeaveRequestCommand;
import com.ohmdevs.hrsaas.shared.web.CursorPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /me/leave-balances} y {@code /me/leave-requests} — saldos,
 * historial, solicitud y cancelación de licencias del actor autenticado
 * (blueprint §09).
 */
@RestController
@RequestMapping("/api/v1/me")
public class PortalLeaveController {

    private final LeavePortalService leavePortalService;

    public PortalLeaveController(LeavePortalService leavePortalService) {
        this.leavePortalService = leavePortalService;
    }

    @GetMapping("/leave-balances")
    public List<LeaveBalanceResponse> balances() {
        return leavePortalService.balances();
    }

    @GetMapping("/leave-requests")
    public CursorPage<LeaveRequestResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        return leavePortalService.list(cursor, limit);
    }

    @PostMapping("/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestResponse create(@Valid @RequestBody NewLeaveRequestCommand command) {
        return leavePortalService.create(command);
    }

    /** Cancelar mientras esté pendiente (blueprint §09) — el estado se valida en {@code LeavePortalService}, no aquí. */
    @DeleteMapping("/leave-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id) {
        leavePortalService.cancel(id);
    }
}
