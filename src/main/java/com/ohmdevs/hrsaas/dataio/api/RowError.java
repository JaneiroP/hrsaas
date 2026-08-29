package com.ohmdevs.hrsaas.dataio.api;

/** Un error de validación de una fila de importación: la columna culpable y el porqué (blueprint §07). */
public record RowError(String column, String message) {
}
