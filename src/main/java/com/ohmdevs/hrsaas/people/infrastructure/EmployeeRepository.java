package com.ohmdevs.hrsaas.people.infrastructure;

import com.ohmdevs.hrsaas.people.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Búsqueda por índice ciego, nunca por el número en claro (ver {@code Employee.documentNumberHmac}). */
    Optional<Employee> findByDocumentNumberHmac(String documentNumberHmac);

    Optional<Employee> findByUserId(Long userId);
}
