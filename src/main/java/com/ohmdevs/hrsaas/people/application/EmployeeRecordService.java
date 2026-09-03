package com.ohmdevs.hrsaas.people.application;

import com.ohmdevs.hrsaas.people.domain.BankAccount;
import com.ohmdevs.hrsaas.people.domain.BankAccountType;
import com.ohmdevs.hrsaas.people.domain.Dependent;
import com.ohmdevs.hrsaas.people.domain.DependentRelationship;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.domain.EmployeeDocument;
import com.ohmdevs.hrsaas.people.domain.EmployeeDocumentCategory;
import com.ohmdevs.hrsaas.people.domain.EmergencyContact;
import com.ohmdevs.hrsaas.people.infrastructure.BankAccountRepository;
import com.ohmdevs.hrsaas.people.infrastructure.DependentRepository;
import com.ohmdevs.hrsaas.people.infrastructure.EmergencyContactRepository;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeDocumentRepository;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import com.ohmdevs.hrsaas.people.infrastructure.storage.DocumentStorageGateway;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.shared.crypto.HmacBlindIndexService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta del expediente del empleado y de todo lo que cuelga de él.
 *
 * <p>El índice ciego ({@code *Hmac}) lo calcula esta capa, nunca
 * {@code EncryptedStringConverter} — mismo patrón que
 * {@code InvitationService} calculando {@code tokenHash}. El converter solo
 * sabe cifrar/descifrar un valor; no sabe que ese valor necesita también un
 * índice buscable.
 */
@Service
public class EmployeeRecordService {

    private final EmployeeRepository employeeRepository;
    private final DependentRepository dependentRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final BankAccountRepository bankAccountRepository;
    private final EmployeeDocumentRepository employeeDocumentRepository;
    private final HmacBlindIndexService hmacBlindIndexService;
    private final DocumentStorageGateway documentStorageGateway;

    public EmployeeRecordService(EmployeeRepository employeeRepository,
                                  DependentRepository dependentRepository,
                                  EmergencyContactRepository emergencyContactRepository,
                                  BankAccountRepository bankAccountRepository,
                                  EmployeeDocumentRepository employeeDocumentRepository,
                                  HmacBlindIndexService hmacBlindIndexService,
                                  DocumentStorageGateway documentStorageGateway) {
        this.employeeRepository = employeeRepository;
        this.dependentRepository = dependentRepository;
        this.emergencyContactRepository = emergencyContactRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.hmacBlindIndexService = hmacBlindIndexService;
        this.documentStorageGateway = documentStorageGateway;
    }

    @Transactional
    public Employee createEmployee(String firstName, String lastName, LocalDate birthDate,
                                    DocumentType documentType, String documentNumber,
                                    String email, String phone) {
        TenantContext.require();
        String documentNumberHmac = hmacBlindIndexService.blindIndex(documentNumber);
        employeeRepository.findByDocumentNumberHmac(documentNumberHmac).ifPresent(existing -> {
            throw new IllegalArgumentException(
                    "Ya existe un empleado con ese numero de documento en este tenant");
        });

        Employee employee = new Employee();
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setBirthDate(birthDate);
        employee.setDocumentType(documentType);
        employee.setDocumentNumber(documentNumber);
        employee.setDocumentNumberHmac(documentNumberHmac);
        employee.setEmail(email);
        employee.setPhone(phone);
        return employeeRepository.save(employee);
    }

    /**
     * Edita datos de contacto/identidad básicos de un expediente ya existente
     * ({@code people.web.EmployeeAdminController}). Deliberadamente no toca
     * {@code documentType}/{@code documentNumber}/{@code birthDate}: cambiar
     * el documento exige recalcular el índice ciego y repetir la comprobación
     * de duplicado que hace {@link #createEmployee}, y cambiar la fecha de
     * nacimiento de un expediente ya en uso es una corrección excepcional,
     * no una edición de rutina — ambas quedan pendientes de un flujo propio
     * de "corrección de expediente" si el negocio lo pide.
     */
    @Transactional
    public Employee updateEmployee(Employee employee, String firstName, String lastName, String email, String phone) {
        TenantContext.require();
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(email);
        employee.setPhone(phone);
        return employeeRepository.save(employee);
    }

    @Transactional
    public Dependent addDependent(Employee employee, String fullName,
                                   DependentRelationship relationship, LocalDate birthDate) {
        TenantContext.require();
        Dependent dependent = new Dependent();
        dependent.setEmployee(employee);
        dependent.setFullName(fullName);
        dependent.setRelationship(relationship);
        dependent.setBirthDate(birthDate);
        return dependentRepository.save(dependent);
    }

    @Transactional
    public EmergencyContact addEmergencyContact(Employee employee, String fullName,
                                                 String relationship, String phone) {
        TenantContext.require();
        EmergencyContact contact = new EmergencyContact();
        contact.setEmployee(employee);
        contact.setFullName(fullName);
        contact.setRelationship(relationship);
        contact.setPhone(phone);
        return emergencyContactRepository.save(contact);
    }

    /**
     * {@code primary=true} solo si es la primera cuenta del empleado — el
     * índice único parcial de la migración exige que a lo sumo una cuenta por
     * empleado sea primaria, y esta capa evita el conflicto en vez de dejar
     * que la restricción lo descubra.
     */
    @Transactional
    public BankAccount addBankAccount(Employee employee, String bankName,
                                       BankAccountType accountType, String accountNumber) {
        TenantContext.require();
        String accountNumberHmac = hmacBlindIndexService.blindIndex(accountNumber);
        bankAccountRepository.findByAccountNumberHmac(accountNumberHmac).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe una cuenta bancaria con ese numero en este tenant");
        });

        boolean isFirstAccount = bankAccountRepository.findByEmployeeId(employee.getId()).isEmpty();

        BankAccount account = new BankAccount();
        account.setEmployee(employee);
        account.setBankName(bankName);
        account.setAccountType(accountType);
        account.setAccountNumber(accountNumber);
        account.setAccountNumberHmac(accountNumberHmac);
        account.setPrimary(isFirstAccount);
        return bankAccountRepository.save(account);
    }

    /** Sube el archivo a {@link DocumentStorageGateway} y registra su metadata. */
    @Transactional
    public EmployeeDocument uploadDocument(Employee employee, EmployeeDocumentCategory category,
                                            String fileName, String contentType, byte[] content,
                                            Long uploadedByUserId) {
        Long tenantId = TenantContext.require();
        String storageKey = "%d/employees/%d/%s-%s".formatted(
                tenantId, employee.getId(), UUID.randomUUID(), fileName);
        documentStorageGateway.upload(storageKey, content, contentType);

        EmployeeDocument document = new EmployeeDocument();
        document.setEmployee(employee);
        document.setCategory(category);
        document.setFileName(fileName);
        document.setStorageKey(storageKey);
        document.setContentType(contentType);
        document.setSizeBytes(content.length);
        document.setUploadedByUserId(uploadedByUserId);
        return employeeDocumentRepository.save(document);
    }
}
