/**
 * Borde de entrada desde sistemas externos. Hoy tiene una sola pieza: recibir
 * marcajes de un sistema de control de asistencia por webhook firmado y
 * entregarlos a {@code time}.
 *
 * <p>Del módulo que describe el blueprint (§08) queda fuera, a propósito, todo
 * lo demás: API keys, outbox, webhooks salientes y conectores contables.
 *
 * <p>Regla de dirección: {@code integrations} conoce a {@code time :: api} y a
 * {@code people :: api}; ninguno de los dos lo conoce a él. Un sistema externo
 * nuevo se añade aquí sin tocar el dominio.
 *
 * <p>El contrato de lo que llega (cuerpo, cabecera de firma, reintentos) lo
 * define quien envía; aquí no se importa ni una clase suya, solo se
 * implementa lo que ese contrato dice.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Integraciones",
        allowedDependencies = {
                "platform :: access", "platform :: tenancy", "platform :: domain", "shared",
                "time :: api", "people :: api"
        })
package com.ohmdevs.hrsaas.integrations;
