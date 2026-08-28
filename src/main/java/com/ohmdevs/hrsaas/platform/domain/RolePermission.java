package com.ohmdevs.hrsaas.platform.domain;

import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Concesión de un permiso a un rol, <strong>con su alcance</strong>.
 *
 * <p>Ésta es la pieza central del diseño de autorización. El alcance vive en la
 * concesión y no en el rol, porque un mismo rol necesita alcances distintos
 * según el recurso: un supervisor lee los expedientes de su equipo (TEAM) pero
 * solo su propio volante de pago (SELF). Poner el alcance en el rol obligaría a
 * inventar un rol por combinación.
 */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
public class RolePermission extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PermissionScope scope;
}
