package com.ohmdevs.hrsaas.portal.web;

import com.ohmdevs.hrsaas.portal.application.PayslipDetailResponse;
import com.ohmdevs.hrsaas.portal.application.PayslipPortalService;
import com.ohmdevs.hrsaas.portal.application.PayslipSummaryResponse;
import com.ohmdevs.hrsaas.shared.web.CursorPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /me/payslips[/{id}]} — histórico y desglose de volantes del actor autenticado (blueprint §09). */
@RestController
@RequestMapping("/api/v1/me/payslips")
public class PortalPayslipController {

    private final PayslipPortalService payslipPortalService;

    public PortalPayslipController(PayslipPortalService payslipPortalService) {
        this.payslipPortalService = payslipPortalService;
    }

    @GetMapping
    public CursorPage<PayslipSummaryResponse> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit) {
        return payslipPortalService.list(cursor, limit);
    }

    @GetMapping("/{id}")
    public PayslipDetailResponse detail(@PathVariable String id) {
        return payslipPortalService.detail(id);
    }
}
