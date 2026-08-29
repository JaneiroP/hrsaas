/**
 * Expediente del empleado: datos personales, documentos, dependientes,
 * contactos de emergencia y cuentas bancarias.
 *
 * <p>Independiente de {@code org}: la persona no pertenece a una razón
 * social, un contrato sí (eso lo resuelve {@code contracts}). Tampoco depende
 * de {@code platform.domain.User} con asociación JPA — el enlace es
 * {@link com.ohmdevs.hrsaas.people.domain.Employee#getUserId()}, un
 * {@code Long} suelto, nunca al revés.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Personas",
        allowedDependencies = {"platform :: access", "platform :: tenancy", "platform :: domain", "shared", "dataio :: api"})
package com.ohmdevs.hrsaas.people;
