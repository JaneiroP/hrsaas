/**
 * Utilidades técnicas sin conocimiento de dominio, compartidas por cualquier
 * módulo: máquinas de estado ({@code shared.statemachine}), cifrado de
 * columna e índice ciego ({@code shared.crypto}, AD-08) y auditoría de
 * revisiones con tenant ({@code shared.audit}, AD-07).
 *
 * <p>Abierto a propósito ({@code OPEN}): no hay fronteras de negocio que
 * proteger aquí, solo utilidades técnicas reutilizables.
 *
 * <p>Regla de dirección: {@code shared} no conoce ningún módulo de negocio.
 * Puede depender de {@code platform :: tenancy} (lo usa
 * {@code shared.audit.TenantRevisionListener} para leer el tenant activo al
 * crear una revisión de Envers); nunca al revés.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Utilidades compartidas",
        allowedDependencies = {"platform :: tenancy"})
package com.ohmdevs.hrsaas.shared;
