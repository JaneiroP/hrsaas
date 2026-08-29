package com.ohmdevs.hrsaas.loans.infrastructure;

import com.ohmdevs.hrsaas.loans.domain.GarnishmentOrder;
import com.ohmdevs.hrsaas.loans.domain.GarnishmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GarnishmentOrderRepository extends JpaRepository<GarnishmentOrder, Long> {

    List<GarnishmentOrder> findByEmployeeId(Long employeeId);

    /** Orden de prelación: prioridad ascendente primero. */
    List<GarnishmentOrder> findByEmployeeIdAndStatusOrderByPriorityAsc(Long employeeId, GarnishmentStatus status);
}
