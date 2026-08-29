package com.ohmdevs.hrsaas.people.infrastructure.storage;

/**
 * Puerto de almacenamiento de archivos de {@code EmployeeDocument}. La clave
 * ({@code key}) la construye quien llama — layout
 * {@code <tenantId>/employees/<employeeId>/<uuid>-<fileName>} — este puerto
 * no sabe nada de tenants ni de empleados, solo mueve bytes.
 */
public interface DocumentStorageGateway {

    void upload(String key, byte[] content, String contentType);

    byte[] download(String key);

    void delete(String key);
}
