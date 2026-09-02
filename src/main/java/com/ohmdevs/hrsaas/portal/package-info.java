/**
 * Capa de lectura y autoservicio para el empleado (blueprint §03: "sin
 * entidades propias") — perfil, volantes de pago y licencias del propio
 * actor autenticado. Cero lógica de negocio: cada endpoint traduce HTTP a una
 * llamada a la {@code api} de otro módulo y de vuelta.
 *
 * <p><strong>Alcance real de esta implementación</strong>, para no dar por
 * hecho más de lo que hay: solo sirve {@code SELF} — el empleado ve
 * exactamente sus propios datos, nunca los de otro, porque cada consulta se
 * filtra siempre por el {@code employeeId} resuelto del actor autenticado
 * ({@code CurrentActor.userId()} vía {@code people.api.EmployeeDirectoryQueryService}),
 * jamás por uno que llegue en la URL o en el cuerpo de la petición. Los
 * alcances {@code TEAM}/{@code COMPANY} de blueprint §09 (bandeja de
 * aprobación del supervisor, `GET /team/leave-requests`) no están
 * implementados todavía. Tampoco lo están `/me/loans`, `/me/assets`,
 * `/me/documents`, ni el PDF de un volante — el frontend (Sprint 1) todavía
 * no los consume.
 *
 * <p>Dirección de dependencia: {@code portal} es capa de entrada (blueprint
 * §02, fila {@code ENTRADA}) y lee de los módulos de dominio y cálculo a
 * través de su {@code api}, nunca de su interior. Ninguno de ellos conoce a
 * {@code portal} — la flecha va siempre hacia abajo.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Portal del empleado",
        allowedDependencies = {
                "platform :: access", "platform :: tenancy", "shared",
                "org :: api", "people :: api", "contracts :: api", "time :: api", "payroll :: api"
        })
package com.ohmdevs.hrsaas.portal;
