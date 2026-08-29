package com.ohmdevs.hrsaas.people.application;

import com.ohmdevs.hrsaas.dataio.api.ImportRowHandler;
import com.ohmdevs.hrsaas.dataio.api.RowError;
import com.ohmdevs.hrsaas.people.domain.DocumentType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plantilla de importación de empleados (blueprint §07/§12 Fase 4). Columnas
 * del CSV: {@code firstName, lastName, birthDate, documentType,
 * documentNumber, email, phone} — {@code birthDate} en ISO-8601
 * ({@code YYYY-MM-DD}), {@code documentType} en {@code CEDULA}/{@code PASSPORT}.
 *
 * <p>Único {@link ImportRowHandler} conectado en esta pasada — el resto de
 * tipos de importación del blueprint (contratos, saldos, préstamos,
 * novedades) siguen el mismo patrón, sin implementar todavía.
 */
@Component
public class EmployeeImportRowHandler implements ImportRowHandler {

    private static final List<String> REQUIRED_COLUMNS =
            List.of("firstName", "lastName", "birthDate", "documentType", "documentNumber");

    private final EmployeeRecordService employeeRecordService;

    public EmployeeImportRowHandler(EmployeeRecordService employeeRecordService) {
        this.employeeRecordService = employeeRecordService;
    }

    @Override
    public String importType() {
        return "EMPLOYEES";
    }

    @Override
    public List<RowError> validate(Map<String, String> row) {
        List<RowError> errors = new ArrayList<>();
        for (String column : REQUIRED_COLUMNS) {
            if (isBlank(row.get(column))) {
                errors.add(new RowError(column, "Columna requerida vacía"));
            }
        }
        if (!isBlank(row.get("birthDate"))) {
            try {
                LocalDate.parse(row.get("birthDate"));
            } catch (DateTimeParseException e) {
                errors.add(new RowError("birthDate", "Fecha inválida, se espera YYYY-MM-DD"));
            }
        }
        if (!isBlank(row.get("documentType"))) {
            try {
                DocumentType.valueOf(row.get("documentType"));
            } catch (IllegalArgumentException e) {
                errors.add(new RowError("documentType", "Debe ser CEDULA o PASSPORT"));
            }
        }
        return errors;
    }

    @Override
    public void commit(Map<String, String> row) {
        employeeRecordService.createEmployee(
                row.get("firstName"),
                row.get("lastName"),
                LocalDate.parse(row.get("birthDate")),
                DocumentType.valueOf(row.get("documentType")),
                row.get("documentNumber"),
                row.get("email"),
                row.get("phone"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
