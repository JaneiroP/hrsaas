package com.ohmdevs.hrsaas.platform.tenancy;

import com.ohmdevs.hrsaas.platform.access.ActorContext;
import com.ohmdevs.hrsaas.platform.access.CurrentActor;
import org.springframework.core.task.TaskDecorator;

/**
 * Propaga tenant y actor al hilo que ejecuta la tarea.
 *
 * <p>Sin esto, cualquier trabajo fuera del hilo de la petición —{@code @Async},
 * un step particionado de Spring Batch, un {@code CompletableFuture} sobre un
 * pool— arranca sin tenant. Con el resolver devolviendo {@code NO_TENANT} el
 * síntoma es una corrida de nómina que no encuentra empleados; sin él, sería
 * mucho peor.
 *
 * <p>Todo {@code Executor} de la aplicación debe llevarlo. Registrarlo una vez
 * y olvidarlo es justamente el objetivo: nadie debería tener que acordarse.
 */
public class PlatformContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capturado en el hilo que ENVÍA la tarea.
        Long tenantId = TenantContext.current();
        CurrentActor actor = ActorContext.current();

        return () -> {
            // Restaurado en el hilo que la EJECUTA, que puede venir reciclado
            // de un pool con restos de otra tarea.
            Long previousTenant = TenantContext.current();
            CurrentActor previousActor = ActorContext.current();
            apply(tenantId, actor);
            try {
                runnable.run();
            } finally {
                apply(previousTenant, previousActor);
            }
        };
    }

    private static void apply(Long tenantId, CurrentActor actor) {
        if (tenantId == null) {
            TenantContext.clear();
        } else {
            TenantContext.set(tenantId);
        }
        if (actor == null) {
            ActorContext.clear();
        } else {
            ActorContext.set(actor);
        }
    }
}
