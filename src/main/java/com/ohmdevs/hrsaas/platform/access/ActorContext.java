package com.ohmdevs.hrsaas.platform.access;

import java.util.function.Supplier;

/**
 * Actor activo en el hilo actual.
 *
 * <p>Lo puebla el filtro de autenticación al resolver el token, y lo propaga el
 * {@code PlatformContextTaskDecorator} hacia los hilos de trabajo. Vale la misma
 * advertencia que para el tenant: sin decorador, el actor no cruza a otro hilo.
 */
public final class ActorContext {

    private static final ThreadLocal<CurrentActor> CURRENT = new ThreadLocal<>();

    private ActorContext() {
    }

    public static CurrentActor current() {
        return CURRENT.get();
    }

    public static CurrentActor require() {
        CurrentActor actor = CURRENT.get();
        if (actor == null) {
            throw new IllegalStateException(
                    "No hay actor en el contexto. Una operación que exige permisos se está "
                            + "ejecutando sin autenticación resuelta.");
        }
        return actor;
    }

    public static void set(CurrentActor actor) {
        CURRENT.set(actor);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static <T> T callWith(CurrentActor actor, Supplier<T> action) {
        CurrentActor previous = CURRENT.get();
        CURRENT.set(actor);
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
}
