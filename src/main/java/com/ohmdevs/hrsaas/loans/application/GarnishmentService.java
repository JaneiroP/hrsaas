package com.ohmdevs.hrsaas.loans.application;

import com.ohmdevs.hrsaas.loans.domain.GarnishmentOrder;
import com.ohmdevs.hrsaas.loans.domain.GarnishmentStatus;
import com.ohmdevs.hrsaas.loans.infrastructure.GarnishmentOrderRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Embargos judiciales, con orden de prelación cuando compiten por el mínimo inembargable de un empleado. */
@Service
public class GarnishmentService {

    private final GarnishmentOrderRepository garnishmentOrderRepository;

    public GarnishmentService(GarnishmentOrderRepository garnishmentOrderRepository) {
        this.garnishmentOrderRepository = garnishmentOrderRepository;
    }

    /** Exactamente uno de {@code fixedAmount}/{@code percentage} debe venir con valor. */
    @Transactional
    public GarnishmentOrder create(Long employeeId, String courtOrderNumber, String beneficiary,
                                    BigDecimal fixedAmount, BigDecimal percentage, int priority,
                                    LocalDate startDate) {
        TenantContext.require();
        boolean hasFixed = fixedAmount != null;
        boolean hasPercentage = percentage != null;
        if (hasFixed == hasPercentage) {
            throw new IllegalArgumentException(
                    "Un embargo debe declarar exactamente uno de monto fijo o porcentaje, no ambos ni ninguno");
        }

        GarnishmentOrder order = new GarnishmentOrder();
        order.setEmployeeId(employeeId);
        order.setCourtOrderNumber(courtOrderNumber);
        order.setBeneficiary(beneficiary);
        order.setFixedAmount(fixedAmount);
        order.setPercentage(percentage);
        order.setPriority(priority);
        order.setStartDate(startDate);
        return garnishmentOrderRepository.save(order);
    }

    @Transactional
    public GarnishmentOrder complete(Long garnishmentOrderId, LocalDate endDate) {
        GarnishmentOrder order = transition(garnishmentOrderId, GarnishmentStatus.COMPLETED);
        order.setEndDate(endDate);
        return garnishmentOrderRepository.save(order);
    }

    @Transactional
    public GarnishmentOrder cancel(Long garnishmentOrderId) {
        return transition(garnishmentOrderId, GarnishmentStatus.CANCELLED);
    }

    private GarnishmentOrder transition(Long garnishmentOrderId, GarnishmentStatus target) {
        GarnishmentOrder order = garnishmentOrderRepository.findById(garnishmentOrderId).orElseThrow(
                () -> new IllegalArgumentException("Embargo " + garnishmentOrderId + " no existe en este tenant"));
        GarnishmentStatus current = order.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("GarnishmentOrder", garnishmentOrderId, current, target);
        }
        order.setStatus(target);
        return order;
    }
}
