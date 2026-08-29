package com.ohmdevs.hrsaas.assets.infrastructure;

import com.ohmdevs.hrsaas.assets.domain.AssetAssignment;
import com.ohmdevs.hrsaas.assets.domain.AssetAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {

    List<AssetAssignment> findByEmployeeId(Long employeeId);

    /** Checklist de devolución: equipos que un empleado todavía tiene asignados. */
    List<AssetAssignment> findByEmployeeIdAndStatus(Long employeeId, AssetAssignmentStatus status);

    List<AssetAssignment> findByAssetId(Long assetId);
}
