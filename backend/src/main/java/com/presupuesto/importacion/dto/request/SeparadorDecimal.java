package com.presupuesto.importacion.dto.request;

/** Carácter que separa los decimales en los montos del archivo. */
public enum SeparadorDecimal {
    PUNTO('.'),
    COMA(',');

    private final char caracter;

    SeparadorDecimal(char caracter) {
        this.caracter = caracter;
    }

    public char caracter() {
        return caracter;
    }
}
