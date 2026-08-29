package com.ohmdevs.hrsaas.people.infrastructure;

import com.ohmdevs.hrsaas.people.domain.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    List<EmployeeDocument> findByEmployeeId(Long employeeId);
}
