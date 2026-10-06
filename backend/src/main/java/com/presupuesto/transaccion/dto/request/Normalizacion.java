package com.presupuesto.transaccion.dto.request;

/** Normalización de la entrada de los requests de transacciones, aplicada antes de validar. */
final class Normalizacion {

    private Normalizacion() {}

    /** Recortado, y {@code null} si queda vacío. */
    static String recortarONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
