/**
 * Equipos entregados a empleados: inventario, asignación con acta y
 * devolución.
 *
 * <p>Sin superficie pública hacia otros módulos por ahora — "checklist ligado
 * a la terminación" (blueprint Fase 2) queda como consulta disponible
 * ({@code findByEmployeeIdAndStatus(ACTIVE)}) para cuando el flujo de
 * desvinculación de {@code contracts} decida usarla explícitamente; no se
 * conecta sola aquí para no inventar un acoplamiento que nadie pidió.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Activos",
        allowedDependencies = {"platform :: tenancy", "platform :: domain", "shared"})
package com.ohmdevs.hrsaas.assets;
