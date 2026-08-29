package com.ohmdevs.hrsaas.dataio.api;

import java.util.List;
import java.util.Map;

/**
 * Cómo un módulo de negocio participa en el motor único de importación
 * (blueprint §07: "escribir un importador por entidad es cómo un backend
 * acumula 4.000 líneas duplicadas — se construye uno, dirigido por
 * descriptores"). Cada implementación es un {@code @Component} de su propio
 * módulo (p. ej. {@code people.application.EmployeeImportRowHandler}) que
 * {@code dataio.application.DataImportService} descubre por inyección
 * múltiple de Spring — {@code dataio} nunca importa el módulo que lo
 * implementa, es al revés (ver {@code dataio/package-info.java}).
 */
public interface ImportRowHandler {

    /** Código de tipo de importación que maneja esta implementación (p. ej. {@code "EMPLOYEES"}). */
    String importType();

    /**
     * Valida una fila cruda (columna → valor, del CSV). Lista vacía si no
     * hay errores — nunca lanza excepción por una fila inválida, blueprint
     * §07: "los errores se acumulan por fila, no abortan el archivo."
     */
    List<RowError> validate(Map<String, String> row);

    /**
     * Persiste una fila ya validada como {@code VALID}. Solo se llama sobre
     * filas que {@link #validate} aceptó — un fallo aquí (p. ej. una
     * restricción de base de datos que {@link #validate} no anticipó) es un
     * error real de programación, no un caso de negocio a capturar en silencio.
     */
    void commit(Map<String, String> row);
}
