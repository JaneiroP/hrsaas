package com.ohmdevs.hrsaas.dataio.application;

import com.ohmdevs.hrsaas.dataio.api.ImportRowHandler;
import com.ohmdevs.hrsaas.dataio.api.RowError;
import com.ohmdevs.hrsaas.dataio.domain.ImportBatch;
import com.ohmdevs.hrsaas.dataio.domain.ImportBatchStatus;
import com.ohmdevs.hrsaas.dataio.domain.ImportRow;
import com.ohmdevs.hrsaas.dataio.domain.ImportRowStatus;
import com.ohmdevs.hrsaas.dataio.infrastructure.ImportBatchRepository;
import com.ohmdevs.hrsaas.dataio.infrastructure.ImportRowRepository;
import com.ohmdevs.hrsaas.shared.statemachine.IllegalStateTransitionException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El motor único de importación (blueprint §07): subida → staging →
 * validación → vista previa → commit. Formato de lectura único en esta
 * pasada: {@code .csv} (ver {@code dataio/package-info.java} para lo que
 * falta).
 */
@Service
public class DataImportService {

    private final Map<String, ImportRowHandler> handlersByType;
    private final ImportBatchRepository importBatchRepository;
    private final ImportRowRepository importRowRepository;
    private final ObjectMapper objectMapper;

    public DataImportService(List<ImportRowHandler> handlers,
                              ImportBatchRepository importBatchRepository,
                              ImportRowRepository importRowRepository,
                              ObjectMapper objectMapper) {
        this.handlersByType = handlers.stream()
                .collect(Collectors.toMap(ImportRowHandler::importType, Function.identity()));
        this.importBatchRepository = importBatchRepository;
        this.importRowRepository = importRowRepository;
        this.objectMapper = objectMapper;
    }

    /** Paso 1-2: sube y hace staging de un CSV. Idempotente por hash SHA-256 del archivo. */
    @Transactional
    public ImportBatch stage(String importType, byte[] csvBytes, String fileName, Long uploadedByUserId) {
        handlerFor(importType); // falla rápido si nadie declara este tipo de importación
        String fileHash = sha256Hex(csvBytes);
        importBatchRepository.findByFileHash(fileHash).ifPresent(existing -> {
            throw new IllegalStateException(
                    "Este archivo ya fue subido: lote " + existing.getId() + " (" + existing.getFileName() + ")");
        });

        List<CSVRecord> records = parseCsv(csvBytes);

        ImportBatch batch = new ImportBatch();
        batch.setImportType(importType);
        batch.setFileName(fileName);
        batch.setFileHash(fileHash);
        batch.setStatus(ImportBatchStatus.STAGED);
        batch.setTotalRows(records.size());
        batch.setUploadedByUserId(uploadedByUserId);
        batch = importBatchRepository.save(batch);

        for (CSVRecord record : records) {
            ImportRow row = new ImportRow();
            row.setImportBatch(batch);
            row.setLineNumber((int) record.getRecordNumber());
            row.setRawData(writeJson(record.toMap()));
            row.setStatus(ImportRowStatus.PENDING);
            importRowRepository.save(row);
        }
        return batch;
    }

    /** Paso 3: {@code STAGED → VALIDATED}. Cada fila se valida por separado — un error no aborta el archivo. */
    @Transactional
    public ImportBatch validate(Long batchId) {
        ImportBatch batch = getBatch(batchId);
        transition(batch, ImportBatchStatus.VALIDATED);
        ImportRowHandler handler = handlerFor(batch.getImportType());

        int validCount = 0;
        int errorCount = 0;
        for (ImportRow row : importRowRepository.findByImportBatchIdOrderByLineNumberAsc(batchId)) {
            Map<String, String> data = readJsonMap(row.getRawData());
            List<RowError> errors = handler.validate(data);
            if (errors.isEmpty()) {
                row.setStatus(ImportRowStatus.VALID);
                validCount++;
            } else {
                row.setStatus(ImportRowStatus.INVALID);
                row.setErrors(writeJson(errors));
                errorCount++;
            }
            importRowRepository.save(row);
        }

        batch.setValidRows(validCount);
        batch.setErrorRows(errorCount);
        batch.setValidatedAt(java.time.Instant.now());
        return importBatchRepository.save(batch);
    }

    /** Paso 4: resumen de un lote ya validado — cuántas filas van bien, cuántas no, y por qué. */
    @Transactional(readOnly = true)
    public ImportPreview preview(Long batchId) {
        ImportBatch batch = getBatch(batchId);
        List<ImportPreview.RowErrorSummary> rowErrors = importRowRepository
                .findByImportBatchIdOrderByLineNumberAsc(batchId).stream()
                .filter(row -> row.getStatus() == ImportRowStatus.INVALID)
                .map(row -> new ImportPreview.RowErrorSummary(row.getLineNumber(), readJsonErrors(row.getErrors())))
                .toList();
        return new ImportPreview(batch.getTotalRows(), batch.getValidRows(), batch.getErrorRows(), rowErrors);
    }

    /** Paso 5: {@code VALIDATED → COMMITTED}. Persiste las filas {@code VALID} vía el {@link ImportRowHandler} del tipo. */
    @Transactional
    public ImportBatch commit(Long batchId, CommitMode mode) {
        ImportBatch batch = getBatch(batchId);
        if (mode == CommitMode.ALL_OR_NOTHING && batch.getErrorRows() > 0) {
            throw new IllegalStateException(
                    "No se puede comitear todo-o-nada: %d fila(s) con error en el lote %d"
                            .formatted(batch.getErrorRows(), batchId));
        }
        transition(batch, ImportBatchStatus.COMMITTED);
        ImportRowHandler handler = handlerFor(batch.getImportType());

        for (ImportRow row : importRowRepository.findByImportBatchIdAndStatusOrderByLineNumberAsc(batchId, ImportRowStatus.VALID)) {
            handler.commit(readJsonMap(row.getRawData()));
        }

        batch.setCommittedAt(java.time.Instant.now());
        return importBatchRepository.save(batch);
    }

    private List<CSVRecord> parseCsv(byte[] csvBytes) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el CSV", e);
        }
    }

    private ImportRowHandler handlerFor(String importType) {
        ImportRowHandler handler = handlersByType.get(importType);
        if (handler == null) {
            throw new IllegalArgumentException("Sin ImportRowHandler registrado para el tipo '" + importType + "'");
        }
        return handler;
    }

    private ImportBatch getBatch(Long batchId) {
        return importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("ImportBatch no encontrado: " + batchId));
    }

    private void transition(ImportBatch batch, ImportBatchStatus target) {
        if (!batch.getStatus().canTransitionTo(target)) {
            throw new IllegalStateTransitionException("ImportBatch", batch.getId(), batch.getStatus(), target);
        }
        batch.setStatus(target);
    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, String> readJsonMap(String json) {
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
        });
    }

    private List<RowError> readJsonErrors(String json) {
        return objectMapper.readValue(json, new TypeReference<List<RowError>>() {
        });
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
