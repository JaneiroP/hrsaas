package com.ohmdevs.hrsaas.time.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Asignación de un {@link WorkSchedule} a un empleado, con vigencia por
 * fecha — mismo patrón que {@code SalaryHistory}: nunca se edita en el
 * sitio, se cierra la vigente y se abre una nueva.
 */
@Entity
@Table(name = "employee_work_schedules")
@Getter
@Setter
public class EmployeeWorkSchedule extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_schedule_id", nullable = false, updatable = false)
    private WorkSchedule workSchedule;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
