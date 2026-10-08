package com.presupuesto.reporte.dto.response;

import java.time.YearMonth;

/** Ingresos, gastos y neto (ingresos menos gastos) de un mes, en milésimas. */
public record MesIngresosGastosResponse(String mes, long ingresos, long gastos, long neto) {

    public static MesIngresosGastosResponse desde(YearMonth mes, long ingresos, long gastos) {
        return new MesIngresosGastosResponse(mes.toString(), ingresos, gastos, ingresos - gastos);
    }
}
