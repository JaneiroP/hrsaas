package com.ohmdevs.hrsaas.integrations.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import com.ohmdevs.hrsaas.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Extremo por el que un sistema externo de asistencia entrega marcajes a un
 * tenant. Es lo que da sentido a una petición que llega sin usuario: dice de
 * qué tenant es y con qué secreto debe venir firmada.
 */
@Entity
@Table(name = "attendance_connectors")
@Getter
@Setter
public class AttendanceConnector extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** El identificador que sale de aquí: va en la URL del webhook y en la API. El {@code id} numérico nunca. */
    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, length = 160)
    private String name;

    /**
     * Secreto con el que el sistema externo firma cada petición. Lo genera
     * ese sistema, no éste, y por eso puede faltar: el conector existe antes
     * de que el otro lado tenga a dónde enviar.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "signing_secret", length = 255)
    private String signingSecret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_received_at")
    private Instant lastReceivedAt;

    /** Sin secreto no hay con qué comprobar una firma, así que no se acepta nada. */
    public boolean canReceive() {
        return signingSecret != null;
    }
}
