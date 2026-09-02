package com.ohmdevs.hrsaas.portal.application;

import com.ohmdevs.hrsaas.contracts.api.EmployeeEmploymentQueryService;
import com.ohmdevs.hrsaas.contracts.api.EmployeeEmploymentSummary;
import com.ohmdevs.hrsaas.org.api.CompanyDirectorySummary;
import com.ohmdevs.hrsaas.org.api.OrgDirectoryQueryService;
import com.ohmdevs.hrsaas.org.api.PositionDirectorySummary;
import com.ohmdevs.hrsaas.people.api.EmployeeDirectorySummary;
import com.ohmdevs.hrsaas.platform.access.ActorContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ensambla {@code GET /me} a partir de tres módulos distintos —
 * {@code people} (quién es), {@code contracts} (su vínculo vigente) y
 * {@code org} (nombres de empresa y cargo) — sin que ninguno de ellos sepa
 * de los otros dos. Es exactamente el trabajo que blueprint §03 le asigna a
 * {@code portal}: "cero lógica de negocio", solo composición de lectura.
 *
 * <p><strong>No pasa por {@code AccessGuard}.</strong> Ver el propósito del
 * paquete: el perfil siempre es del propio actor, nunca de un id ajeno, así
 * que no hay alcance que decidir — cualquier cuenta autenticada con un
 * expediente de empleado vinculado puede ver su propio perfil. Los demás
 * endpoints del portal ({@code payslip:read}, {@code leave:read}/{@code
 * :request}) sí exigen un permiso concreto porque tienen alcance SELF sobre
 * datos que también existen en vistas COMPANY/TEAM para otros roles.
 */
@Service
public class EmployeeProfileService {

    /**
     * Traducción a español para presentación (blueprint §04 solo prohíbe
     * tasas y escalas fiscales en código — esto es vocabulario de UI, no una
     * regla laboral). Un tipo de contrato sin traducción cae a su propio
     * nombre en vez de romper la respuesta.
     */
    private static final Map<String, String> CONTRACT_TYPE_LABELS = Map.of(
            "INDEFINITE", "Indefinido",
            "FIXED_TERM", "Tiempo determinado",
            "PROJECT_BASED", "Por obra",
            "INTERNSHIP", "Pasantía");

    private final PortalEmployeeResolver employeeResolver;
    private final EmployeeEmploymentQueryService employmentQueryService;
    private final OrgDirectoryQueryService orgDirectoryQueryService;

    public EmployeeProfileService(PortalEmployeeResolver employeeResolver,
                                   EmployeeEmploymentQueryService employmentQueryService,
                                   OrgDirectoryQueryService orgDirectoryQueryService) {
        this.employeeResolver = employeeResolver;
        this.employmentQueryService = employmentQueryService;
        this.orgDirectoryQueryService = orgDirectoryQueryService;
    }

    @Transactional
    public EmployeeProfileResponse currentProfile() {
        Long userId = ActorContext.require().userId();
        EmployeeDirectorySummary employee = employeeResolver.resolve(userId);

        EmployeeEmploymentSummary employment = employmentQueryService.activeEmploymentFor(employee.employeeId())
                .orElseThrow(() -> new IllegalStateException(
                        "El empleado " + employee.employeeId() + " no tiene un contrato vigente"));

        PositionDirectorySummary position = orgDirectoryQueryService.findPosition(employment.positionId())
                .orElseThrow(() -> new IllegalStateException("Cargo " + employment.positionId() + " no existe"));
        CompanyDirectorySummary company = orgDirectoryQueryService.findCompany(employment.companyId())
                .orElseThrow(() -> new IllegalStateException("Empresa " + employment.companyId() + " no existe"));

        // "" y no null: el campo del contrato del frontend es un string no anulable
        // (no todo el mundo tiene supervisor — el dueño de la empresa, por ejemplo).
        String supervisorName = employment.supervisorEmployeeId() == null
                ? ""
                : employeeResolver.resolveById(employment.supervisorEmployeeId())
                        .map(EmployeeDirectorySummary::fullName)
                        .orElse("");

        return new EmployeeProfileResponse(
                employee.employeeId().toString(),
                employee.fullName(),
                initialsOf(employee),
                position.title(),
                position.departmentName(),
                // Sin Location asociada al contrato en el modelo actual (ver package-info
                // de portal): pendiente hasta que contracts modele la sucursal del empleado.
                "",
                supervisorName,
                employee.email(),
                employment.startDate(),
                CONTRACT_TYPE_LABELS.getOrDefault(employment.contractType(), employment.contractType()),
                company.legalName(),
                company.rnc(),
                List.of("SELF"));
    }

    private static String initialsOf(EmployeeDirectorySummary employee) {
        String first = initialOf(employee.firstName());
        String last = initialOf(employee.lastName());
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private static String initialOf(String name) {
        return (name == null || name.isBlank()) ? "" : name.strip().substring(0, 1);
    }
}
