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

/**
 * Cuenta de acceso. Es <strong>global</strong>, no pertenece a un tenant: la
 * misma persona puede ser contadora externa de tres empresas distintas y entrar
 * con un solo correo. La pertenencia vive en {@link UserTenant}.
 *
 * <p>Un {@code User} tampoco es un empleado. La mayoría de los empleados de un
 * cliente nunca tendrán login, y algunos usuarios no serán empleados. El enlace,
 * cuando exista, lo guarda el módulo {@code people} apuntando hacia aquí.
 *
 * <p>{@code passwordHash} es nullable a propósito: un usuario invitado existe
 * antes de fijar contraseña, y si más adelante se delega la identidad a un
 * proveedor externo la columna deja de usarse sin migrar nada.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Siempre normalizado a minúsculas antes de persistir. */
    @Column(nullable = false, length = 160)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean canAuthenticate() {
        return enabled && !locked && passwordHash != null;
    }
}
