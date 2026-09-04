package com.ohmdevs.hrsaas.payroll.infrastructure;

import com.ohmdevs.hrsaas.payroll.domain.Payslip;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    List<Payslip> findByPayrollRunId(Long payrollRunId);

    /** Primera página del histórico personal del portal — keyset por id, ver {@code CursorCodec}. */
    List<Payslip> findByEmployeeIdOrderByIdDesc(Long employeeId, Pageable pageable);

    /** Páginas siguientes: "lo que sigue después de este id", nunca un offset. */
    List<Payslip> findByEmployeeIdAndIdLessThanOrderByIdDesc(Long employeeId, Long id, Pageable pageable);

    /**
     * Filtra por {@code employeeId} en la propia consulta, no después de
     * cargar por id: es lo que impide que el portal sirva el volante de otro
     * empleado a quien le cambie el id en la URL (blueprint §09).
     */
    Optional<Payslip> findByIdAndEmployeeId(Long id, Long employeeId);

    /**
     * Mismo motivo que {@link #findByIdAndEmployeeId}, para la API admin
     * ({@code GET .../runs/{runId}/payslips/{payslipId}}): filtra por
     * {@code payrollRunId} en la consulta en vez de cargar por id y comparar
     * después, y de paso evita tocar la asociación perezosa {@code payrollRun}
     * (no hace falta inicializarla solo para comprobar a qué corrida pertenece).
     */
    Optional<Payslip> findByIdAndPayrollRunId(Long id, Long payrollRunId);
}
