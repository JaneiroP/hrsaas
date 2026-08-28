package com.ohmdevs.hrsaas.platform.repository;

import com.ohmdevs.hrsaas.platform.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleId(Long roleId);

    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);
}
