package com.presupuesto.importacion.dto.request;

/** Separador de campos del CSV. */
public enum Separador {
    COMA(','),
    PUNTO_Y_COMA(';'),
    TABULADOR('\t');

    private final char caracter;

    Separador(char caracter) {
        this.caracter = caracter;
    }

    public char caracter() {
        return caracter;
    }
}
