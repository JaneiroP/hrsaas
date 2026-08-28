package com.ohmdevs.hrsaas.platform.domain;

import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Permiso del catálogo global, con código {@code <recurso>:<acción>}.
 *
 * <p>No es configurable por tenant: lo que cada tenant configura es qué rol
 * recibe cuál permiso y con qué alcance. Si los permisos fueran por tenant, cada
 * módulo nuevo obligaría a tocar los datos de todos los clientes.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "module_code", nullable = false, length = 32)
    private String moduleCode;

    @Column(nullable = false, length = 200)
    private String description;

    /**
     * Techo del alcance concedible. Impide que {@code leave:request}, que solo
     * tiene sentido sobre uno mismo, se conceda a nivel COMPANY por descuido en
     * la API de administración.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "max_scope", nullable = false, length = 16)
    private PermissionScope maxScope = PermissionScope.ALL;
}
