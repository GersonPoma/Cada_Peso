package com.presupuesto.transaccionprogramada.dto.request;

/** Normalización de la entrada de los requests de transacciones programadas. */
final class Normalizacion {

    private Normalizacion() {}

    /** Recortado, y {@code null} si queda vacío. */
    static String recortarONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
