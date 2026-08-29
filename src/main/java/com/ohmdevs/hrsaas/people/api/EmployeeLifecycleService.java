package com.ohmdevs.hrsaas.people.api;

import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.domain.EmployeeStatus;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transiciones de {@link EmployeeStatus}, disparadas desde {@code contracts}:
 * el expediente no decide su propio estado, lo decide la existencia de un
 * contrato ({@code ContractLifecycleService.hire}/{@code .terminate} llaman
 * aquí). Único punto de entrada público de {@code people} para otros
 * módulos — todo lo demás del módulo queda interno.
 */
@Service
public class EmployeeLifecycleService {

    private final EmployeeRepository employeeRepository;

    public EmployeeLifecycleService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** El expediente pasa a {@code ACTIVE}: tiene un contrato {@code ACTIVE}/{@code SUSPENDED}. */
    @Transactional
    public Employee markHired(Long employeeId) {
        return transition(employeeId, EmployeeStatus.ACTIVE);
    }

    /** El expediente pasa a {@code INACTIVE}: ya no tiene contrato vigente. */
    @Transactional
    public Employee markSeparated(Long employeeId) {
        return transition(employeeId, EmployeeStatus.INACTIVE);
    }

    private Employee transition(Long employeeId, EmployeeStatus target) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(
                () -> new IllegalArgumentException("Empleado " + employeeId + " no existe en este tenant"));
        EmployeeStatus current = employee.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("Employee", employeeId, current, target);
        }
        employee.setStatus(target);
        return employeeRepository.save(employee);
    }
}
