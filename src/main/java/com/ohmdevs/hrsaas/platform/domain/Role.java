package com.ohmdevs.hrsaas.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Rol dentro de un tenant. "Supervisor" del tenant A no es el del tenant B. */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 200)
    private String description;

    /** Creado en el aprovisionamiento. No se borra ni se renombra desde la API. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
