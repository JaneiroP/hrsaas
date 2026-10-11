package com.ohmdevs.hrsaas.integrations.web;

import com.ohmdevs.hrsaas.integrations.application.AttendanceWebhookService;
import com.ohmdevs.hrsaas.integrations.application.AttendanceWebhookService.BatchResult;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * El único endpoint de la aplicación que no entra con un token de usuario: lo
 * llama otro sistema, y lo que lo autentica es la firma de cada petición.
 * {@code SecurityConfiguration} lo deja pasar sin JWT por ese motivo.
 *
 * <p><strong>El tenant viaja en la URL porque no hay token del que sacarlo.</strong>
 * No autentica nada: solo dice bajo qué tenant buscar el conector. Con RLS no
 * existe "buscar el conector en todos los tenants", y eso es deliberado. Un
 * tenant equivocado o inventado da el mismo 401 que una firma falsa, porque
 * bajo ese tenant el conector no aparece.
 *
 * <p>El cuerpo se recibe como bytes y no como objeto: la firma se calcula
 * sobre lo que llegó exactamente, antes de que nada lo interprete.
 */
@RestController
@RequestMapping("/api/v1/webhooks/attendance")
public class AttendanceWebhookController {

    static final String SIGNATURE_HEADER = "X-Attendance-Signature";

    private final AttendanceWebhookService webhooks;

    public AttendanceWebhookController(AttendanceWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    /**
     * Responde 200 con el recuento cuando el lote quedó guardado. Reenviar el
     * mismo lote es seguro: lo ya guardado se cuenta como duplicado.
     */
    @PostMapping("/{tenantId}/{connectorId}")
    public BatchResult receive(@PathVariable Long tenantId, @PathVariable UUID connectorId,
                               @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                               @RequestBody byte[] body) {
        // Fuera de la transacción a propósito: el tenant tiene que estar puesto
        // cuando ésta arranca, que es cuando se fija para RLS.
        return TenantContext.callWith(tenantId, () -> webhooks.receive(connectorId, signature, body));
    }
}
