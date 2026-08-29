/**
 * Segunda capa de aislamiento: {@link com.ohmdevs.hrsaas.platform.tenancy.TenantContext}
 * es lo único que un módulo de negocio debería tocar directamente.
 *
 * <p>El resto del paquete ({@code TenantAwareDataSource}, {@code TenantIdentifierResolver},
 * {@code TenantSecurityGuard}, {@code PlatformContextTaskDecorator},
 * {@code TenancyConfiguration}) es plumbing de infraestructura que se conecta
 * solo, sin que ningún módulo lo invoque — se trata como interno aunque
 * {@code @NamedInterface} opera a nivel de paquete completo y técnicamente
 * también lo expone. Un módulo de negocio que necesite más que
 * {@code TenantContext.current()}/{@code require()} probablemente está
 * resolviendo aislamiento en el lugar equivocado.
 */
@org.springframework.modulith.NamedInterface("tenancy")
package com.ohmdevs.hrsaas.platform.tenancy;
