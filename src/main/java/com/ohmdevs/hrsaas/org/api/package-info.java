/**
 * Superficie pública de {@code org} hacia otros módulos:
 * {@link com.ohmdevs.hrsaas.org.api.OrgDirectoryQueryService}, que
 * {@code portal} (Fase 5, adelantada) usa para resolver el nombre de una
 * empresa o de un cargo a partir del {@code Long*Id} que le llega de
 * {@code contracts} — nunca la entidad de {@code org.domain} completa.
 */
@org.springframework.modulith.NamedInterface("api")
package com.ohmdevs.hrsaas.org.api;
