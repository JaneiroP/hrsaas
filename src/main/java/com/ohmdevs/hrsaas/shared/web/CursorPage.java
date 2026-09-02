package com.ohmdevs.hrsaas.shared.web;

import java.util.List;

/**
 * Envoltorio de paginación por cursor para cualquier colección expuesta por
 * HTTP (blueprint §08: "offset se degrada en la primera empresa de 5.000
 * empleados").
 *
 * <p>{@code nextCursor} es opaco para quien consume la API: se reenvía tal
 * cual en la siguiente petición, nunca se decodifica del lado del cliente.
 * {@code null} significa que no hay página siguiente.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {
}
