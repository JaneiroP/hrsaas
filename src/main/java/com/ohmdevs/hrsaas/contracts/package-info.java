/**
 * Vínculo laboral con vigencia: tipo, período de prueba, histórico salarial,
 * adendas y terminación con causa.
 *
 * <p>Único de los tres módulos de Fase 1 que depende de los otros dos, pero
 * <strong>por identificador, nunca {@code @ManyToOne}</strong> — la
 * integridad referencial vive en la FK de la migración SQL, no en el mapeo
 * de Hibernate. Depende de {@code people :: api} para disparar las
 * transiciones de {@code EmployeeStatus} al contratar/desvincular; no
 * depende de {@code org.domain} ni de {@code people.domain}.
 *
 * <p>Desde Fase 5 (API administrativa, {@code contracts.web.ContractAdminController})
 * también depende de {@code org :: api} y de {@code platform :: access}: el
 * primero para resolver nombre de empresa y título de cargo al responder un
 * contrato por HTTP (antes de esto, {@code contracts} nunca necesitaba
 * traducir sus propios ids a nombres — se lo dejaba a quien consumiera
 * {@code contracts.api}, como hace {@code portal}); el segundo para exigir
 * {@code contract:read}/{@code contract:manage} en cada endpoint nuevo.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Contratos",
        allowedDependencies = {
                "platform :: access", "platform :: tenancy", "platform :: domain", "shared",
                "people :: api", "org :: api"
        })
package com.ohmdevs.hrsaas.contracts;
