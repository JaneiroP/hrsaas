/**
 * Estructura de la empresa: razón social (RNC), sucursales, departamentos,
 * cargos, centros de costo y bandas salariales.
 *
 * <p>Base de {@code contracts} — un contrato se firma con una {@link
 * com.ohmdevs.hrsaas.org.domain.Company}, no con el tenant directamente. No
 * conoce empleados ni contratos: la dirección de dependencia va de captura
 * (aquí) hacia dominio, nunca al revés.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Estructura organizacional",
        allowedDependencies = {"platform :: tenancy", "platform :: domain"})
package com.ohmdevs.hrsaas.org;
