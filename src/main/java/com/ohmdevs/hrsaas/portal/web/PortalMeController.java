package com.ohmdevs.hrsaas.portal.web;

import com.ohmdevs.hrsaas.portal.application.EmployeeProfileResponse;
import com.ohmdevs.hrsaas.portal.application.EmployeeProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /me} — perfil, contrato vigente y supervisor del actor autenticado (blueprint §09). */
@RestController
@RequestMapping("/api/v1/me")
public class PortalMeController {

    private final EmployeeProfileService employeeProfileService;

    public PortalMeController(EmployeeProfileService employeeProfileService) {
        this.employeeProfileService = employeeProfileService;
    }

    @GetMapping
    public EmployeeProfileResponse me() {
        return employeeProfileService.currentProfile();
    }
}
