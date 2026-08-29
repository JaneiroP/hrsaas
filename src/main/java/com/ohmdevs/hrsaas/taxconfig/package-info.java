/**
 * Toda regla fiscal y laboral dominicana con vigencia por fecha (AD-04):
 * escalas de TSS, tramos de ISR, parámetros estatutarios (salario mínimo,
 * topes) y la escala de cesantía.
 *
 * <p><strong>Global, sin {@code tenant_id}.</strong> A diferencia de todo
 * módulo de negocio construido hasta ahora, estas tablas no pertenecen a un
 * tenant — la ley dominicana es la misma para todas las empresas del país,
 * no una configuración de producto. Mismo patrón que {@code modules}/
 * {@code permissions} en {@code platform} (catálogo global), no el de
 * {@code org}/{@code people}/{@code contracts} (aislado por tenant). Ninguna
 * tabla de este módulo lleva RLS.
 *
 * <p><strong>Cero tasas/topes/escalas en código Java</strong> (regla no
 * negociable del proyecto): esta es la única fuente de verdad. El motor de
 * {@code payroll} pregunta "¿qué aplicaba el 2025-03-31?"
 * ({@link com.ohmdevs.hrsaas.taxconfig.api.TaxConfigLookupService}), nunca
 * lee una constante.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Configuración fiscal",
        allowedDependencies = {})
package com.ohmdevs.hrsaas.taxconfig;
