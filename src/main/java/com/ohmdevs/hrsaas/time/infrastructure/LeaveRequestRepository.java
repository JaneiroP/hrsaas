package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.LeaveRequest;
import com.ohmdevs.hrsaas.time.domain.LeaveRequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeId(Long employeeId);

    List<LeaveRequest> findByEmployeeIdAndStatus(Long employeeId, LeaveRequestStatus status);

    /** Solicitudes aprobadas que solapan con un período — lo que necesita {@code payroll}. */
    List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveRequestStatus status, LocalDate periodEnd, LocalDate periodStart);

    /** Primera página del historial personal del portal — keyset por id, ver {@code shared.web.CursorCodec}. */
    List<LeaveRequest> findByEmployeeIdOrderByIdDesc(Long employeeId, Pageable pageable);

    /** Páginas siguientes: "lo que sigue después de este id", nunca un offset. */
    List<LeaveRequest> findByEmployeeIdAndIdLessThanOrderByIdDesc(Long employeeId, Long id, Pageable pageable);
}
