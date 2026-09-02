/**
 * Superficie pública de {@code payroll}:
 * {@link com.ohmdevs.hrsaas.payroll.api.PayrollRunPayoutQueryService} hacia
 * {@code banking} (Fase 4), con los netos de <em>toda una corrida</em>; y
 * {@link com.ohmdevs.hrsaas.payroll.api.PayslipSelfServiceQueryService} hacia
 * {@code portal} (Fase 5, adelantada), con el histórico de <em>un solo
 * empleado</em>.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.payroll.api;
