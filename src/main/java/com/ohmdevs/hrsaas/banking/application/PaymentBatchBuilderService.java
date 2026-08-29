package com.ohmdevs.hrsaas.banking.application;

import com.ohmdevs.hrsaas.banking.domain.PaymentBatch;
import com.ohmdevs.hrsaas.banking.domain.PaymentBatchItem;
import com.ohmdevs.hrsaas.banking.domain.PaymentItemStatus;
import com.ohmdevs.hrsaas.banking.infrastructure.PaymentBatchItemRepository;
import com.ohmdevs.hrsaas.banking.infrastructure.PaymentBatchRepository;
import com.ohmdevs.hrsaas.people.api.PrimaryBankAccountQueryService;
import com.ohmdevs.hrsaas.payroll.api.PayoutLine;
import com.ohmdevs.hrsaas.payroll.api.PayrollRunPayoutQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Arma un {@link PaymentBatch} desde una {@code PayrollRun} aprobada
 * (blueprint §06). Dos de las validaciones bloqueantes del blueprint se
 * resuelven aquí como exclusión reportada, no como fallo de todo el lote —
 * decisión documentada porque el blueprint no lo desambigua del todo: un
 * empleado sin cuenta bancaria o con neto no positivo no debe bloquear el
 * pago de los demás. Cada uno nace como un {@link PaymentBatchItem} en
 * {@code RECHAZADO} con su código, visible y consultable, en vez de
 * desaparecer sin dejar rastro.
 */
@Service
public class PaymentBatchBuilderService {

    private static final String REJECTION_NO_BANK_ACCOUNT = "SIN_CUENTA_BANCARIA";
    private static final String REJECTION_NON_POSITIVE_NET = "NETO_NO_POSITIVO";

    private final PayrollRunPayoutQueryService payrollRunPayoutQueryService;
    private final PrimaryBankAccountQueryService primaryBankAccountQueryService;
    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentBatchItemRepository paymentBatchItemRepository;

    public PaymentBatchBuilderService(PayrollRunPayoutQueryService payrollRunPayoutQueryService,
                                       PrimaryBankAccountQueryService primaryBankAccountQueryService,
                                       PaymentBatchRepository paymentBatchRepository,
                                       PaymentBatchItemRepository paymentBatchItemRepository) {
        this.payrollRunPayoutQueryService = payrollRunPayoutQueryService;
        this.primaryBankAccountQueryService = primaryBankAccountQueryService;
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentBatchItemRepository = paymentBatchItemRepository;
    }

    @Transactional
    public PaymentBatch build(Long payrollRunId, Long companyId, String bankName,
                               String originAccountNumber, Long builtByUserId) {
        var payouts = payrollRunPayoutQueryService.payoutsForRun(payrollRunId);
        if (payouts.isEmpty()) {
            throw new IllegalStateException("PayrollRun " + payrollRunId + " no tiene volantes que pagar");
        }

        PaymentBatch batch = new PaymentBatch();
        batch.setPayrollRunId(payrollRunId);
        batch.setCompanyId(companyId);
        batch.setBankName(bankName);
        batch.setOriginAccountNumber(originAccountNumber);
        batch.setCurrency(payouts.get(0).currency());
        batch.setBuiltByUserId(builtByUserId);
        batch.setTotalAmount(BigDecimal.ZERO.setScale(2));
        batch = paymentBatchRepository.save(batch);

        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (PayoutLine payout : payouts) {
            PaymentBatchItem item = new PaymentBatchItem();
            item.setPaymentBatch(batch);
            item.setEmployeeId(payout.employeeId());
            item.setContractId(payout.contractId());
            item.setAmount(payout.netPay());

            if (payout.netPay().signum() <= 0) {
                item.setStatus(PaymentItemStatus.RECHAZADO);
                item.setRejectionCode(REJECTION_NON_POSITIVE_NET);
                item.setRejectionReason("El neto del volante no es positivo: " + payout.netPay());
            } else {
                var bankAccount = primaryBankAccountQueryService.primaryAccountFor(payout.employeeId());
                if (bankAccount.isPresent()) {
                    item.setBankAccountId(bankAccount.get().bankAccountId());
                    item.setStatus(PaymentItemStatus.PENDIENTE);
                    total = total.add(payout.netPay());
                } else {
                    item.setStatus(PaymentItemStatus.RECHAZADO);
                    item.setRejectionCode(REJECTION_NO_BANK_ACCOUNT);
                    item.setRejectionReason("El empleado " + payout.employeeId() + " no tiene cuenta bancaria principal activa");
                }
            }
            paymentBatchItemRepository.save(item);
        }

        batch.setTotalAmount(total);
        return paymentBatchRepository.save(batch);
    }
}
