package com.ohmdevs.hrsaas.platform.repository;

import com.ohmdevs.hrsaas.platform.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Los tenants son globales: esta tabla no lleva RLS ni {@code @TenantId}, y no
 * podría llevarlos. Es la que resuelve el tenant <em>antes</em> de que exista
 * contexto — al aceptar una invitación, por ejemplo.
 */
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
