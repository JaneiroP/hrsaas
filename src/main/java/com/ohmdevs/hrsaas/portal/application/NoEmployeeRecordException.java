package com.ohmdevs.hrsaas.portal.application;

/**
 * El usuario autenticado no tiene un {@code people.Employee} vinculado
 * ({@code Employee.userId} nulo para todos, o simplemente no existe la fila).
 *
 * <p>Ocurre con cuentas legítimas que no son de autoservicio: un contador
 * externo o un administrador de RRHH puede tener acceso a la plataforma sin
 * ser empleado de ningún tenant (blueprint §03: "algunos usuarios no serán
 * empleados"). 409 y no 404: el endpoint existe y el token es válido, lo que
 * falta es una precondición de la cuenta, no el recurso.
 */
public class NoEmployeeRecordException extends RuntimeException {

    public NoEmployeeRecordException(Long userId) {
        super("El usuario " + userId + " no tiene un expediente de empleado vinculado");
    }
}
