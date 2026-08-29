/**
 * Superficie pública de autorización: {@link com.ohmdevs.hrsaas.platform.access.AccessGuard},
 * {@link com.ohmdevs.hrsaas.platform.access.CurrentActor},
 * {@link com.ohmdevs.hrsaas.platform.access.PermissionScope},
 * {@link com.ohmdevs.hrsaas.platform.access.GrantedPermission} y
 * {@link com.ohmdevs.hrsaas.platform.access.PermissionDeniedException}.
 *
 * <p>Spring Modulith solo expone por defecto el paquete raíz de un módulo;
 * cualquier subpaquete es interno hasta que se declare lo contrario con
 * {@code @NamedInterface}. Sin esta anotación, el primer {@code import} desde
 * {@code people} o {@code contracts} hacia {@code AccessGuard} rompería
 * {@code ApplicationModules.verify()}.
 */
@org.springframework.modulith.NamedInterface("access")
package com.ohmdevs.hrsaas.platform.access;
