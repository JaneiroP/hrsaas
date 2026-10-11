package com.ohmdevs.hrsaas.integrations.infrastructure;

import com.ohmdevs.hrsaas.integrations.domain.AttendanceConnector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceConnectorRepository extends JpaRepository<AttendanceConnector, Long> {

    Optional<AttendanceConnector> findByPublicId(UUID publicId);

    List<AttendanceConnector> findAllByOrderByCreatedAtAsc();
}
