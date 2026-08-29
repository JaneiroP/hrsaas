/**
 * Superficie pública de {@code org} hacia otros módulos.
 *
 * <p>Vacío por ahora: nada fuera de {@code org} necesita todavía leer su
 * estructura directamente (referencias entre módulos, cuando existan, van
 * por {@code Long*Id} — ver {@code contracts}). Existe para que el módulo
 * tenga una superficie declarada desde el principio, en vez de tener que
 * introducirla más tarde cuando la necesidad aparezca.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.org.api;
