package com.ohmdevs.hrsaas.platform.repository;

import com.ohmdevs.hrsaas.platform.access.GrantedPermission;
import com.ohmdevs.hrsaas.platform.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * Todos los permisos concedidos a un usuario vía sus roles, con el alcance
     * de cada concesión.
     *
     * <p>Devuelve filas sin consolidar: un usuario con dos roles que otorgan el
     * mismo permiso aparece dos veces, con alcances posiblemente distintos.
     * Quedarse con el más amplio es trabajo de {@code ActorLoader} — la base de
     * datos no conoce el orden de contención de {@code PermissionScope}.
     *
     * <p>El filtro por tenant lo pone {@code @TenantId} sobre {@code UserRole},
     * {@code Role} y {@code RolePermission}. No aparece en el JPQL, y es correcto
     * que no aparezca: el día que alguien lo escriba a mano, será porque olvidó
     * que existe.
     */
    @Query("""
            select new com.ohmdevs.hrsaas.platform.access.GrantedPermission(p.code, rp.scope)
            from UserRole ur
            join ur.role r
            join RolePermission rp on rp.role = r
            join rp.permission p
            where ur.user.id = :userId
              and r.active = true
            """)
    List<GrantedPermission> findGrantedPermissions(@Param("userId") Long userId);
}
