package com.ohmdevs.hrsaas.contracts.infrastructure;

import com.ohmdevs.hrsaas.contracts.domain.ContractStatus;
import com.ohmdevs.hrsaas.contracts.domain.EmploymentContract;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, Long> {

    List<EmploymentContract> findByEmployeeId(Long employeeId);

    /** El índice único parcial de la migración garantiza a lo sumo un resultado. */
    Optional<EmploymentContract> findByEmployeeIdAndStatusNot(Long employeeId, ContractStatus status);

    List<EmploymentContract> findByCompanyIdAndStatus(Long companyId, ContractStatus status);

    /**
     * Listado administrativo paginado por keyset (mismo patrón que
     * {@code EmployeeRepository.search}): {@code employeeId}/{@code companyId}/
     * {@code status} nulos se ignoran, {@code afterId} nulo pide la primera
     * página. Un solo método cubre tanto "todos los contratos de un
     * empleado" como "todos los contratos de una empresa" que pide
     * {@code contracts.web.ContractAdminController}.
     */
    @Query("""
            SELECT c FROM EmploymentContract c
            WHERE (:employeeId IS NULL OR c.employeeId = :employeeId)
              AND (:companyId IS NULL OR c.companyId = :companyId)
              AND (:status IS NULL OR c.status = :status)
              AND (:afterId IS NULL OR c.id < :afterId)
            ORDER BY c.id DESC
            """)
    List<EmploymentContract> search(@Param("employeeId") Long employeeId, @Param("companyId") Long companyId,
                                     @Param("status") ContractStatus status, @Param("afterId") Long afterId,
                                     Pageable pageable);
}
