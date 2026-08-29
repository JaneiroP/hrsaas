/**
 * El motor de nómina (Fase 3 — "la fase que no se recorta", blueprint §12):
 * devengos, TSS, ISR, deducciones, neto. Lee de {@code contracts},
 * {@code time :: api}, {@code loans :: api} y {@code taxconfig :: api};
 * no lo conoce ninguno de ellos — la dirección de dependencia va de captura
 * hacia el cálculo, nunca al revés (blueprint §02).
 *
 * <p><strong>AD-03: un {@code PayrollRun} cerrado es inmutable.</strong>
 * Nunca se edita ni se borra; se corrige con una corrida de reverso.
 *
 * <p><strong>Alcance real de esta implementación</strong>, documentado para
 * no dar por hecho más de lo que hay: el cálculo por empleado, la máquina de
 * estados de la corrida y el orden de la tubería (blueprint §04) están
 * completos y probados. Spring Batch particionado (el objetivo de 5.000
 * volantes en 3 minutos), el PDF del volante archivado en S3, y el juego
 * completo de golden files acordados con un contador dominicano — **no
 * están implementados en esta pasada**, quedan señalados como pendientes.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Nómina",
        allowedDependencies = {
                "platform :: tenancy", "platform :: domain", "shared",
                "taxconfig :: api", "taxconfig :: domain",
                "time :: api", "loans :: api", "contracts :: api"
        })
package com.ohmdevs.hrsaas.payroll;
