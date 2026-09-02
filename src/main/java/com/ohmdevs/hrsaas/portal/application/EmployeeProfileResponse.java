package com.ohmdevs.hrsaas.portal.application;

import java.time.LocalDate;
import java.util.List;

/** Forma exacta de {@code GET /me} acordada con el frontend ({@code core/models/employee.model.ts:EmployeeProfile}). */
public record EmployeeProfileResponse(
        String id,
        String fullName,
        String initials,
        String position,
        String department,
        String location,
        String supervisorName,
        String email,
        LocalDate hireDateIso,
        String contractType,
        String companyName,
        String companyRnc,
        List<String> scopes) {
}
