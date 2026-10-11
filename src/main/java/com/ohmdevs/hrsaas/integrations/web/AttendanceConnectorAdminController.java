package com.ohmdevs.hrsaas.integrations.web;

import com.ohmdevs.hrsaas.integrations.application.AttendanceConnectorService;
import com.ohmdevs.hrsaas.integrations.domain.AttendanceConnector;
import com.ohmdevs.hrsaas.platform.access.AccessGuard;
import com.ohmdevs.hrsaas.platform.access.PermissionScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API administrativa de los conectores de asistencia. Todo exige
 * {@code integration:manage} con alcance {@code ALL}: un conector escribe
 * marcajes de cualquier empleado del tenant, así que no tiene sentido
 * concederlo por equipo o por empresa. De los roles de sistema solo
 * {@code OWNER} lo tiene.
 *
 * <p>El secreto entra por aquí y no vuelve a salir: ninguna respuesta lo
 * incluye, solo si ya está registrado.
 */
@RestController
@RequestMapping("/api/v1/integrations/attendance-connectors")
public class AttendanceConnectorAdminController {

    private static final String PERMISSION = "integration:manage";

    private final AttendanceConnectorService connectors;
    private final AccessGuard accessGuard;

    public AttendanceConnectorAdminController(AttendanceConnectorService connectors, AccessGuard accessGuard) {
        this.connectors = connectors;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public List<ConnectorResponse> list() {
        accessGuard.require(PERMISSION, PermissionScope.ALL);
        return connectors.list().stream().map(ConnectorResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectorResponse create(@Valid @RequestBody ConnectorCreateRequest request) {
        accessGuard.require(PERMISSION, PermissionScope.ALL);
        return ConnectorResponse.of(connectors.create(request.name()));
    }

    @PutMapping("/{id}/signing-secret")
    public ConnectorResponse replaceSigningSecret(@PathVariable UUID id,
                                                  @Valid @RequestBody SigningSecretRequest request) {
        accessGuard.require(PERMISSION, PermissionScope.ALL);
        return ConnectorResponse.of(connectors.replaceSigningSecret(id, request.signingSecret()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        accessGuard.require(PERMISSION, PermissionScope.ALL);
        connectors.delete(id);
    }

    public record ConnectorCreateRequest(@NotBlank @Size(max = 160) String name) {
    }

    /** Mínimo de 32 caracteres: es la clave de un HMAC, no una contraseña que alguien vaya a recordar. */
    public record SigningSecretRequest(@NotBlank @Size(min = 32, max = 100) String signingSecret) {
    }

    /**
     * @param id          identificador público del conector, el mismo que va en {@code webhookPath}
     * @param webhookPath ruta que hay que registrar, con el host de esta API delante, en el sistema de asistencia
     */
    public record ConnectorResponse(UUID id, String name, boolean hasSigningSecret,
                                    String webhookPath, Instant createdAt, Instant lastReceivedAt) {

        static ConnectorResponse of(AttendanceConnector connector) {
            return new ConnectorResponse(connector.getPublicId(), connector.getName(),
                    connector.getSigningSecret() != null,
                    "/api/v1/webhooks/attendance/" + connector.getTenantId() + "/" + connector.getPublicId(),
                    connector.getCreatedAt(), connector.getLastReceivedAt());
        }
    }
}
