/**
 * Feriados, licencias, solicitudes de ausencia, horarios y marcajes.
 *
 * <p>Alimenta a {@code payroll} (Fase 3): para un empleado y un período,
 * expone días efectivos, horas extra y ausencias no remuneradas —
 * {@link com.ohmdevs.hrsaas.time.api.TimeAndAttendanceSummaryService}, el
 * único contenido de {@code time.api}. Referencia a {@code org}/{@code people}
 * por {@code Long*Id}, nunca {@code @ManyToOne} — mismo patrón que
 * {@code contracts}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Tiempo y ausencias",
        allowedDependencies = {"platform :: tenancy", "platform :: domain", "shared"})
package com.ohmdevs.hrsaas.time;
