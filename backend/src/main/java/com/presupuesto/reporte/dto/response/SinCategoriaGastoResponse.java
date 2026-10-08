package com.presupuesto.reporte.dto.response;

import java.util.function.LongUnaryOperator;

/** El gasto sin categoría (milésimas): siempre presente, aunque valga cero. */
public record SinCategoriaGastoResponse(long total, long porcentaje) {

    public static SinCategoriaGastoResponse desde(long total, LongUnaryOperator porcentajeDe) {
        return new SinCategoriaGastoResponse(total, porcentajeDe.applyAsLong(total));
    }
}
