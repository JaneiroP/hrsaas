package com.ohmdevs.hrsaas.loans.infrastructure;

import com.ohmdevs.hrsaas.loans.domain.LoanAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanAgreementRepository extends JpaRepository<LoanAgreement, Long> {

    List<LoanAgreement> findByEmployeeId(Long employeeId);
}
