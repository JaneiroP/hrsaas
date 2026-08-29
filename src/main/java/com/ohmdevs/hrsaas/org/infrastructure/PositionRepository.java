package com.ohmdevs.hrsaas.org.infrastructure;

import com.ohmdevs.hrsaas.org.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findByCompanyId(Long companyId);

    List<Position> findByDepartmentId(Long departmentId);
}
