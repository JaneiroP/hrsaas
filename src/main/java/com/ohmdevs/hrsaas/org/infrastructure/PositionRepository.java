package com.ohmdevs.hrsaas.org.infrastructure;

import com.ohmdevs.hrsaas.org.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    /**
     * {@code JOIN FETCH department} a propósito: {@code OrgAdminController.toResponse(Position)}
     * llama {@code position.getDepartment().getName()} fuera de cualquier
     * {@code @Transactional} (la transacción de {@code OrgAdminQueryService}
     * ya cerró para cuando el controlador arma la respuesta) y
     * {@code spring.jpa.open-in-view=false} no deja abierta una sesión de
     * respaldo — sin este fetch-join, {@code department} llega como proxy sin
     * inicializar y Hibernate revienta con {@code LazyInitializationException}.
     */
    @Query("SELECT p FROM Position p JOIN FETCH p.department WHERE p.company.id = :companyId")
    List<Position> findByCompanyId(@Param("companyId") Long companyId);

    List<Position> findByDepartmentId(Long departmentId);

    /** Mismo motivo que {@link #findByCompanyId(Long)}, para la lectura de un único cargo. */
    @Query("SELECT p FROM Position p JOIN FETCH p.department WHERE p.id = :id")
    Optional<Position> findByIdFetchDepartment(@Param("id") Long id);
}
