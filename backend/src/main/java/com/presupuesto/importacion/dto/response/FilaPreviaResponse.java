package com.presupuesto.importacion.dto.response;

import com.presupuesto.importacion.service.FilaInterpretada;
import java.time.LocalDate;

/**
 * Una fila de datos interpretada. {@code motivo} solo viene en las {@code INVALIDA}; en ellas
 * fecha, monto, beneficiario y memo son {@code null}.
 */
public record FilaPreviaResponse(
        int fila,
        LocalDate fecha,
        Long monto,
        String beneficiario,
        String memo,
        EstadoFila estado,
        String motivo) {

    public static FilaPreviaResponse desde(FilaInterpretada interpretada, EstadoFila estado) {
        return new FilaPreviaResponse(
                interpretada.fila(),
                interpretada.fecha(),
                interpretada.monto(),
                interpretada.beneficiario(),
                interpretada.memo(),
                estado,
                interpretada.motivo());
    }
}
