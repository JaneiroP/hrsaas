package com.ohmdevs.hrsaas.platform.repository;

import com.ohmdevs.hrsaas.platform.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Catálogo global; no se filtra por tenant. */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByCodeIn(List<String> codes);
}
