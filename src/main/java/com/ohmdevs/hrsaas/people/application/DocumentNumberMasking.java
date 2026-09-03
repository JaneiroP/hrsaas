package com.ohmdevs.hrsaas.people.application;

import com.ohmdevs.hrsaas.people.domain.DocumentType;

/**
 * Enmascarado de {@link com.ohmdevs.hrsaas.people.domain.Employee#getDocumentNumber()}
 * para cualquier respuesta de API (AD-08: "enmascarado por defecto en
 * respuestas de API"). Extraído de {@code people.api.EmployeeDirectoryQueryService}
 * (que lo usaba solo para el perfil de autoservicio del portal) para que
 * {@code people.web.EmployeeAdminController} — Fase 5, API administrativa —
 * lo reutilice sin duplicar la regla de enmascarado.
 */
public final class DocumentNumberMasking {

    private DocumentNumberMasking() {
    }

    /**
     * Para una cédula dominicana bien formada ({@code NNN-NNNNNNN-N}) conserva
     * el código de provincia y el dígito verificador, que es exactamente el
     * enmascarado que ya espera el frontend del portal (p. ej. {@code
     * 402-XXXXXXX-1}). Cualquier otro formato (pasaporte, o una cédula que no
     * calce con el patrón) cae a un enmascarado genérico: primeros y últimos
     * dos caracteres visibles, el resto sustituido por {@code X}.
     */
    public static String mask(DocumentType type, String plain) {
        if (type == DocumentType.CEDULA && plain.matches("\\d{3}-\\d{7}-\\d")) {
            return plain.substring(0, 4) + "X".repeat(7) + plain.substring(11);
        }
        int visible = 2;
        if (plain.length() <= visible * 2) {
            return "X".repeat(plain.length());
        }
        return plain.substring(0, visible) + "X".repeat(plain.length() - visible * 2) + plain.substring(plain.length() - visible);
    }
}
