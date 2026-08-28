package com.ohmdevs.hrsaas.platform.repository;

import com.ohmdevs.hrsaas.platform.domain.UserTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserTenantRepository extends JpaRepository<UserTenant, Long> {

    /** Filtrado al tenant activo por {@code @TenantId}; no hace falta pasarlo. */
    Optional<UserTenant> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Todas las pertenencias de un usuario, <strong>en todos los tenants</strong>.
     *
     * <p>Es la excepción deliberada al filtro: al iniciar sesión hay que saber a
     * qué tenants puede entrar antes de tener uno activo. La consulta es nativa
     * justamente para escapar de {@code @TenantId} — y por eso RLS existe: aquí
     * la política de PostgreSQL sí seguiría aplicando si la conexión llevara un
     * tenant fijado, de modo que esto solo devuelve algo cuando no lo hay.
     */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT tenant_id FROM user_tenants WHERE user_id = :userId AND status = 'ACTIVE'",
            nativeQuery = true)
    List<Long> findActiveTenantIdsForUser(@org.springframework.data.repository.query.Param("userId") Long userId);
}
