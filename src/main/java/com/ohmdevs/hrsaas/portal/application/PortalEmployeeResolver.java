package com.ohmdevs.hrsaas.portal.application;

import com.ohmdevs.hrsaas.people.api.EmployeeDirectoryQueryService;
import com.ohmdevs.hrsaas.people.api.EmployeeDirectorySummary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Traduce el {@code userId} del actor autenticado a su {@code employeeId} —
 * el único puente entre {@code platform.User} y {@code people.Employee}
 * (blueprint §03: son entidades distintas a propósito).
 *
 * <p>Todo servicio de {@code portal} pasa por aquí antes de tocar cualquier
 * otra {@code api}: es lo que garantiza que "SELF" signifique siempre el
 * empleado dueño de la sesión, nunca uno que llegue como parámetro.
 */
@Component
public class PortalEmployeeResolver {

    private final EmployeeDirectoryQueryService employeeDirectoryQueryService;

    public PortalEmployeeResolver(EmployeeDirectoryQueryService employeeDirectoryQueryService) {
        this.employeeDirectoryQueryService = employeeDirectoryQueryService;
    }

    public EmployeeDirectorySummary resolve(Long userId) {
        return employeeDirectoryQueryService.findByUserId(userId)
                .orElseThrow(() -> new NoEmployeeRecordException(userId));
    }

    /** Para resolver un id ajeno (p. ej. el supervisor de la cadena de mando) sin exigir que exista — es un dato accesorio del perfil, no la identidad del actor. */
    public Optional<EmployeeDirectorySummary> resolveById(Long employeeId) {
        return employeeDirectoryQueryService.findById(employeeId);
    }
}
