package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByCode(String code);

    /** Catálogo de saldos del portal (blueprint §09: {@code GET /me/leave-balances}) — un tenant no borra tipos, los desactiva. */
    List<LeaveType> findByActiveTrue();
}
