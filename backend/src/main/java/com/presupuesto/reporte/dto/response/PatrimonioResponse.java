package com.presupuesto.reporte.dto.response;

import java.time.YearMonth;
import java.util.List;

/** Patrimonio neto al cierre de cada mes del rango, con todas las cuentas del presupuesto. */
public record PatrimonioResponse(String desde, String hasta, List<MesPatrimonioResponse> meses) {

    public static PatrimonioResponse desde(
            YearMonth desde, YearMonth hasta, List<MesPatrimonioResponse> meses) {
        return new PatrimonioResponse(desde.toString(), hasta.toString(), meses);
    }
}
