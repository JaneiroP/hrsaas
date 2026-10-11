package com.ohmdevs.hrsaas.time.api;

/**
 * @param recorded   marcajes guardados por primera vez
 * @param duplicates marcajes que ya estaban guardados y no se tocaron
 */
public record PunchIngestResult(int recorded, int duplicates) {
}
