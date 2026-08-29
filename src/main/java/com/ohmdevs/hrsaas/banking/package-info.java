/**
 * Lotes de pago y archivos bancarios (Fase 4, blueprint §06). Toma el neto
 * de una {@code PayrollRun} aprobada y arma un lote con maker-checker antes
 * de exportarlo.
 *
 * <p><strong>Alcance real de esta implementación</strong>, documentado para
 * no dar por hecho más de lo que hay: el ciclo de vida del lote
 * ({@code CONSTRUIDO → VALIDADO → APROBADO → EXPORTADO → ENVIADO → CONCILIADO}),
 * las validaciones bloqueantes, el maker-checker, y la conciliación a nivel
 * de dominio (aceptar un resultado ya parseado por ítem) están completos y
 * probados. <strong>No están implementados en esta pasada</strong>: los
 * layouts declarativos y la generación real de archivo para Popular, BHD,
 * Banreservas o CSV genérico (blueprint §06 "Layouts como configuración"),
 * ni el parseo del archivo de retorno bancario real — {@link
 * com.ohmdevs.hrsaas.banking.application.PaymentBatchLifecycleService#reconcile}
 * recibe los resultados ya parseados, no un archivo. Sin esos generadores,
 * ningún lote de esta implementación puede enviarse de verdad a un banco
 * todavía.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Banca y pagos",
        allowedDependencies = {
                "platform :: tenancy", "platform :: domain", "shared",
                "payroll :: api", "people :: api"
        })
package com.ohmdevs.hrsaas.banking;
