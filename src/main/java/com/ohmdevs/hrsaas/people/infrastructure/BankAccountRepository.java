package com.ohmdevs.hrsaas.people.infrastructure;

import com.ohmdevs.hrsaas.people.domain.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    List<BankAccount> findByEmployeeId(Long employeeId);

    Optional<BankAccount> findByEmployeeIdAndPrimaryTrueAndActiveTrue(Long employeeId);

    /** Búsqueda por índice ciego, nunca por el número en claro (ver {@code BankAccount.accountNumberHmac}). */
    Optional<BankAccount> findByAccountNumberHmac(String accountNumberHmac);
}
