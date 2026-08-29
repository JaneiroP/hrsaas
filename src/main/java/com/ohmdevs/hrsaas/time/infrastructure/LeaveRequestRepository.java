package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.LeaveRequest;
import com.ohmdevs.hrsaas.time.domain.LeaveRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeId(Long employeeId);

    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, LeaveRequestStatus status);

    /** Solicitudes aprobadas que solapan con un período — lo que necesita {@code payroll}. */
    List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveRequestStatus status, LocalDate periodEnd, LocalDate periodStart);
}
