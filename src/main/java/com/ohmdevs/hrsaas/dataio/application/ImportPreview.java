package com.ohmdevs.hrsaas.dataio.application;

import com.ohmdevs.hrsaas.dataio.api.RowError;

import java.util.List;

/** Resumen de un lote ya validado (blueprint §07, paso 4): cuántas filas van bien, cuántas no, y por qué. */
public record ImportPreview(int totalRows, int validRows, int errorRows, List<RowErrorSummary> rowErrors) {

    public record RowErrorSummary(int lineNumber, List<RowError> errors) {
    }
}
