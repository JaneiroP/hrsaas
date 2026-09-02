package com.ohmdevs.hrsaas.portal;

import com.ohmdevs.hrsaas.contracts.application.ContractLifecycleService;
import com.ohmdevs.hrsaas.contracts.domain.ContractType;
import com.ohmdevs.hrsaas.org.application.OrgStructureService;
import com.ohmdevs.hrsaas.org.domain.Company;
import com.ohmdevs.hrsaas.org.domain.Department;
import com.ohmdevs.hrsaas.org.domain.Position;
import com.ohmdevs.hrsaas.payroll.domain.PayrollConceptType;
import com.ohmdevs.hrsaas.payroll.domain.PayrollFrequency;
import com.ohmdevs.hrsaas.payroll.domain.PayrollPeriod;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRun;
import com.ohmdevs.hrsaas.payroll.domain.PayrollRunStatus;
import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import com.ohmdevs.hrsaas.payroll.domain.PayslipLine;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollPeriodRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayrollRunRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipLineRepository;
import com.ohmdevs.hrsaas.payroll.infrastructure.PayslipRepository;
import com.ohmdevs.hrsaas.people.application.EmployeeRecordService;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import com.ohmdevs.hrsaas.people.domain.Employee;
import com.ohmdevs.hrsaas.people.infrastructure.EmployeeRepository;
import com.ohmdevs.hrsaas.platform.domain.User;
import com.ohmdevs.hrsaas.platform.repository.RoleRepository;
import com.ohmdevs.hrsaas.platform.service.InvitationService;
import com.ohmdevs.hrsaas.platform.service.SystemRole;
import com.ohmdevs.hrsaas.platform.service.TenantProvisioningService;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import com.ohmdevs.hrsaas.time.application.LeaveManagementService;
import com.ohmdevs.hrsaas.time.domain.LeaveType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aprovisiona, en un solo método, un tenant con un empleado listo para
 * iniciar sesión en el portal: empresa, cargo, expediente, contrato,
 * usuario con rol {@code EMPLOYEE}, tipos de licencia y un volante pagado.
 *
 * <p>Vive en {@code src/test}, no en producción: {@code ApplicationModules.of(...)}
 * (el test que verifica fronteras de módulo) solo analiza {@code target/classes},
 * así que este componente puede importar libremente el interior de
 * {@code people}/{@code payroll} sin violar ninguna regla de Modulith — los
 * tests no son parte del monolito modular que esa regla protege.
 */
@Component
public class PortalTestFixture {

    public static final String RAW_PASSWORD = "Cauce#2026-test";

    @Autowired
    private TenantProvisioningService provisioning;
    @Autowired
    private OrgStructureService orgStructure;
    @Autowired
    private EmployeeRecordService employeeRecords;
    @Autowired
    private ContractLifecycleService contractLifecycle;
    @Autowired
    private LeaveManagementService leaveManagement;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;
    @Autowired
    private PayrollRunRepository payrollRunRepository;
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private PayslipLineRepository payslipLineRepository;

    public record Provisioned(
            Long tenantId, Long companyId, Long positionId, Long employeeId, Long userId,
            String email, String password, Long payslipId, Long vacationLeaveTypeId) {
    }

    /** Un segundo empleado en el mismo tenant y empresa, con su propio volante y una solicitud pendiente — la contraparte del "compañero" del blueprint §09. */
    public record Coworker(Long employeeId, String email, String password, Long payslipId, Long leaveRequestId) {
    }

    public Provisioned provisionEmployee(String slugPrefix) {
        String run = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        TenantProvisioningService.ProvisionedTenant tenant =
                provisioning.provision(run, "Constructora Faro SRL", "owner@" + run + ".test");
        Long tenantId = tenant.tenant().getId();

        return TenantContext.callWith(tenantId, () -> {
            Company company = orgStructure.createCompany("Constructora Faro, SRL", "Faro", "131452109");
            Department department = orgStructure.createDepartment(company, "Adquisiciones");
            Position position = orgStructure.createPosition(company, department, "Analista de Compras", null);

            String documentNumber = "402-" + String.format("%07d", (int) (Math.random() * 9_999_999)) + "-1";
            Employee employee = employeeRecords.createEmployee(
                    "Valeria", "Nunez", LocalDate.of(1994, 3, 12),
                    DocumentType.CEDULA, documentNumber, "valeria." + run + "@faro.test", "809-555-0000");

            Long contractId = contractLifecycle.hire(employee.getId(), company.getId(), position.getId(), null, null,
                    ContractType.INDEFINITE, LocalDate.of(2023, 2, 3), null, null,
                    new BigDecimal("52000.00"), "DOP").getId();

            LeaveType vacation = leaveManagement.createLeaveType(
                    "VACACIONES", "Vacaciones", true, true, new BigDecimal("14.00"), 5);
            leaveManagement.createLeaveType(
                    "LICENCIA_MEDICA", "Licencia médica", true, true, null, null);
            leaveManagement.createLeaveType(
                    "PERMISO_PERSONAL", "Permiso personal", false, true, new BigDecimal("3.00"), 0);
            leaveManagement.accrue(employee.getId(), vacation.getId(), new BigDecimal("14.00"));

            Long roleId = roleRepository.findByName(SystemRole.EMPLOYEE.name()).orElseThrow().getId();
            InvitationService.IssuedInvitation issued = invitations.invite(
                    "login." + run + "@faro.test", roleId, null);
            User user = invitations.accept(issued.rawToken(), "Valeria Nunez", RAW_PASSWORD);

            employee.setUserId(user.getId());
            employeeRepository.save(employee);

            Long payslipId = createPaidPayslip(company.getId(), employee.getId(), contractId);

            return new Provisioned(tenantId, company.getId(), position.getId(), employee.getId(), user.getId(),
                    user.getEmail(), RAW_PASSWORD, payslipId, vacation.getId());
        });
    }

    /**
     * Añade un segundo empleado a un tenant y empresa ya provisionados por
     * {@link #provisionEmployee}. Es el escenario más peligroso del blueprint
     * §09: los dos empleados están en el mismo tenant (RLS no los separa), así
     * que lo único que impide que uno lea el volante o cancele la licencia del
     * otro es que cada consulta de {@code portal} filtre por el
     * {@code employeeId} del actor, nunca por uno que llegue en la URL.
     */
    public Coworker provisionCoworker(Long tenantId, Long companyId, Long positionId, Long vacationLeaveTypeId,
                                       String slugPrefix) {
        String run = slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return TenantContext.callWith(tenantId, () -> {
            String documentNumber = "402-" + String.format("%07d", (int) (Math.random() * 9_999_999)) + "-2";
            Employee employee = employeeRecords.createEmployee(
                    "Manuel", "Objio", LocalDate.of(1990, 5, 20),
                    DocumentType.CEDULA, documentNumber, "manuel." + run + "@faro.test", "809-555-0001");

            Long contractId = contractLifecycle.hire(employee.getId(), companyId, positionId, null, null,
                    ContractType.INDEFINITE, LocalDate.of(2022, 1, 10), null, null,
                    new BigDecimal("60000.00"), "DOP").getId();

            Long roleId = roleRepository.findByName(SystemRole.EMPLOYEE.name()).orElseThrow().getId();
            InvitationService.IssuedInvitation issued = invitations.invite("login." + run + "@faro.test", roleId, null);
            User user = invitations.accept(issued.rawToken(), "Manuel Objio", RAW_PASSWORD);
            employee.setUserId(user.getId());
            employeeRepository.save(employee);

            Long payslipId = createPaidPayslip(companyId, employee.getId(), contractId);

            leaveManagement.accrue(employee.getId(), vacationLeaveTypeId, new BigDecimal("14.00"));
            Long leaveRequestId = leaveManagement.submit(employee.getId(), vacationLeaveTypeId,
                    LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), new BigDecimal("3.00"),
                    "Vacaciones cortas").getId();

            return new Coworker(employee.getId(), user.getEmail(), RAW_PASSWORD, payslipId, leaveRequestId);
        });
    }

    private Long createPaidPayslip(Long companyId, Long employeeId, Long contractId) {
        PayrollPeriod period = new PayrollPeriod();
        period.setCompanyId(companyId);
        period.setName("Agosto 2026");
        period.setFrequency(PayrollFrequency.MONTHLY);
        period.setStartDate(LocalDate.of(2026, 8, 1));
        period.setEndDate(LocalDate.of(2026, 8, 31));
        period.setPayDate(LocalDate.of(2026, 8, 29));
        period = payrollPeriodRepository.save(period);

        PayrollRun run = new PayrollRun();
        run.setPayrollPeriod(period);
        run.setCompanyId(companyId);
        run.setStatus(PayrollRunStatus.PAGADA);
        run = payrollRunRepository.save(run);

        Payslip payslip = new Payslip();
        payslip.setPayrollRun(run);
        payslip.setEmployeeId(employeeId);
        payslip.setContractId(contractId);
        payslip.setGrossPay(new BigDecimal("52000.00"));
        payslip.setNetPay(new BigDecimal("48926.80"));
        payslip.setCurrency("DOP");
        payslip = payslipRepository.save(payslip);

        savePayslipLine(payslip, "Salario base", PayrollConceptType.EARNING, new BigDecimal("52000.00"), 1);
        savePayslipLine(payslip, "SFS (3.04%)", PayrollConceptType.DEDUCTION, new BigDecimal("-1580.80"), 2);
        savePayslipLine(payslip, "AFP (2.87%)", PayrollConceptType.DEDUCTION, new BigDecimal("-1492.40"), 3);
        return payslip.getId();
    }

    private void savePayslipLine(Payslip payslip, String description, PayrollConceptType type,
                                  BigDecimal amount, int order) {
        PayslipLine line = new PayslipLine();
        line.setPayslip(payslip);
        line.setDescription(description);
        line.setType(type);
        line.setAmount(amount);
        line.setLineOrder(order);
        payslipLineRepository.save(line);
    }
}
