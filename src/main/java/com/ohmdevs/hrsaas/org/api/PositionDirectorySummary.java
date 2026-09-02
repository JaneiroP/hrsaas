package com.ohmdevs.hrsaas.org.api;

/**
 * Lo que {@code portal} necesita de un cargo: su título y el nombre del
 * departamento al que pertenece, ya resuelto — evita que {@code portal} tenga
 * que conocer la entidad {@code Department} para mostrar un solo nombre.
 */
public record PositionDirectorySummary(Long positionId, String title, Long departmentId, String departmentName) {
}
