/**
 * Superficie pública de {@code contracts} hacia otros módulos:
 * {@link com.ohmdevs.hrsaas.contracts.api.ContractPayrollQueryService}
 * (Fase 3), que expone los contratos activos de <em>una empresa</em> y su
 * salario vigente — el insumo de "plantilla, salario base" que menciona el
 * blueprint §02 en la flecha {@code DOMINIO → CÁLCULO}; y
 * {@link com.ohmdevs.hrsaas.contracts.api.EmployeeEmploymentQueryService}
 * (Fase 5, adelantada), que expone el contrato vigente de <em>un
 * empleado</em> para el perfil de autoservicio de {@code portal}.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.contracts.api;
