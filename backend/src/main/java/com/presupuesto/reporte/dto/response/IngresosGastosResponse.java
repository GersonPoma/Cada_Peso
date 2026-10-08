package com.presupuesto.reporte.dto.response;

import java.time.YearMonth;
import java.util.List;

/** Ingresos contra gastos mes a mes y sus totales del rango (milésimas). */
public record IngresosGastosResponse(
        String desde,
        String hasta,
        long ingresos,
        long gastos,
        long neto,
        List<MesIngresosGastosResponse> meses) {

    public static IngresosGastosResponse desde(
            YearMonth desde, YearMonth hasta, List<MesIngresosGastosResponse> meses) {
        long ingresos = meses.stream().mapToLong(MesIngresosGastosResponse::ingresos).sum();
        long gastos = meses.stream().mapToLong(MesIngresosGastosResponse::gastos).sum();
        return new IngresosGastosResponse(
                desde.toString(), hasta.toString(), ingresos, gastos, ingresos - gastos, meses);
    }
}
