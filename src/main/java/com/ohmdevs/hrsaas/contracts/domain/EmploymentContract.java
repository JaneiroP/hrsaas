package com.ohmdevs.hrsaas.contracts.domain;

import com.ohmdevs.hrsaas.platform.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Vínculo laboral con vigencia. Referencia a {@code org}/{@code people} por
 * {@code Long*Id}, nunca {@code @ManyToOne}: la integridad referencial vive
 * en la FK de la migración SQL, no en el mapeo de Hibernate — mismo patrón
 * que {@code Employee.userId} hacia {@code platform} ("nunca al revés").
 *
 * <p>Sin {@code baseSalary} a propósito: {@link SalaryHistory} es la única
 * fuente del salario. Duplicarlo aquí crearía una segunda fuente de verdad.
 *
 * <p>{@link #supervisorEmployeeId} vive aquí y no en {@code Employee} ni en
 * {@code Position} porque esta entidad ya tiene vigencia por fecha y ya está
 * bajo {@link Audited} — un cambio de supervisor (vía un
 * {@link ContractAmendment} tipo {@code SUPERVISOR_CHANGE}) queda capturado
 * automáticamente en {@code employment_contracts_aud}, listo para que
 * {@code PermissionScope.TEAM} (fase futura) resuelva "quién supervisaba a
 * quién el día X" con una consulta recursiva.
 */
@Entity
@Table(name = "employment_contracts")
@Audited
@Getter
@Setter
public class EmploymentContract extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private Long employeeId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    /** Mutable: una promoción cambia el cargo sin abrir un contrato nuevo. */
    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "cost_center_id")
    private Long costCenterId;

    @Column(name = "supervisor_employee_id")
    private Long supervisorEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    private ContractType contractType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Obligatorio para {@code FIXED_TERM}/{@code INTERNSHIP}; la validación vive en la capa de aplicación. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "probation_ends_at")
    private LocalDate probationEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
