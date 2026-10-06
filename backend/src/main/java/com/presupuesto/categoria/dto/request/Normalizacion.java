package com.presupuesto.categoria.dto.request;

/** Normalización compartida por los requests de grupos y categorías. */
final class Normalizacion {

    private Normalizacion() {
    }

    /** Recorta el nombre; {@code null} se conserva para que lo rechace la validación. */
    static String nombre(String nombre) {
        return nombre == null ? null : nombre.strip();
    }

    /** Recorta la nota; una nota ausente, vacía o en blanco queda en {@code null}. */
    static String nota(String nota) {
        if (nota == null) {
            return null;
        }
        String recortada = nota.strip();
        return recortada.isEmpty() ? null : recortada;
    }
}
