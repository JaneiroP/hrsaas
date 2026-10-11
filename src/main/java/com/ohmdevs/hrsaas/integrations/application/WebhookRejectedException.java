package com.ohmdevs.hrsaas.integrations.application;

/**
 * Un webhook que no se pudo autenticar. El mensaje dice la causa real
 * (conector inexistente o sin secreto, firma inválida o vencida) y
 * va solo al log: quien llama recibe siempre el mismo 401, para que la
 * respuesta no sirva para averiguar qué conectores existen.
 */
public class WebhookRejectedException extends RuntimeException {

    public WebhookRejectedException(String message) {
        super(message);
    }
}
