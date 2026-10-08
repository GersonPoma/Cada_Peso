package com.presupuesto.transaccionprogramada.entity;

import lombok.Getter;

/** Cada cuánto se repite una transacción programada: un paso en días o en meses. */
@Getter
public enum FrecuenciaProgramada {
    DIARIA(1, 0),
    SEMANAL(7, 0),
    CADA_2_SEMANAS(14, 0),
    MENSUAL(0, 1),
    CADA_3_MESES(0, 3),
    ANUAL(0, 12);

    private final int dias;
    private final int meses;

    FrecuenciaProgramada(int dias, int meses) {
        this.dias = dias;
        this.meses = meses;
    }

    public boolean esEnMeses() {
        return meses > 0;
    }
}
