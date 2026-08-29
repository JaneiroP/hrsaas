/**
 * Superficie pública de {@code dataio} — pero al revés del resto de la
 * suite: no es lo que {@code dataio} ofrece para <em>leer</em> de otro
 * módulo, es el contrato que otro módulo <em>implementa</em> para que
 * {@code dataio} pueda importar sus datos
 * ({@link com.ohmdevs.hrsaas.dataio.api.ImportRowHandler}). Ver
 * {@code dataio/package-info.java} para la justificación de la dirección de
 * dependencia invertida.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.dataio.api;
