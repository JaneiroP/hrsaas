package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByCode(String code);

    List<LeaveType> findByActiveTrue();
}
