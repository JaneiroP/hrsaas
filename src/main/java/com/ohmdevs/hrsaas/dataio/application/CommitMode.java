package com.ohmdevs.hrsaas.dataio.application;

/** Cómo {@code DataImportService.commit} trata las filas con error (blueprint §07, paso 5). */
public enum CommitMode {
    /** Se niega a comitear si hay al menos una fila con error — todo o nada. */
    ALL_OR_NOTHING,
    /** Comitea las filas {@code VALID}, ignorando las {@code INVALID} (ya reportadas en la vista previa). */
    VALID_ROWS_ONLY
}
