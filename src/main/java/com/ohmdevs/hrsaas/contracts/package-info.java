/**
 * Vínculo laboral con vigencia: tipo, período de prueba, histórico salarial,
 * adendas y terminación con causa.
 *
 * <p>Único de los tres módulos de Fase 1 que depende de los otros dos, pero
 * <strong>por identificador, nunca {@code @ManyToOne}</strong> — la
 * integridad referencial vive en la FK de la migración SQL, no en el mapeo
 * de Hibernate. Depende de {@code people :: api} para disparar las
 * transiciones de {@code EmployeeStatus} al contratar/desvincular; no
 * depende de {@code org} ni de {@code people.domain}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Contratos",
        allowedDependencies = {"platform :: tenancy", "platform :: domain", "shared", "people :: api"})
package com.ohmdevs.hrsaas.contracts;
