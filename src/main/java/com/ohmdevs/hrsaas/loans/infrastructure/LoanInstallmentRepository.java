package com.ohmdevs.hrsaas.loans.infrastructure;

import com.ohmdevs.hrsaas.loans.domain.LoanInstallment;
import com.ohmdevs.hrsaas.loans.domain.LoanInstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, Long> {

    List<LoanInstallment> findByLoanAgreementId(Long loanAgreementId);

    List<LoanInstallment> findByLoanAgreementIdAndStatus(Long loanAgreementId, LoanInstallmentStatus status);

    List<LoanInstallment> findByLoanAgreement_EmployeeIdAndStatusAndDueDateBetween(
            Long employeeId, LoanInstallmentStatus status, LocalDate from, LocalDate to);
}
