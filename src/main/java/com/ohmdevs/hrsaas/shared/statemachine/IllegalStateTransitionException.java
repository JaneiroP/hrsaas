package com.ohmdevs.hrsaas.shared.statemachine;

/**
 * Transición ilegal en un agregado con ciclo de vida (AD-06).
 *
 * <p>Genérica y reutilizable por cualquier módulo con un {@code enum} de
 * estado que declare {@code canTransitionTo(target)} — hoy
 * {@code EmployeeStatus} y {@code ContractStatus}, más adelante
 * {@code payroll}/{@code time}/{@code loans}/{@code banking}. La valida
 * siempre el servicio de aplicación antes de persistir, nunca el
 * controlador ni la entidad.
 */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String aggregateType, Object aggregateId, Enum<?> from, Enum<?> to) {
        super("%s %s no puede pasar de %s a %s".formatted(aggregateType, aggregateId, from, to));
    }
}
