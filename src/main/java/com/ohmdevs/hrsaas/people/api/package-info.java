/**
 * Superficie pública de {@code people} hacia otros módulos:
 * {@link com.ohmdevs.hrsaas.people.api.EmployeeLifecycleService}, que
 * {@code contracts} llama al contratar o desvincular a alguien — el
 * expediente no decide su propio estado, lo decide la existencia de un
 * contrato; {@link com.ohmdevs.hrsaas.people.api.PrimaryBankAccountQueryService},
 * que {@code banking} (Fase 4) llama para armar un lote de pago; y
 * {@link com.ohmdevs.hrsaas.people.api.EmployeeDirectoryQueryService}, que
 * {@code portal} (Fase 5, adelantada) usa para traducir el {@code userId}
 * del actor autenticado a su expediente de empleado.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.people.api;
