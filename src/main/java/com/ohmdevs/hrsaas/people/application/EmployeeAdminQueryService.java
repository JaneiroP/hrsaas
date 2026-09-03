package com.ohmdevs.hrsaas.people.application;

import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.domain.EmployeeStatus;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lecturas administrativas de {@link Employee} para {@code people.web.EmployeeAdminController}.
 * Mismo criterio de separación que {@code org.application.OrgAdminQueryService}:
 * {@link EmployeeRecordService} es alta, este es consulta.
 */
@Service
public class EmployeeAdminQueryService {

    private final EmployeeRepository employeeRepository;

    public EmployeeAdminQueryService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** Página de tamaño {@code items.size() <= limit}; {@code nextAfterId} nulo significa que no hay página siguiente — mismo patrón de {@code payroll.api.PayslipPage}. */
    public record EmployeePage(List<Employee> items, Long nextAfterId) {
    }

    /**
     * Trae {@code limit + 1} filas para decidir si hay página siguiente sin
     * una segunda consulta ({@code COUNT}), mismo truco que
     * {@code PayslipSelfServiceQueryService.payslipsFor}.
     */
    @Transactional
    public EmployeePage search(EmployeeStatus status, String query, Long afterId, int limit) {
        String likePattern = (query == null || query.isBlank()) ? null : "%" + query.toLowerCase() + "%";
        Pageable page = PageRequest.of(0, limit + 1);
        List<Employee> rows = employeeRepository.search(status, likePattern, afterId, page);

        boolean hasMore = rows.size() > limit;
        List<Employee> pageRows = hasMore ? rows.subList(0, limit) : rows;
        Long nextAfterId = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new EmployeePage(pageRows, nextAfterId);
    }

    @Transactional
    public Employee getById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new NoSuchElementException("Empleado " + employeeId + " no encontrado"));
    }
}
