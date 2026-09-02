package com.ohmdevs.hrsaas.people.api;

/**
 * Proyección mínima de un empleado para otros módulos. {@code maskedDocumentNumber}
 * ya viene enmascarado (AD-08: "enmascarado por defecto en respuestas de API"):
 * quien reciba este record nunca ve la cédula/pasaporte completos — pedir el
 * valor sin enmascarar exige el permiso {@code employee:read_bank} y pasa por
 * un camino distinto, que no existe todavía en ningún módulo consumidor.
 */
public record EmployeeDirectorySummary(
        Long employeeId, String firstName, String lastName, String email, String maskedDocumentNumber) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
