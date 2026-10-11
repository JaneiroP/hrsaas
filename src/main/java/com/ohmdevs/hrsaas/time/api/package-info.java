/**
 * Superficie pública de {@code time}:
 * {@link com.ohmdevs.hrsaas.time.api.TimeAndAttendanceSummaryService} hacia
 * {@code payroll} (Fase 3), con días/horas de <em>toda una empresa</em>; y
 * {@link com.ohmdevs.hrsaas.time.api.LeaveSelfServiceQueryService} hacia
 * {@code portal} (Fase 5, adelantada), con saldos y solicitudes de licencia
 * de <em>un solo empleado</em>; y
 * {@link com.ohmdevs.hrsaas.time.api.TimeEntryIngestService} hacia
 * {@code integrations}, que entrega marcajes de sistemas externos de forma
 * idempotente. Las excepciones de este paquete
 * ({@code InvalidLeaveRangeException}, {@code LeaveRequestNotFoundException},
 * {@code LeaveRequestNotCancellableException}, {@code InsufficientLeaveBalanceException})
 * son parte del contrato: quien consuma este servicio las captura para
 * traducirlas a códigos HTTP concretos.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.time.api;
