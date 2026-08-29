package com.ohmdevs.hrsaas.people;

import com.ohmdevs.hrsaas.AbstractPostgresTest;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.BankAccount;
import com.ohmdevs.hrsaas.people.domain.BankAccountType;
import com.ohmdevs.hrsaas.people.domain.Dependent;
import com.ohmdevs.hrsaas.people.domain.DependentRelationship;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.domain.EmergencyContact;
import com.ohmdevs.hrsaas.people.infrastructure.BankAccountRepository;
import com.ohmdevs.hrsaas.people.infrastructure.DependentRepository;
import com.ohmdevs.hrsaas.people.infrastructure.EmergencyContactRepository;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aislamiento cruzado del módulo {@code people}, mismo patrón que
 * {@code PlatformIsolationTest}/{@code OrgIsolationTest}.
 *
 * <p>No cubre {@code EmployeeDocument}/{@code uploadDocument}: eso lo prueba
 * {@code S3DocumentStorageGatewayTest} contra LocalStack — aquí el bean de
 * almacenamiento apunta al S3 real (sin credenciales de test), así que un
 * upload de verdad fallaría por razones ajenas al aislamiento que este test
 * quiere probar.
 */
class PeopleIsolationTest extends AbstractPostgresTest {

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private EmergencyContactRepository emergencyContactRepository;
    @Autowired
    private BankAccountRepository bankAccountRepository;

    private TenantProvisioningService.ProvisionedTenant acme;
    private TenantProvisioningService.ProvisionedTenant globex;

    @BeforeEach
    void provisionTwoTenants() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = provisioning.provision("acme-" + run, "Acme SRL", "owner@acme-" + run + ".test");
        globex = provisioning.provision("globex-" + run, "Globex SA", "owner@globex-" + run + ".test");
    }

    @Test
    @DisplayName("un empleado creado en un tenant no existe para el otro, ni por su indice ciego de documento")
    void employeesDoNotLeakBetweenTenants() {
        String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
        Employee created = TenantContext.callWith(acme.tenant().getId(), () ->
                employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                        DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000"));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> employeeRepository.findByDocumentNumberHmac(created.getDocumentNumberHmac())))
                .as("bajo Acme se resuelve el empleado recien creado por su indice ciego")
                .isPresent();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> employeeRepository.findByDocumentNumberHmac(created.getDocumentNumberHmac())))
                .as("el mismo indice ciego consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un dependiente no se ve consultando desde otro tenant")
    void dependentsDoNotLeakBetweenTenants() {
        Employee employee = createAcmeEmployee();
        Dependent created = TenantContext.callWith(acme.tenant().getId(), () ->
                employeeRecords.addDependent(employee, "Hijo de Ana", DependentRelationship.CHILD,
                        LocalDate.of(2015, 6, 1)));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> dependentRepository.findByEmployeeId(employee.getId())))
                .extracting(Dependent::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> dependentRepository.findByEmployeeId(employee.getId())))
                .as("el mismo employee_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("un contacto de emergencia no se ve consultando desde otro tenant")
    void emergencyContactsDoNotLeakBetweenTenants() {
        Employee employee = createAcmeEmployee();
        EmergencyContact created = TenantContext.callWith(acme.tenant().getId(), () ->
                employeeRecords.addEmergencyContact(employee, "Madre de Ana", "MADRE", "809-111-1111"));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> emergencyContactRepository.findByEmployeeId(employee.getId())))
                .extracting(EmergencyContact::getId)
                .contains(created.getId());
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> emergencyContactRepository.findByEmployeeId(employee.getId())))
                .as("el mismo employee_id consultado bajo Globex no devuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("una cuenta bancaria no se ve consultando desde otro tenant, ni por su indice ciego")
    void bankAccountsDoNotLeakBetweenTenants() {
        Employee employee = createAcmeEmployee();
        String accountNumber = "ACC-" + UUID.randomUUID();
        BankAccount created = TenantContext.callWith(acme.tenant().getId(), () ->
                employeeRecords.addBankAccount(employee, "Banco Popular", BankAccountType.SAVINGS, accountNumber));

        assertThat(TenantContext.callWith(acme.tenant().getId(),
                () -> bankAccountRepository.findByAccountNumberHmac(created.getAccountNumberHmac())))
                .as("bajo Acme se resuelve la cuenta recien creada por su indice ciego")
                .isPresent();
        assertThat(TenantContext.callWith(globex.tenant().getId(),
                () -> bankAccountRepository.findByAccountNumberHmac(created.getAccountNumberHmac())))
                .as("el mismo indice ciego consultado bajo Globex no resuelve nada: la fila es de Acme")
                .isEmpty();
    }

    @Test
    @DisplayName("la primera cuenta bancaria de un empleado queda marcada primaria; la segunda, no")
    void firstBankAccountIsPrimary() {
        Employee employee = createAcmeEmployee();
        BankAccount first = TenantContext.callWith(acme.tenant().getId(), () ->
                employeeRecords.addBankAccount(employee, "Banco Popular", BankAccountType.SAVINGS,
                        "ACC-" + UUID.randomUUID()));
        BankAccount second = TenantContext.callWith(acme.tenant().getId(), () ->
                employeeRecords.addBankAccount(employee, "Banreservas", BankAccountType.CHECKING,
                        "ACC-" + UUID.randomUUID()));

        assertThat(first.isPrimary()).isTrue();
        assertThat(second.isPrimary()).isFalse();
    }

    private Employee createAcmeEmployee() {
        String documentNumber = "001-" + UUID.randomUUID().toString().substring(0, 7) + "-8";
        return TenantContext.callWith(acme.tenant().getId(), () ->
                employeeRecords.createEmployee("Ana", "Perez", LocalDate.of(1990, 1, 1),
                        DocumentType.CEDULA, documentNumber, "ana@acme.test", "809-000-0000"));
    }
}
