package com.ohmdevs.hrsaas.contracts.application;

import com.ohmdevs.hrsaas.contracts.domain.AmendmentType;
import com.ohmdevs.hrsaas.contracts.domain.ContractAmendment;
import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import com.ohmdevs.hrsaas.contracts.domain.SalaryChangeReason;
import com.ohmdevs.hrsaas.contracts.domain.SalaryHistory;
import com.ohmdevs.hrsaas.contracts.domain.Termination;
import com.ohmdevs.hrsaas.contracts.domain.TerminationCause;
import com.ohmdevs.hrsaas.contracts.infrastructure.ContractAmendmentRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.EmploymentContractRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.SalaryHistoryRepository;
import com.ohmdevs.hrsaas.contracts.infrastructure.TerminationRepository;
import com.ohmdevs.hrsaas.people.api.EmployeeLifecycleService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Único servicio transaccional de {@code contracts}, mismo estilo que
 * {@code TenantProvisioningService}/{@code InvitationService}: cada
 * transición valida {@link ContractStatus#canTransitionTo(ContractStatus)} y
 * lanza {@link IllegalStateTransitionException} si no es legal — aquí, no en
 * el controlador (que todavía no existe).
 */
@Service
public class ContractLifecycleService {

    private final EmploymentContractRepository contractRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final ContractAmendmentRepository amendmentRepository;
    private final TerminationRepository terminationRepository;
    private final EmployeeLifecycleService employeeLifecycle;
    private final ObjectMapper objectMapper;

    public ContractLifecycleService(EmploymentContractRepository contractRepository,
                                     SalaryHistoryRepository salaryHistoryRepository,
                                     ContractAmendmentRepository amendmentRepository,
                                     TerminationRepository terminationRepository,
                                     EmployeeLifecycleService employeeLifecycle,
                                     ObjectMapper objectMapper) {
        this.contractRepository = contractRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.amendmentRepository = amendmentRepository;
        this.terminationRepository = terminationRepository;
        this.employeeLifecycle = employeeLifecycle;
        this.objectMapper = objectMapper;
    }

    /**
     * Abre el contrato y el primer registro de {@link SalaryHistory}, y marca
     * el expediente como {@code ACTIVE} en {@code people}.
     */
    @Transactional
    public EmploymentContract hire(Long employeeId, Long companyId, Long positionId, Long costCenterId,
                                    Long supervisorEmployeeId, ContractType contractType,
                                    LocalDate startDate, LocalDate endDate, LocalDate probationEndsAt,
                                    BigDecimal initialSalary, String currency) {
        TenantContext.require();
        contractRepository.findByEmployeeIdAndStatusNot(employeeId, ContractStatus.TERMINATED).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "El empleado " + employeeId + " ya tiene un contrato no terminado en este tenant");
        });

        EmploymentContract contract = new EmploymentContract();
        contract.setEmployeeId(employeeId);
        contract.setCompanyId(companyId);
        contract.setPositionId(positionId);
        contract.setCostCenterId(costCenterId);
        contract.setSupervisorEmployeeId(supervisorEmployeeId);
        contract.setContractType(contractType);
        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        contract.setProbationEndsAt(probationEndsAt);
        contract.setStatus(ContractStatus.ACTIVE);
        contract = contractRepository.save(contract);

        SalaryHistory initial = new SalaryHistory();
        initial.setContract(contract);
        initial.setAmount(initialSalary);
        initial.setCurrency(currency);
        initial.setEffectiveFrom(startDate);
        initial.setReason(SalaryChangeReason.HIRE);
        salaryHistoryRepository.save(initial);

        employeeLifecycle.markHired(employeeId);
        return contract;
    }

    /** Cierra el registro de salario abierto y abre uno nuevo — nunca se edita el histórico. */
    @Transactional
    public SalaryHistory changeSalary(Long contractId, BigDecimal newAmount, String currency,
                                       LocalDate effectiveFrom, SalaryChangeReason reason) {
        TenantContext.require();
        // saveAndFlush, no save: SalaryHistory usa IDENTITY, que fuerza el INSERT de la
        // fila nueva a ejecutarse de inmediato al llamar a save() (Hibernate no puede
        // diferirlo ni batchearlo con IDENTITY, necesita el id generado ya). Si el cierre
        // de la fila abierta se queda diferido en la cola de acciones (el comportamiento
        // por defecto de una actualización), Postgres ve momentaneamente dos filas con
        // effective_to IS NULL para el mismo contrato y el indice unico parcial
        // uk_salary_history_open lo rechaza -- encontrado corriendo el test, no en
        // revision de codigo.
        salaryHistoryRepository.findByContractIdAndEffectiveToIsNull(contractId).ifPresent(open -> {
            open.setEffectiveTo(effectiveFrom.minusDays(1));
            salaryHistoryRepository.saveAndFlush(open);
        });

        EmploymentContract contract = contractRepository.findById(contractId).orElseThrow(
                () -> new IllegalArgumentException("Contrato " + contractId + " no existe en este tenant"));

        SalaryHistory next = new SalaryHistory();
        next.setContract(contract);
        next.setAmount(newAmount);
        next.setCurrency(currency);
        next.setEffectiveFrom(effectiveFrom);
        next.setReason(reason);
        return salaryHistoryRepository.save(next);
    }

    /** Cambia de cargo sin abrir un contrato nuevo; el cambio queda en {@code employment_contracts_aud} vía Envers. */
    @Transactional
    public EmploymentContract promote(Long contractId, Long newPositionId, LocalDate effectiveDate, String notes) {
        TenantContext.require();
        EmploymentContract contract = contractRepository.findById(contractId).orElseThrow(
                () -> new IllegalArgumentException("Contrato " + contractId + " no existe en este tenant"));

        Long previousPositionId = contract.getPositionId();
        contract.setPositionId(newPositionId);
        contract = contractRepository.save(contract);

        recordAmendment(contract, AmendmentType.PROMOTION, effectiveDate,
                Map.of("previousPositionId", previousPositionId, "newPositionId", newPositionId), notes);
        return contract;
    }

    @Transactional
    public EmploymentContract suspend(Long contractId) {
        return transition(contractId, ContractStatus.SUSPENDED);
    }

    @Transactional
    public EmploymentContract reactivate(Long contractId) {
        return transition(contractId, ContractStatus.ACTIVE);
    }

    /** Termina el contrato, registra la causa y cierra el histórico salarial. */
    @Transactional
    public Termination terminate(Long contractId, LocalDate terminationDate, TerminationCause cause,
                                  boolean noticeGiven, boolean severanceEligible, String notes) {
        TenantContext.require();
        EmploymentContract contract = transition(contractId, ContractStatus.TERMINATED);

        salaryHistoryRepository.findByContractIdAndEffectiveToIsNull(contractId).ifPresent(open -> {
            open.setEffectiveTo(terminationDate);
            salaryHistoryRepository.save(open);
        });

        Termination termination = new Termination();
        termination.setContract(contract);
        termination.setTerminationDate(terminationDate);
        termination.setCause(cause);
        termination.setNoticeGiven(noticeGiven);
        termination.setSeveranceEligible(severanceEligible);
        termination.setNotes(notes);
        termination = terminationRepository.save(termination);

        employeeLifecycle.markSeparated(contract.getEmployeeId());
        return termination;
    }

    private EmploymentContract transition(Long contractId, ContractStatus target) {
        EmploymentContract contract = contractRepository.findById(contractId).orElseThrow(
                () -> new IllegalArgumentException("Contrato " + contractId + " no existe en este tenant"));
        ContractStatus current = contract.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("EmploymentContract", contractId, current, target);
        }
        contract.setStatus(target);
        return contractRepository.save(contract);
    }

    private void recordAmendment(EmploymentContract contract, AmendmentType type, LocalDate effectiveDate,
                                  Map<String, ?> detail, String notes) {
        ContractAmendment amendment = new ContractAmendment();
        amendment.setContract(contract);
        amendment.setAmendmentType(type);
        amendment.setEffectiveDate(effectiveDate);
        amendment.setDetail(writeJson(detail));
        amendment.setNotes(notes);
        amendmentRepository.save(amendment);
    }

    /**
     * Jackson 3.x ({@code tools.jackson.*}, no {@code com.fasterxml.jackson.*} —
     * este proyecto trae la serie 3 vía {@code spring-boot-starter-jackson}):
     * {@link JacksonException} ya es {@code RuntimeException}, no hace falta
     * capturarla, pero se envuelve igual para dar contexto, mismo estilo que
     * {@code AesGcmEncryptionService}.
     */
    private String writeJson(Map<String, ?> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar el detalle de la adenda", e);
        }
    }
}
