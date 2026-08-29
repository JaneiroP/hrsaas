package com.ohmdevs.hrsaas.people.infrastructure;

import com.ohmdevs.hrsaas.people.domain.Dependent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependentRepository extends JpaRepository<Dependent, Long> {

    List<Dependent> findByEmployeeId(Long employeeId);
}
