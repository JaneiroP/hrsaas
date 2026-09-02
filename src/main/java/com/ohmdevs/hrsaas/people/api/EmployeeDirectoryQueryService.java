package com.ohmdevs.hrsaas.people.api;

import com.ohmdevs.hrsaas.people.domain.DocumentType;
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
                maskDocumentNumber(employee.getDocumentType(), employee.getDocumentNumber()));
    }

    /**
     * Para una cédula dominicana bien formada ({@code NNN-NNNNNNN-N}) conserva
     * el código de provincia y el dígito verificador, que es exactamente el
     * enmascarado que ya espera el frontend del portal (p. ej. {@code
     * 402-XXXXXXX-1}). Cualquier otro formato (pasaporte, o una cédula que no
     * calce con el patrón) cae a un enmascarado genérico: primeros y últimos
     * dos caracteres visibles, el resto sustituido por {@code X}.
     */
    private static String maskDocumentNumber(DocumentType type, String plain) {
        if (type == DocumentType.CEDULA && plain.matches("\\d{3}-\\d{7}-\\d")) {
            return plain.substring(0, 4) + "X".repeat(7) + plain.substring(11);
        }
        int visible = 2;
        if (plain.length() <= visible * 2) {
            return "X".repeat(plain.length());
        }
        return plain.substring(0, visible) + "X".repeat(plain.length() - visible * 2) + plain.substring(plain.length() - visible);
    }
}
