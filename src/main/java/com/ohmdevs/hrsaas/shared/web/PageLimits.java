package com.ohmdevs.hrsaas.shared.web;

/**
 * Acota el {@code limit} de una colección paginada por cursor antes de que
 * llegue a una consulta. Sin esto, un cliente (o un bug en el propio
 * frontend) que mande {@code limit=1000000} convierte una lista personal en
 * un intento de traer toda la tabla.
 */
public final class PageLimits {

    public static final int DEFAULT = 10;
    public static final int MAX = 50;

    private PageLimits() {
    }

    /** {@code requested <= 0} se trata como "no especificado", no como error. */
    public static int clamp(int requested) {
        if (requested <= 0) {
            return DEFAULT;
        }
        return Math.min(requested, MAX);
    }
}
