package com.ohmdevs.hrsaas.shared.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Codifica/decodifica el cursor de paginación como el {@code id} (clave
 * subrogada, autoincremental) de la última fila devuelta.
 *
 * <p>Es paginación por keyset de verdad, no offset disfrazado: cada página
 * pide "lo que sigue después de este id" con un {@code WHERE id < :cursor
 * ORDER BY id DESC}, así que el costo no crece con la profundidad de la
 * página y no se degrada si se insertan filas nuevas entre una página y la
 * siguiente. Ordenar por {@code id DESC} en vez de por la fecha de negocio
 * (fecha de pago, fecha de solicitud) es una simplificación deliberada: como
 * las filas de este proyecto se insertan en el mismo orden cronológico en
 * que ocurren los eventos de negocio, el id más alto coincide en la práctica
 * con el más reciente. Si algún día se necesita reordenar sin depender de
 * eso, hace falta un cursor compuesto (fecha, id) — no aplica todavía a
 * ninguna colección personal expuesta por {@code portal}.
 *
 * <p>Base64 no es cifrado: no protege nada, solo evita que el cliente vea un
 * entero de base de datos "en claro" y se sienta invitado a incrementarlo a
 * mano. Quien de verdad protege contra eso es que cada consulta va siempre
 * filtrada por el {@code employeeId} del actor autenticado, nunca por uno
 * que llegue en la petición.
 */
public final class CursorCodec {

    private CursorCodec() {
    }

    public static String encode(Long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** {@code null} o vacío se tratan como "primera página", no como error. */
    public static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException(cursor);
        }
    }

    /** Extiende {@link IllegalArgumentException} para que caiga en el mapeo genérico a 400. */
    public static final class InvalidCursorException extends IllegalArgumentException {
        public InvalidCursorException(String cursor) {
            super("Cursor de paginación inválido: '" + cursor + "'");
        }
    }
}
