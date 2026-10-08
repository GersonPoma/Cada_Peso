package com.presupuesto.importacion.dto.request;

/** Carácter que agrupa los miles en la parte entera de los montos del archivo. */
public enum SeparadorMiles {
    NINGUNO(""),
    PUNTO("."),
    COMA(","),
    /** Espacio normal, espacio no separable (U+00A0) y espacio fino no separable (U+202F). */
    ESPACIO("   ");

    private final String caracteres;

    SeparadorMiles(String caracteres) {
        this.caracteres = caracteres;
    }

    public boolean es(char c) {
        return caracteres.indexOf(c) >= 0;
    }
}
