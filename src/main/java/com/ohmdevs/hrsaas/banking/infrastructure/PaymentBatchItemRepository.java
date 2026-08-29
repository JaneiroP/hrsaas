package com.ohmdevs.hrsaas.banking.infrastructure;

import com.ohmdevs.hrsaas.banking.domain.PaymentBatchItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentBatchItemRepository extends JpaRepository<PaymentBatchItem, Long> {

    List<PaymentBatchItem> findByPaymentBatchId(Long paymentBatchId);
}
