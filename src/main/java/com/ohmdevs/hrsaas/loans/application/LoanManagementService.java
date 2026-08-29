package com.ohmdevs.hrsaas.loans.application;

import com.ohmdevs.hrsaas.loans.domain.LoanAgreement;
import com.ohmdevs.hrsaas.loans.domain.LoanInstallment;
import com.ohmdevs.hrsaas.loans.domain.LoanInstallmentStatus;
import com.ohmdevs.hrsaas.loans.domain.LoanStatus;
import com.ohmdevs.hrsaas.loans.infrastructure.LoanAgreementRepository;
import com.ohmdevs.hrsaas.loans.infrastructure.LoanInstallmentRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Préstamos y su tabla de amortización. */
@Service
public class LoanManagementService {

    private final LoanAgreementRepository loanAgreementRepository;
    private final LoanInstallmentRepository loanInstallmentRepository;

    public LoanManagementService(LoanAgreementRepository loanAgreementRepository,
                                  LoanInstallmentRepository loanInstallmentRepository) {
        this.loanAgreementRepository = loanAgreementRepository;
        this.loanInstallmentRepository = loanInstallmentRepository;
    }

    /**
     * Genera la tabla de amortización en cuotas iguales, mensuales desde
     * {@code startDate}. La última cuota absorbe el residuo de redondeo —
     * la suma de cuotas siempre cuadra exacto con el principal, nunca sobra
     * ni falta un centavo por dividir en partes iguales.
     */
    @Transactional
    public LoanAgreement createLoan(Long employeeId, BigDecimal principal, int installmentCount,
                                     LocalDate startDate, String description) {
        TenantContext.require();
        if (installmentCount <= 0) {
            throw new IllegalArgumentException("El número de cuotas debe ser mayor que cero");
        }

        BigDecimal installmentAmount = principal.divide(
                BigDecimal.valueOf(installmentCount), 2, RoundingMode.DOWN);

        LoanAgreement loan = new LoanAgreement();
        loan.setEmployeeId(employeeId);
        loan.setPrincipal(principal);
        loan.setInstallmentCount(installmentCount);
        loan.setInstallmentAmount(installmentAmount);
        loan.setStartDate(startDate);
        loan.setDescription(description);
        loan = loanAgreementRepository.save(loan);

        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 1; i <= installmentCount; i++) {
            boolean isLast = i == installmentCount;
            BigDecimal amount = isLast ? principal.subtract(allocated) : installmentAmount;
            allocated = allocated.add(amount);

            LoanInstallment installment = new LoanInstallment();
            installment.setLoanAgreement(loan);
            installment.setInstallmentNumber(i);
            installment.setDueDate(startDate.plusMonths(i));
            installment.setAmount(amount);
            loanInstallmentRepository.save(installment);
        }
        return loan;
    }

    /** Marca la cuota como pagada y, si era la última pendiente, cierra el préstamo. */
    @Transactional
    public LoanInstallment payInstallment(Long installmentId) {
        TenantContext.require();
        LoanInstallment installment = loanInstallmentRepository.findById(installmentId).orElseThrow(
                () -> new IllegalArgumentException("Cuota " + installmentId + " no existe en este tenant"));
        if (!installment.getStatus().canTransitionTo(LoanInstallmentStatus.PAID)) {
            throw new IllegalStateTransitionException(
                    "LoanInstallment", installmentId, installment.getStatus(), LoanInstallmentStatus.PAID);
        }
        installment.setStatus(LoanInstallmentStatus.PAID);
        installment.setPaidAt(Instant.now());
        loanInstallmentRepository.save(installment);

        List<LoanInstallment> pending = loanInstallmentRepository.findByLoanAgreementIdAndStatus(
                installment.getLoanAgreement().getId(), LoanInstallmentStatus.PENDING);
        if (pending.isEmpty()) {
            transitionLoan(installment.getLoanAgreement().getId(), LoanStatus.PAID_OFF);
        }
        return installment;
    }

    @Transactional
    public LoanAgreement cancelLoan(Long loanAgreementId) {
        return transitionLoan(loanAgreementId, LoanStatus.CANCELLED);
    }

    private LoanAgreement transitionLoan(Long loanAgreementId, LoanStatus target) {
        LoanAgreement loan = loanAgreementRepository.findById(loanAgreementId).orElseThrow(
                () -> new IllegalArgumentException("Préstamo " + loanAgreementId + " no existe en este tenant"));
        LoanStatus current = loan.getStatus();
        if (!current.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("LoanAgreement", loanAgreementId, current, target);
        }
        loan.setStatus(target);
        return loanAgreementRepository.save(loan);
    }
}
