package com.ohmdevs.hrsaas.people.api;

/**
 * La cuenta bancaria principal de un empleado, tal como la necesita
 * {@code banking} para armar un lote de pago. {@code accountType} viaja
 * como {@code String} (no el enum de {@code people.domain}) a propósito:
 * evita que otro módulo dependa de un tipo interno solo para leer un dato,
 * mismo patrón que {@code contracts.api.ActiveContractSummary} con
 * {@code currency}.
 *
 * <p>{@code accountNumber} ya viene descifrado — {@code BankAccount} aplica
 * {@code EncryptedStringConverter} a nivel de mapeo JPA, así que cualquier
 * lectura vía repositorio lo devuelve en claro. Quien reciba este record es
 * responsable de no loguearlo ni exponerlo fuera de su uso legítimo
 * (construir el archivo bancario).
 */
public record PrimaryBankAccountSummary(
        Long bankAccountId,
        String bankName,
        String accountType,
        String accountNumber) {
}
