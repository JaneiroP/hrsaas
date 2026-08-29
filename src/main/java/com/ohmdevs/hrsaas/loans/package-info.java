/**
 * Préstamos, avances de salario y embargos judiciales, con orden de
 * prelación entre ellos.
 *
 * <p>Alimenta a {@code payroll} (Fase 3):
 * {@link com.ohmdevs.hrsaas.loans.api.PayrollDeductionSummaryService}
 * expone, para un empleado y un período, las cuotas pendientes de cada
 * fuente — el cálculo de cuánto se retiene de verdad (respetando el mínimo
 * inembargable) es de {@code payroll}, no de este módulo.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Préstamos y embargos",
        allowedDependencies = {"platform :: tenancy", "platform :: domain", "shared"})
package com.ohmdevs.hrsaas.loans;
