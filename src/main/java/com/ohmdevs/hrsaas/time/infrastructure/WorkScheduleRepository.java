package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {

    List<WorkSchedule> findByCompanyId(Long companyId);
}
