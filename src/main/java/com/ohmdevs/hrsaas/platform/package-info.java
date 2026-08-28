/**
 * Núcleo multi-tenant: identidad, pertenencia, permisos con alcance por fila,
 * invitaciones y auditoría.
 *
 * <p>Ningún módulo de negocio se construye antes que éste, y ninguno accede a
 * sus entidades directamente: la superficie pública es {@code platform.access}
 * ({@code CurrentActor}, {@code PermissionScope}, {@code AccessGuard}) y
 * {@code platform.tenancy} ({@code TenantContext}).
 *
 * <p>Regla de dirección: {@code platform} no conoce a ningún módulo de negocio.
 * En particular no sabe qué es un empleado — resolver "usuario &rarr; empleado"
 * es responsabilidad del módulo {@code people}, que sí puede depender de aquí.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Platform")
package com.ohmdevs.hrsaas.platform;
