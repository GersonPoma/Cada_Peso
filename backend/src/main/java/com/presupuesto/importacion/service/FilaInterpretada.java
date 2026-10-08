package com.presupuesto.importacion.service;

import java.time.LocalDate;

/**
 * Una fila de datos del archivo ya interpretada. Si {@code motivo} no es {@code null} la fila es
 * inválida y fecha, monto, beneficiario y memo pueden ser parciales. {@code fila} cuenta las
 * filas de datos desde 1.
 */
public record FilaInterpretada(
        int fila,
        LocalDate fecha,
        Long monto,
        String beneficiario,
        String memo,
        String motivo) {

    public boolean valida() {
        return motivo == null;
    }
}
