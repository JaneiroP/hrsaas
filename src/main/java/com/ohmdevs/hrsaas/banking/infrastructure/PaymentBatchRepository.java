package com.ohmdevs.hrsaas.banking.infrastructure;

import com.ohmdevs.hrsaas.banking.domain.PaymentBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long> {
}
