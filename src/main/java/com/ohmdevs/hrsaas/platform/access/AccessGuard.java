package com.ohmdevs.hrsaas.platform.access;

import org.springframework.stereotype.Service;

/**
 * Punto único donde se comprueba un permiso.
 *
 * <p>Devolver el {@link PermissionScope} en vez de un booleano es deliberado:
 * la comprobación y el filtrado son la misma decisión. Quien llama recibe el
 * alcance concedido y lo traduce a un predicado de consulta, en lugar de
 * preguntar "¿puedo?" y después decidir por su cuenta sobre qué filas.
 *
 * <pre>{@code
 * PermissionScope scope = accessGuard.require("employee:read");
 * return employeeRepository.findAll(EmployeeScopes.within(scope, actor));
 * }</pre>
 *
 * <p>Ese filtrado vive en la capa de repositorio de cada módulo, no en el
 * controlador. Un endpoint nuevo que olvide el chequeo es una fuga; una
 * especificación por defecto en el repositorio hace que olvidarlo cueste.
 */
@Service
public class AccessGuard {

    /** Exige el permiso con cualquier alcance y devuelve el concedido. */
    public PermissionScope require(String permissionCode) {
        return require(permissionCode, PermissionScope.SELF);
    }

    /** Exige el permiso con al menos {@code minimum} de alcance, y devuelve el concedido. */
    public PermissionScope require(String permissionCode, PermissionScope minimum) {
        CurrentActor actor = ActorContext.require();
        PermissionScope granted = actor.scopeFor(permissionCode).orElse(null);
        if (granted == null || !granted.includes(minimum)) {
            throw new PermissionDeniedException(permissionCode, minimum, granted);
        }
        return granted;
    }

    public boolean has(String permissionCode) {
        CurrentActor actor = ActorContext.current();
        return actor != null && actor.can(permissionCode);
    }
}
