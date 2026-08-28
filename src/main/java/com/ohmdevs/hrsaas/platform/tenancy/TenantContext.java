package com.ohmdevs.hrsaas.platform.tenancy;

import java.util.function.Supplier;

/**
 * Tenant activo en el hilo actual.
 *
 * <p>Lo lee el {@code CurrentTenantIdentifierResolver} de Hibernate para filtrar
 * cada consulta, y el {@code TenantAwareDataSource} para fijar
 * {@code app.tenant_id} en la conexión de PostgreSQL (RLS).
 *
 * <p><strong>Es un {@link ThreadLocal}, y ahí está la trampa.</strong> Spring
 * Batch ejecuta los steps en otros hilos, igual que {@code @Async} y cualquier
 * ejecutor: el contexto no viaja solo. Una corrida de nómina que particiona por
 * empleado perdería el tenant en el primer worker y las consultas saldrían sin
 * filtro. Por eso todo ejecutor de la aplicación debe llevar el
 * {@link PlatformContextTaskDecorator}, y el trabajo asíncrono suelto debe
 * envolverse con {@link #callWith(Long, Supplier)}.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Tenant activo, o {@code null} si no hay ninguno (petición sin autenticar, arranque, migración). */
    public static Long current() {
        return CURRENT.get();
    }

    /** Tenant activo, o excepción. Úsalo donde operar sin tenant sería un error de programación. */
    public static Long require() {
        Long tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "No hay tenant en el contexto. Si esto ocurre en un hilo de Spring Batch o "
                            + "@Async, al ejecutor le falta el PlatformContextTaskDecorator.");
        }
        return tenantId;
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Ejecuta una acción bajo un tenant concreto y restaura el anterior al salir.
     *
     * <p>Restaura en vez de limpiar porque las llamadas pueden anidarse: el
     * aprovisionamiento de un tenant corre dentro de una petición administrativa
     * que ya tiene su propio contexto.
     */
    public static <T> T callWith(Long tenantId, Supplier<T> action) {
        Long previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runWith(Long tenantId, Runnable action) {
        callWith(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
