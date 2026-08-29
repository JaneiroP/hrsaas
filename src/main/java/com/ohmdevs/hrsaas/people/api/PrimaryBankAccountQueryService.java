package com.ohmdevs.hrsaas.people.api;

import com.ohmdevs.hrsaas.people.domain.BankAccount;
import com.ohmdevs.hrsaas.people.infrastructure.BankAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Segundo contenido público de {@code people}: la cuenta bancaria principal de un empleado, para {@code banking}. */
@Service
public class PrimaryBankAccountQueryService {

    private final BankAccountRepository bankAccountRepository;

    public PrimaryBankAccountQueryService(BankAccountRepository bankAccountRepository) {
        this.bankAccountRepository = bankAccountRepository;
    }

    @Transactional
    public Optional<PrimaryBankAccountSummary> primaryAccountFor(Long employeeId) {
        return bankAccountRepository.findByEmployeeIdAndPrimaryTrueAndActiveTrue(employeeId)
                .map(this::toSummary);
    }

    private PrimaryBankAccountSummary toSummary(BankAccount account) {
        return new PrimaryBankAccountSummary(
                account.getId(), account.getBankName(), account.getAccountType().name(), account.getAccountNumber());
    }
}
