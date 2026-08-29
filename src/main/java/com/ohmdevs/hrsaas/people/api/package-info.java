/**
 * Superficie pública de {@code people} hacia otros módulos:
 * {@link com.ohmdevs.hrsaas.people.api.EmployeeLifecycleService}, que
 * {@code contracts} llama al contratar o desvincular a alguien — el
 * expediente no decide su propio estado, lo decide la existencia de un
 * contrato.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.people.api;
