package com.ohmdevs.hrsaas.people.infrastructure;

import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.domain.EmployeeStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Búsqueda por índice ciego, nunca por el número en claro (ver {@code Employee.documentNumberHmac}). */
    Optional<Employee> findByDocumentNumberHmac(String documentNumberHmac);

    Optional<Employee> findByUserId(Long userId);

    /**
     * Listado administrativo paginado por keyset (mismo patrón que
     * {@code PayslipRepository}): {@code status}/{@code likePattern} nulos se
     * ignoran, {@code afterId} nulo pide la primera página. Nunca contra
     * {@code documentNumber}, que va cifrado y solo es buscable por su índice
     * ciego de igualdad exacta, no por coincidencia parcial.
     *
     * <p>{@code likePattern} llega ya armado ({@code "%termino%"}, en
     * minúsculas) desde {@code people.application.EmployeeAdminQueryService}
     * en vez de construirse aquí con {@code CONCAT}/{@code LOWER} sobre el
     * parámetro crudo: enlazar un {@code String} nulo dentro de una expresión
     * {@code LOWER(CONCAT('%', :param, '%'))} hace que Hibernate 7 no pueda
     * inferir el tipo JDBC del parámetro y termine enviándolo como
     * {@code bytea} — Postgres responde "function lower(bytea) does not
     * exist". Enlazar el patrón ya resuelto directo en el {@code LIKE} (o
     * {@code NULL}) evita la inferencia ambigua. Encontrado corriendo
     * {@code AdminApiIntegrationTest} contra Postgres real, no en revisión de
     * código.
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE (:status IS NULL OR e.status = :status)
              AND (:likePattern IS NULL
                   OR LOWER(e.firstName) LIKE :likePattern
                   OR LOWER(e.lastName)  LIKE :likePattern)
              AND (:afterId IS NULL OR e.id < :afterId)
            ORDER BY e.id DESC
            """)
    List<Employee> search(@Param("status") EmployeeStatus status, @Param("likePattern") String likePattern,
                           @Param("afterId") Long afterId, Pageable pageable);
}
