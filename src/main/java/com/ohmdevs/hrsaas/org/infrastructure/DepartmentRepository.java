package com.ohmdevs.hrsaas.org.infrastructure;

import com.ohmdevs.hrsaas.org.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByCompanyId(Long companyId);
}
