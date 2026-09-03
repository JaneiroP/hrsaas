package com.ohmdevs.hrsaas.people.api;

import com.ohmdevs.hrsaas.people.application.DocumentNumberMasking;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Tercer contenido público de {@code people}: proyecciones de solo lectura
 * para {@code portal} — el puente {@code platform.User} &rarr;
 * {@code people.Employee} ({@link #findByUserId}) y la ficha resumida de un
 * empleado por su propio id ({@link #findById}, para resolver, por ejemplo,
 * el nombre de un supervisor).
 */
@Service
public class EmployeeDirectoryQueryService {

    private final EmployeeRepository employeeRepository;

    public EmployeeDirectoryQueryService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public Optional<EmployeeDirectorySummary> findByUserId(Long userId) {
        return employeeRepository.findByUserId(userId).map(EmployeeDirectoryQueryService::toSummary);
    }

    @Transactional
    public Optional<EmployeeDirectorySummary> findById(Long employeeId) {
        return employeeRepository.findById(employeeId).map(EmployeeDirectoryQueryService::toSummary);
    }

    private static EmployeeDirectorySummary toSummary(Employee employee) {
        return new EmployeeDirectorySummary(
                employee.getId(), employee.getFirstName(), employee.getLastName(), employee.getEmail(),
                DocumentNumberMasking.mask(employee.getDocumentType(), employee.getDocumentNumber()));
    }
}
