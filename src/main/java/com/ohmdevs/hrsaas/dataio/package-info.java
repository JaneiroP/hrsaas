/**
 * Motor único de importación (blueprint §07): "escribir un importador por
 * entidad es cómo un backend acumula 4.000 líneas duplicadas."
 *
 * <p>La dirección de dependencia es la inversa de lo habitual en este
 * proyecto: {@code dataio} no conoce a {@code people}/{@code contracts}/etc.
 * — son esos módulos los que <strong>contribuyen</strong> una implementación
 * de {@link com.ohmdevs.hrsaas.dataio.api.ImportRowHandler} (importando
 * {@code dataio :: api}), y Spring la descubre por inyección múltiple en
 * {@link com.ohmdevs.hrsaas.dataio.application.DataImportService}. Así
 * {@code dataio} queda como infraestructura genuina, sin acoplarse a cada
 * tipo de importación que exista hoy o se agregue después.
 *
 * <p><strong>Alcance real de esta implementación</strong>, documentado para
 * no dar por hecho más de lo que hay: la tubería completa (subida → staging
 * → validación con errores acumulados por fila → vista previa → commit
 * todo-o-nada o solo-válidas) está implementada y probada, con {@code .csv}
 * como único formato de lectura. <strong>No están implementados en esta
 * pasada</strong>: lectura de {@code .xlsx} (Apache POI, con la API de
 * streaming que exige el blueprint — cargar un xlsx grande en memoria
 * tumbaría el contenedor), ancho fijo para bancos/TSS/DGII (comparte
 * descriptor con {@code banking.BankLayout}, que tampoco existe todavía),
 * salida a {@code .pdf}, la plantilla en blanco descargable generada desde
 * el descriptor, y el reporte de errores en {@code .xlsx}. Solo hay un
 * {@code ImportRowHandler} real conectado ({@code people}, para empleados) —
 * el resto de tipos de importación del blueprint (contratos, saldos,
 * préstamos, novedades) quedan como el mismo patrón, sin implementar.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Importación y exportación",
        allowedDependencies = {"platform :: tenancy", "platform :: domain", "shared"})
package com.ohmdevs.hrsaas.dataio;
