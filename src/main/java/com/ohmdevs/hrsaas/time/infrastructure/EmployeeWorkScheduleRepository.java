package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.EmployeeWorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeWorkScheduleRepository extends JpaRepository<EmployeeWorkSchedule, Long> {

    /** El índice único parcial de la migración garantiza a lo sumo una vigente por empleado. */
    Optional<EmployeeWorkSchedule> findByEmployeeIdAndEffectiveToIsNull(Long employeeId);
}
