package com.presupuesto.auth.dto.request;

import java.util.Locale;

/** Normalización de la entrada de los requests de autenticación, aplicada antes de validar. */
final class Normalizacion {

    private Normalizacion() {}

    /** Sin espacios al inicio ni al final y en minúsculas; {@code null} se deja como está. */
    static String email(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Sin espacios al inicio ni al final; {@code null} se deja como está. */
    static String recortar(String texto) {
        return texto == null ? null : texto.strip();
    }

    /** Recortado, y {@code null} si queda vacío. */
    static String recortarONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
